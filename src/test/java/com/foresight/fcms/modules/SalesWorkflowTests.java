package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.sales.dto.*;
import com.foresight.fcms.sales.model.SalesKind;
import com.foresight.fcms.sales.service.SalesService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesWorkflowTests {
    static final String SCHEMA = "sales_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("fcms.mail.initial-delay", () -> "3600000");
        p.add("fcms.mail.interval", () -> "3600000");
        p.add("fcms.backup.interval", () -> "3600000");
    }

    @Autowired SalesService sales;
    @Autowired JdbcTemplate db;

    @Value("${local.server.port}")
    int port;

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(email, "unused", List.of()));
    }

    @BeforeEach
    void admin() {
        actor("admin@fcms.local");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    SalesForm form() {
        var f = SalesPdfTests.reference();
        f.setDocumentNumber("VERIFY-" + UUID.randomUUID());
        return f;
    }

    long count(String table) {
        return db.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void createAndEditComputeExactTotalsRetainRevisionsAndNeverPostMoney() {
        long journals = count("gl_journals"), clients = count("saccos");
        var f = form();
        long id = sales.save(SalesKind.PROSPECT, null, f);
        assertEquals(id, sales.save(SalesKind.PROSPECT, null, f));
        var d = sales.find(SalesKind.PROSPECT, id);
        assertEquals(new BigDecimal("780000.00"), d.amount());
        var edit = sales.form(SalesKind.PROSPECT, id);
        edit.setTerms("Revised terms");
        sales.save(SalesKind.PROSPECT, id, edit);
        assertEquals(id, sales.save(SalesKind.PROSPECT, id, edit));
        assertEquals(1, sales.find(SalesKind.PROSPECT, id).data().getVersion());
        assertEquals(
                2,
                db.queryForObject(
                        "select count(*) from sales_document_revisions where document_id=?",
                        Integer.class,
                        id));
        assertEquals(
                "",
                db.queryForObject(
                        "select snapshot->>'terms' from sales_document_revisions where"
                                + " document_id=? and version=0",
                        String.class,
                        id));
        assertEquals(
                "Revised terms",
                db.queryForObject(
                        "select snapshot->>'terms' from sales_document_revisions where"
                                + " document_id=? and version=1",
                        String.class,
                        id));
        var historyQuery = new RegisterQuery();
        historyQuery.setSort("revision");
        historyQuery.setDirection("asc");
        assertEquals(
                0, sales.history(SalesKind.PROSPECT, id, historyQuery).rows().getFirst().version());
        historyQuery.setDirection("desc");
        assertEquals(
                1, sales.history(SalesKind.PROSPECT, id, historyQuery).rows().getFirst().version());
        historyQuery.setSearch("no-numeric-history-match");
        assertTrue(sales.history(SalesKind.PROSPECT, id, historyQuery).rows().isEmpty());
        historyQuery.setSearch("1");
        assertEquals(2, sales.history(SalesKind.PROSPECT, id, historyQuery).rows().size());
        assertEquals(journals, count("gl_journals"));
        assertEquals(clients, count("saccos"));
        assertThrows(IllegalArgumentException.class, () -> sales.find(SalesKind.INVOICE, id));
        edit.setTerms("Substituted retry");
        assertThrows(
                IllegalArgumentException.class, () -> sales.save(SalesKind.PROSPECT, id, edit));
    }

    @Test
    void validationRejectsInvalidDocumentsAndAcceptsFiftyExactDecimalItems() {
        var f = form();
        f.setItems(
                new ArrayList<>(
                        java.util.stream.IntStream.range(0, 50)
                                .mapToObj(i -> new SalesItem("Item " + i, new BigDecimal("0.01")))
                                .toList()));
        long id = sales.save(SalesKind.INVOICE, null, f);
        assertEquals(new BigDecimal("0.50"), sales.find(SalesKind.INVOICE, id).amount());
        var invalids = new ArrayList<SalesForm>();
        var bad = form();
        bad.setClientType("MICROFINANCE");
        invalids.add(bad);
        bad = form();
        bad.setCurrency("XXX");
        invalids.add(bad);
        bad = form();
        bad.setPaymentMethod("UNKNOWN");
        invalids.add(bad);
        bad = form();
        bad.setValidUntil(bad.getDocumentDate().minusDays(1));
        invalids.add(bad);
        bad = form();
        bad.setDocumentNumber("number\ninjection");
        invalids.add(bad);
        bad = form();
        bad.getItems().getFirst().setAmount(new BigDecimal("0.001"));
        invalids.add(bad);
        bad = form();
        bad.getItems().getFirst().setAmount(new BigDecimal("-1"));
        invalids.add(bad);
        bad = form();
        bad.getItems().clear();
        invalids.add(bad);
        bad = form();
        bad.setItems(
                new ArrayList<>(Collections.nCopies(51, new SalesItem("Item", BigDecimal.ONE))));
        invalids.add(bad);
        bad = form();
        bad.setItems(
                new ArrayList<>(
                        Collections.nCopies(50, new SalesItem("x".repeat(4000), BigDecimal.ONE))));
        invalids.add(bad);
        bad = form();
        bad.setItems(
                new ArrayList<>(
                        List.of(
                                new SalesItem("One", new BigDecimal("999999999999.99")),
                                new SalesItem("Two", BigDecimal.ONE))));
        invalids.add(bad);
        long before = count("sales_documents");
        for (var input : invalids)
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sales.save(SalesKind.PROSPECT, null, input));
        assertEquals(before, count("sales_documents"));
    }

    @Test
    void independentClaimsEnforceCompanyScopeForReadWriteAndExport() {
        var f = form();
        long id = sales.save(SalesKind.INVOICE, null, f);
        long role =
                db.queryForObject(
                        "insert into roles(role_name) values (?) returning role_id",
                        Long.class,
                        "sales-test-" + UUID.randomUUID());
        String email = "sales-reader-" + UUID.randomUUID() + "@test.invalid";
        long user =
                db.queryForObject(
                        "insert into users(first_name,last_name,email,password,role_id,status_id)"
                                + " values ('Synthetic','Reader',?,'unused',?,1) returning id",
                        Long.class,
                        email,
                        role);
        actor(email);
        assertThrows(AccessDeniedException.class, () -> sales.find(SalesKind.INVOICE, id));
        db.update("insert into user_claims(user_id,claim) values (?,'sales.invoice.read')", user);
        assertEquals(id, sales.find(SalesKind.INVOICE, id).id());
        assertThrows(
                AccessDeniedException.class, () -> sales.save(SalesKind.INVOICE, null, form()));
        assertThrows(AccessDeniedException.class, () -> sales.export(SalesKind.INVOICE, id));
        assertThrows(
                AccessDeniedException.class,
                () -> sales.list(SalesKind.PROSPECT, new RegisterQuery()));
        db.update("insert into user_claims(user_id,claim) values (?,'sales.invoice.export')", user);
        assertEquals(id, sales.export(SalesKind.INVOICE, id).id());
        db.update("update users set status_id=2 where id=?", user);
        assertThrows(AccessDeniedException.class, () -> sales.find(SalesKind.INVOICE, id));
    }

    @Test
    void duplicateConcurrentCommandsAndConcurrentEditsHaveOneWinner() throws Exception {
        var f = form();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var jobs =
                    List.<Callable<Long>>of(
                            () -> {
                                actor("admin@fcms.local");
                                try {
                                    return sales.save(SalesKind.INVOICE, null, f);
                                } finally {
                                    clear();
                                }
                            },
                            () -> {
                                actor("admin@fcms.local");
                                try {
                                    var copy = form();
                                    copy.setDocumentNumber(f.getDocumentNumber());
                                    copy.setRequestKey(f.getRequestKey());
                                    return sales.save(SalesKind.INVOICE, null, copy);
                                } finally {
                                    clear();
                                }
                            });
            var created = executor.invokeAll(jobs);
            long id = created.getFirst().get();
            assertEquals(id, created.getLast().get());
            var a = sales.form(SalesKind.INVOICE, id);
            var b = sales.form(SalesKind.INVOICE, id);
            a.setTerms("Winner A");
            b.setTerms("Winner B");
            var start = new CountDownLatch(1);
            var edits = new ArrayList<Callable<Boolean>>();
            for (var edit : List.of(a, b))
                edits.add(
                        () -> {
                            actor("admin@fcms.local");
                            try {
                                start.await();
                                sales.save(SalesKind.INVOICE, id, edit);
                                return true;
                            } catch (OptimisticLockingFailureException expected) {
                                return false;
                            } finally {
                                clear();
                            }
                        });
            var first = executor.submit(edits.getFirst());
            var second = executor.submit(edits.getLast());
            start.countDown();
            assertNotEquals(first.get(), second.get());
            assertEquals(
                    2,
                    db.queryForObject(
                            "select count(*) from sales_document_revisions where document_id=?",
                            Integer.class,
                            id));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void databaseEnforcesAmountItemsUniqueNumbersAndRetainedHistory() {
        var f = form();
        long id = sales.save(SalesKind.INVOICE, null, f);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> db.update("update sales_documents set amount=0.01 where id=?", id));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> db.update("update sales_documents set items='[]'::jsonb where id=?", id));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> db.update("delete from sales_documents where id=?", id));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> db.update("delete from sales_document_revisions where document_id=?", id));
        var duplicate = form();
        duplicate.setDocumentNumber(f.getDocumentNumber());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> sales.save(SalesKind.INVOICE, null, duplicate));
        assertDoesNotThrow(() -> sales.save(SalesKind.PROSPECT, null, duplicate));
    }

    @Test
    void registerSearchSortFiltersAndLookaheadApplyBeforePagination() {
        String token = "FILTER-" + UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            var f = form();
            f.setDocumentNumber(token + "-" + String.format("%02d", i));
            f.setClientName("Literal %_" + i);
            sales.save(SalesKind.INVOICE, null, f);
        }
        var q = new RegisterQuery();
        q.setSearch(token);
        q.setSize(10);
        q.setSort("documentNumber");
        q.setDirection("asc");
        var page = sales.list(SalesKind.INVOICE, q);
        assertEquals(10, page.rows().size());
        assertTrue(page.hasNext());
        assertTrue(page.rows().getFirst().documentNumber().endsWith("-00"));
        q.setPage(1);
        page = sales.list(SalesKind.INVOICE, q);
        assertEquals(2, page.rows().size());
        assertFalse(page.hasNext());
        assertTrue(page.rows().getFirst().documentNumber().endsWith("-10"));
        q.setPage(0);
        q.setSearch("%_");
        assertEquals(10, sales.list(SalesKind.INVOICE, q).rows().size());
        q.setSearch(token);
        q.setStatus("AMCOS");
        assertTrue(sales.list(SalesKind.INVOICE, q).rows().isEmpty());
        q.setStatus("");
        q.setSort("amount;drop table users");
        assertDoesNotThrow(() -> sales.list(SalesKind.INVOICE, q));
        q.setFrom(LocalDate.of(2027, 1, 1));
        assertTrue(sales.list(SalesKind.INVOICE, q).rows().isEmpty());
    }

    final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    URI uri(String route) {
        return URI.create("http://127.0.0.1:" + port + route);
    }

    HttpResponse<String> get(String route, String cookie) throws Exception {
        var r = HttpRequest.newBuilder(uri(route)).timeout(Duration.ofSeconds(45));
        if (cookie != null) r.header("Cookie", cookie);
        return http.send(r.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String route, String cookie, Map<String, String> data)
            throws Exception {
        String body =
                data.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return http.send(
                HttpRequest.newBuilder(uri(route))
                        .timeout(Duration.ofSeconds(45))
                        .header("Cookie", cookie)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String value(String html, String name) {
        var pattern =
                Pattern.compile(
                        "<input[^>]*name=\"" + Pattern.quote(name) + "\"[^>]*value=\"([^\"]*)\"");
        var m = pattern.matcher(html);
        assertTrue(m.find(), name);
        return m.group(1);
    }

    @Test
    void actualHttpFormsRetainDraftItemsAndErrorsRequireCsrfAndRenderBothLanguages()
            throws Exception {
        assertEquals(302, get("/sales/invoices", null).statusCode());
        var login = get("/login", null);
        String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        var signed =
                post(
                        "/login",
                        cookie,
                        Map.of(
                                "email",
                                "admin@fcms.local",
                                "password",
                                "ChangeMe123!",
                                "_csrf",
                                value(login.body(), "_csrf")));
        cookie = signed.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        var create = get("/sales/invoices/new", cookie);
        assertEquals(200, create.statusCode());
        var data = new LinkedHashMap<String, String>();
        data.put("_csrf", value(create.body(), "_csrf"));
        data.put("requestKey", value(create.body(), "requestKey"));
        data.put("version", "0");
        data.put("clientType", "MICROFINANCE");
        data.put("clientName", "Synthetic <script>alert(1)</script>");
        data.put("documentNumber", "HTTP-" + UUID.randomUUID());
        data.put("documentDate", "2026-11-03");
        data.put("validUntil", "2026-12-03");
        data.put("currency", "TZS");
        data.put("paymentMethod", "BANK_TRANSFER");
        data.put("items[0].description", "HTTP service");
        data.put("items[0].amount", "0.10");
        long before = count("sales_documents");
        var noCsrf = new LinkedHashMap<>(data);
        noCsrf.remove("_csrf");
        assertEquals(403, post("/sales/invoices", cookie, noCsrf).statusCode());
        data.put("addItem", "true");
        var added = post("/sales/invoices", cookie, data);
        assertEquals(200, added.statusCode());
        assertTrue(added.body().contains("items[1].description"));
        assertEquals(before, count("sales_documents"));
        assertTrue(added.body().contains("&lt;script&gt;"));
        assertTrue(added.body().contains("data-unsaved-draft=\"true\""));
        data.remove("addItem");
        data.put("items[1].description", "HTTP second");
        data.put("items[1].amount", "0.20");
        var removal = new LinkedHashMap<>(data);
        removal.put("removeItem", "0");
        var removed = post("/sales/invoices", cookie, removal);
        assertEquals(200, removed.statusCode());
        assertTrue(removed.body().contains("HTTP second"));
        assertFalse(removed.body().contains("items[1].description"));
        assertTrue(removed.body().contains("data-unsaved-draft=\"true\""));
        assertEquals(before, count("sales_documents"));
        var invalid = new LinkedHashMap<>(data);
        invalid.put("items[1].amount", "-1");
        var rejected = post("/sales/invoices", cookie, invalid);
        assertEquals(200, rejected.statusCode());
        assertTrue(rejected.body().contains("HTTP second"));
        assertEquals(before, count("sales_documents"));
        var saved = post("/sales/invoices", cookie, data);
        assertEquals(302, saved.statusCode());
        assertTrue(
                saved.headers()
                        .firstValue("location")
                        .orElseThrow()
                        .endsWith("/sales/invoices?saved"));
        assertEquals(before + 1, count("sales_documents"));
        long id =
                db.queryForObject(
                        "select id from sales_documents where document_number=?",
                        Long.class,
                        data.get("documentNumber"));
        assertEquals(new BigDecimal("0.30"), sales.find(SalesKind.INVOICE, id).amount());
        for (String route :
                List.of(
                        "/sales/invoices",
                        "/sales/prospects",
                        "/sales/invoices/" + id,
                        "/sales/invoices/" + id + "/edit",
                        "/sales/prospects/new")) {
            var en = get(route + "?lang=en", cookie);
            assertEquals(200, en.statusCode(), route);
            assertFalse(en.body().contains("Exception"));
            var sw = get(route + "?lang=sw", cookie);
            assertEquals(200, sw.statusCode(), route);
            assertTrue(
                    sw.body().contains("Ankara")
                            || sw.body().contains("ankara")
                            || sw.body().contains("watarajiwa"),
                    route);
        }
        var emptyHistory =
                get("/sales/invoices/" + id + "?lang=en&search=NO-HISTORY-MATCH", cookie);
        assertEquals(200, emptyHistory.statusCode());
        assertTrue(emptyHistory.body().contains("No records"));
        var exported = get("/sales/invoices/" + id + "/document.pdf?download=true", cookie);
        assertEquals(200, exported.statusCode());
        assertTrue(exported.body().startsWith("%PDF-"));
        assertTrue(
                exported.headers()
                        .firstValue("content-disposition")
                        .orElseThrow()
                        .startsWith("attachment"));
        assertTrue(
                exported.headers().firstValue("cache-control").orElseThrow().contains("no-store"));
    }
}
