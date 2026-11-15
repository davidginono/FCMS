package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fcms.mail.initial-delay=3600000",
            "fcms.mail.interval=3600000",
            "fcms.backup.interval=3600000",
            "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerReportSnapshotTests {
    private static final String SCHEMA =
            "ledger_report_" + UUID.randomUUID().toString().replace("-", "");
    private static final String MAKER = "ledger-maker@test.invalid",
            STAFF = "ledger-staff@test.invalid";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired LedgerService ledger;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    void actor(String name) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, "", List.of()));
    }

    LedgerForm form(String amount, String description) {
        var f = new LedgerForm();
        f.setAmount(new BigDecimal(amount));
        f.setDescription(description);
        f.setCategory("Office");
        f.setReference(UUID.randomUUID().toString());
        return f;
    }

    @BeforeAll
    void fixtures() {
        for (var row : Map.of(MAKER, 1, STAFF, 2).entrySet())
            db.update(
                    "insert into users(first_name,last_name,email,password,status_id,role_id)"
                            + " values ('Ledger','Fixture',?,?,1,?)",
                    row.getKey(),
                    passwords.encode("Verification123!"),
                    row.getValue());
        for (var kind : LedgerKind.values()) {
            actor(MAKER);
            var opening = form("100.35", "Ledger facsimile opening");
            if (kind == LedgerKind.BULK) opening.setUnits(100);
            ledger.post(kind, opening);
            var out = form("10.10", "Ledger facsimile correction source");
            if (kind != LedgerKind.INCOME) out.setDirection("OUT");
            if (kind == LedgerKind.BULK) {
                out.setUnits(11);
                out.setUnitDirection("OUT");
            }
            long id = ledger.post(kind, out);
            actor("admin@fcms.local");
            ledger.reverse(kind, id, UUID.randomUUID(), "Ledger facsimile correction");
        }
        actor(MAKER);
        var units = form("0.00", "Ledger facsimile unit-only");
        units.setUnits(3);
        ledger.post(LedgerKind.BULK, units);
    }

    @BeforeEach
    void signIn() {
        actor(MAKER);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void exactTotalsAndRecordedUnitBalancesSurviveFiltersAndSorting() {
        var q = new RegisterQuery();
        q.setPage(4);
        q.setSize(10);
        q.setSort("date");
        q.setDirection("asc");
        var source = ledger.reportSnapshot(LedgerKind.BULK, q);
        assertEquals(0, new BigDecimal("110.45").compareTo(source.amountIn()));
        assertEquals(0, new BigDecimal("10.10").compareTo(source.amountOut()));
        assertEquals(114, source.unitsIn());
        assertEquals(11, source.unitsOut());
        assertEquals(103, source.currentUnits());
        assertEquals(0, source.movement().compareTo(source.currentBalance()));
        assertEquals(
                List.of(100L, 89L, 100L, 103L),
                source.rows().stream().map(r -> r.unitBalance()).toList());
        assertEquals(4, q.getPage());
        assertEquals(10, q.getSize());
        q.setSearch("opening");
        q.setFrom(LocalDate.now());
        q.setTo(LocalDate.now());
        var selected = ledger.reportSnapshot(LedgerKind.BULK, q);
        assertEquals(1, selected.rows().size());
        assertEquals(100, selected.unitsIn());
        assertEquals(103, selected.currentUnits());
        assertNotNull(selected.rows().getFirst().reference());
        q.setSearch("absent");
        assertTrue(ledger.reportSnapshot(LedgerKind.BULK, q).rows().isEmpty());
        assertEquals(4, q.getPage());
        assertEquals(10, q.getSize());
    }

    HttpClient browser(String email) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(cookies)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
        var login =
                web.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                                .timeout(Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        var token =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(login.body());
        assertTrue(token.find());
        var response =
                web.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                                .timeout(Duration.ofSeconds(30))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "email="
                                                        + URLEncoder.encode(
                                                                email, StandardCharsets.UTF_8)
                                                        + "&password=Verification123!&_csrf="
                                                        + token.group(1)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        assertFalse(response.headers().firstValue("location").orElse("").contains("error"));
        return web;
    }

    @Test
    @Order(2)
    void nativeLocalizedHttpExportsArePdfNoStoreAndCannotBeReadByStaff() throws Exception {
        var web = browser(MAKER);
        var output = Path.of(".verification/ledger-facsimile/http");
        Files.createDirectories(output);
        for (var kind : LedgerKind.values())
            for (String lang : List.of("en", "sw")) {
                var response =
                        web.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/finance/"
                                                                + kind.route
                                                                + "/print.pdf?lang="
                                                                + lang
                                                                + "&search=opening&from="
                                                                + LocalDate.now()))
                                        .timeout(Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode(), kind + " " + lang);
                assertTrue(
                        response.headers()
                                .firstValue("content-type")
                                .orElse("")
                                .contains("application/pdf"));
                assertTrue(
                        response.headers()
                                .firstValue("cache-control")
                                .orElse("")
                                .contains("no-store"));
                assertTrue(
                        response.headers()
                                .firstValue("content-disposition")
                                .orElse("")
                                .contains(kind.route + ".pdf"));
                var reader = new PdfReader(response.body());
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertTrue(text.contains("100.35"));
                assertTrue(text.contains("opening"));
                if (lang.equals("sw")) assertTrue(text.contains("Taarifa"));
                assertFalse(text.contains("10.10"));
                reader.close();
                Files.write(output.resolve(kind.route + "-" + lang + ".pdf"), response.body());
            }
        var staff = browser(STAFF);
        for (var kind : LedgerKind.values()) {
            var response =
                    staff.send(
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/finance/"
                                                            + kind.route
                                                            + "/print.pdf"))
                                    .timeout(Duration.ofSeconds(30))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode());
        }
    }

    @Test
    @Order(3)
    void deniedActorsCannotReadRowsOrCountersAndInvalidRangesAreRejected() {
        actor(STAFF);
        assertThrows(
                AccessDeniedException.class,
                () -> ledger.reportSnapshot(LedgerKind.BULK, new RegisterQuery()));
        actor(MAKER);
        var q = new RegisterQuery();
        q.setFrom(LocalDate.now());
        q.setTo(LocalDate.now().minusDays(1));
        assertThrows(
                IllegalArgumentException.class, () -> ledger.reportSnapshot(LedgerKind.CLOUD, q));
        assertEquals(0, q.getPage());
        assertEquals(25, q.getSize());
    }

    @Test
    @Order(4)
    void aConcurrentPostingDoesNotMixRowsAndCountersFromTwoSnapshots() {
        var annotation = LedgerService.class.getMethods();
        var method =
                Arrays.stream(annotation)
                        .filter(m -> m.getName().equals("reportSnapshot"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                        .isolation());
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var before =
                    tx.execute(
                            status -> {
                                var first =
                                        ledger.reportSnapshot(
                                                LedgerKind.CLOUD, new RegisterQuery());
                                try {
                                    executor.submit(
                                                    () -> {
                                                        try {
                                                            actor(MAKER);
                                                            ledger.post(
                                                                    LedgerKind.CLOUD,
                                                                    form(
                                                                            "0.05",
                                                                            "Concurrent source"));
                                                        } finally {
                                                            SecurityContextHolder.clearContext();
                                                        }
                                                    })
                                            .get(30, TimeUnit.SECONDS);
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                                var second =
                                        ledger.reportSnapshot(
                                                LedgerKind.CLOUD, new RegisterQuery());
                                assertEquals(first.rows(), second.rows());
                                assertEquals(first.currentBalance(), second.currentBalance());
                                assertEquals(first.amountIn(), second.amountIn());
                                return first.currentBalance();
                            });
            assertNotNull(before);
            var after = ledger.reportSnapshot(LedgerKind.CLOUD, new RegisterQuery());
            assertEquals(0, before.add(new BigDecimal("0.05")).compareTo(after.currentBalance()));
            assertEquals(0, after.currentBalance().compareTo(after.movement()));
        }
    }

    @Test
    @Order(5)
    void reportLimitIsExactAndCallerPaginationIsRestoredOnFailure() {
        for (int i = 0; i < 1000; i++)
            ledger.post(LedgerKind.CLOUD, form("0.01", "Bound fixture " + i));
        var q = new RegisterQuery();
        q.setSearch("Bound fixture");
        q.setPage(8);
        q.setSize(50);
        assertEquals(1000, ledger.reportSnapshot(LedgerKind.CLOUD, q).rows().size());
        assertEquals(8, q.getPage());
        assertEquals(50, q.getSize());
        ledger.post(LedgerKind.CLOUD, form("0.01", "Bound fixture 1001"));
        assertEquals(
                "error.narrowReport",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ledger.reportSnapshot(LedgerKind.CLOUD, q))
                        .getMessage());
        assertEquals(8, q.getPage());
        assertEquals(50, q.getSize());
    }
}
