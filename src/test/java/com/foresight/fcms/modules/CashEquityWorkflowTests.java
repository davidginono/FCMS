package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.EquityReporting.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fcms.mail.initial-delay=86400000",
            "fcms.mail.interval=86400000",
            "fcms.backup.interval=86400000",
            "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CashEquityWorkflowTests {
    static final String SCHEMA = "cash_equity_" + UUID.randomUUID().toString().replace("-", "");
    static final Path PROOF = Path.of(".verification/cash-equity-drills-20261115");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired AccountingService accounts;
    @Autowired JournalService journals;
    @Autowired StatementService statements;
    @Autowired StatementPresentation presentation;
    @Autowired StatementDrillService drill;
    @Autowired EquityReportingService reporting;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountingDocumentPdfService pdf;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @LocalServerPort int port;
    final String maker = "cash-equity-maker@test.invalid",
            reader = "cash-equity-reader@test.invalid",
            stranger = "cash-equity-stranger@test.invalid";
    final LocalDate today = LocalDate.now(),
            start = today.withDayOfYear(1),
            opening = start.minusYears(2);
    long makerId,
            readerId,
            cash,
            bank,
            equipment,
            receivable,
            income,
            expense,
            capital,
            reserve,
            contribution,
            transfer,
            earnings,
            setupRevision;
    String moneyBefore;

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @BeforeEach
    void signIn() {
        actor(maker);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long user(String email, int role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Cash Equity','Fixture',?,?,1,?) returning id",
                Long.class,
                email,
                passwords.encode("Verification123!"),
                role);
    }

    long account(String code, String name, String classification, boolean money) {
        long parent =
                db.queryForObject(
                        "select id from gl_accounts where code=?",
                        Long.class,
                        code.substring(0, 1) + "00000");
        for (int depth = 2; depth <= 4; depth++) {
            String next = depth == 4 ? code : code.substring(0, depth) + "0".repeat(6 - depth);
            var old = db.queryForList("select id from gl_accounts where code=?", Long.class, next);
            if (!old.isEmpty()) {
                parent = old.getFirst();
                continue;
            }
            var f = new AccountForm();
            f.setParentId(parent);
            f.setCode(next);
            f.setName(name + " " + depth);
            f.setNormalBalance(
                    Set.of("ASSET", "EXPENSE").contains(classification) ? "DEBIT" : "CREDIT");
            f.setCashAccount(depth == 4 && money);
            parent = accounts.saveAccount(null, f);
        }
        return parent;
    }

    long post(long debit, long credit, String amount, String flow, LocalDate date) {
        actor(maker);
        var f = new JournalForm();
        f.setKind("JOURNAL");
        f.setDate(date);
        f.setReference(UUID.randomUUID().toString());
        f.setDescription("Cash and equity acceptance");
        f.setEvidenceReference("Synthetic reviewed evidence");
        f.setCashFlow(flow);
        f.getLines().get(0).setAccountId(debit);
        f.getLines().get(0).setDebit(new BigDecimal(amount));
        f.getLines().get(1).setAccountId(credit);
        f.getLines().get(1).setCredit(new BigDecimal(amount));
        long id = journals.save(null, f);
        journals.transition(id, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(id, 1, "post", "Independent fixture review");
        return id;
    }

    ReportingDecisionForm decision(String action, long version) {
        var f = new ReportingDecisionForm();
        f.setAction(action);
        f.setVersion(version);
        f.setReason("Independent reporting review");
        return f;
    }

    long tag(long journal, Category category) {
        actor(maker);
        var f = reporting.movementForm(journal);
        f.setCategory(category);
        f.setReason("Evidence-based equity movement");
        long id = reporting.classify(journal, f);
        actor("admin@fcms.local");
        reporting.decide(id, decision("approve", 0));
        return id;
    }

    Form setup() {
        var f = reporting.form();
        for (var item :
                List.of(
                        new String[] {"Owner capital", "Mtaji wa wamiliki", "311001", "false"},
                        new String[] {
                            "Reserves / retained earnings",
                            "Akiba / faida iliyobaki",
                            "321001",
                            "true"
                        })) {
            var c = new ComponentForm();
            c.setName(item[0]);
            c.setNameSw(item[1]);
            c.setAccounts(item[2]);
            c.setRetained(Boolean.parseBoolean(item[3]));
            f.getComponents().add(c);
        }
        f.setReason("Approved component membership");
        return f;
    }

    @BeforeAll
    void fixture() throws Exception {
        Files.createDirectories(PROOF);
        makerId = user(maker, 1);
        readerId = user(reader, 2);
        user(stranger, 2);
        user("cash-equity-checker@test.invalid", 1);
        db.update(
                "insert into user_claims(user_id,claim) values"
                        + " (?,'accounting.read'),(?,'accounting.report')",
                readerId,
                readerId);
        actor(maker);
        var b = new BookForm();
        b.setOpeningDate(opening);
        b.setOpeningMode("ZERO");
        b.setPolicyReference("Cash/equity fixture");
        accounts.configure(b);
        actor("admin@fcms.local");
        accounts.approveBooks(0, "Independent opening review");
        for (int y = opening.getYear(); y <= today.getYear(); y++) {
            var p = new PeriodForm();
            p.setName("Cash equity " + y);
            p.setStartsOn(LocalDate.of(y, 1, 1));
            p.setEndsOn(LocalDate.of(y, 12, 31));
            accounts.createPeriod(p);
        }
        actor(maker);
        cash = account("111001", "Cash", "ASSET", true);
        bank = account("111002", "Bank", "ASSET", true);
        equipment = account("121001", "Equipment", "ASSET", false);
        receivable = account("112001", "Receivables", "ASSET", false);
        income = account("511001", "Income", "INCOME", false);
        expense = account("411001", "Expense", "EXPENSE", false);
        capital = account("311001", "Capital", "EQUITY", false);
        reserve = account("321001", "Reserve", "EQUITY", false);
        long previousCapital = post(cash, capital, "500", "FINANCING", today.minusYears(1));
        tag(previousCapital, Category.CONTRIBUTION);
        post(cash, income, "1000", "OPERATING", today);
        post(expense, cash, "200", "OPERATING", today);
        post(receivable, income, "300", "NONE", today);
        post(expense, equipment, "40", "NONE", today);
        post(cash, receivable, "150", "OPERATING", today);
        post(equipment, cash, "100", "INVESTING", today);
        contribution = post(cash, capital, "250", "FINANCING", today);
        post(bank, cash, "50", "INTERNAL", today);
        transfer = post(capital, reserve, "30", "NONE", today);
        earnings = post(income, reserve, "100", "NONE", today);
        tag(contribution, Category.CONTRIBUTION);
        tag(transfer, Category.TRANSFER);
        tag(earnings, Category.EARNINGS_TRANSFER);
        actor(maker);
        moneyBefore = digest();
        Files.writeString(PROOF.resolve("fixture-schema.txt"), SCHEMA);
        Files.writeString(PROOF.resolve("financial-before.txt"), moneyBefore);
    }

    String digest() {
        return db.queryForObject(
                "select md5((select coalesce(string_agg(row_to_json(j)::text,'' order by id),'')"
                    + " from gl_journals j)||(select coalesce(string_agg(row_to_json(l)::text,''"
                    + " order by id),'') from gl_lines l))",
                String.class);
    }

    RegisterQuery query() {
        var q = new RegisterQuery();
        q.setFrom(start);
        q.setTo(today);
        return q;
    }

    @Test
    @Order(1)
    void cashUsesActualCounterpartsExcludesInternalTransfersAndReconcilesSurplus() {
        var c = statements.comparison("cash-flow", start, today);
        assertEquals(0, new BigDecimal("1060.00").compareTo(c.current().profit()));
        assertEquals(0, new BigDecimal("950.00").compareTo(c.current().operatingCash()));
        assertEquals(0, new BigDecimal("1600.00").compareTo(c.current().cashClosing()));
        assertTrue(c.current().cashFlowMatches());
        assertTrue(c.current().equityMatches());
        assertEquals(
                0,
                c.current()
                        .profit()
                        .compareTo(
                                statements
                                        .comparison("income-statement", start, today)
                                        .current()
                                        .profit()));
        assertFalse(
                c.cashCounterparts().stream()
                        .anyMatch(r -> r.groupId() == accounts.account(cash).parentId()));
        var d = presentation.statement("cash-flow", c, Locale.ENGLISH);
        assertTrue(
                d.rows().stream()
                        .anyMatch(
                                r ->
                                        "report.profit".equals(r.drillLine())
                                                && r.cells().get(3).equals("1,060.00")));
        var operating =
                d.rows().stream()
                        .takeWhile(r -> !r.heading() || !r.label().contains("investing"))
                        .filter(r -> !r.heading() && !r.total())
                        .map(r -> r.cells().get(3))
                        .filter(s -> s.matches("[0-9,.()]+"))
                        .map(
                                s ->
                                        new BigDecimal(
                                                s.replace(",", "")
                                                        .replace("(", "-")
                                                        .replace(")", "")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("950").compareTo(operating));
        for (var row : d.rows())
            if (row.drillLine() != null) {
                var a = drill.accounts("cash-flow", row.drillLine(), query());
                var expected =
                        new BigDecimal(
                                row.cells()
                                        .get(3)
                                        .replace(",", "")
                                        .replace("(", "-")
                                        .replace(")", ""));
                assertEquals(0, expected.compareTo(a.total()), row.drillLine());
            }
    }

    @Test
    @Order(2)
    void setupIsReviewedIdempotentAndCannotPublishItsOwnProposal() {
        var f = setup();
        setupRevision = reporting.propose(f);
        assertEquals(setupRevision, reporting.propose(f));
        assertTrue(reporting.effective().components().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> reporting.decide(setupRevision, decision("approve", 0)));
        actor(reader);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> reporting.revision(setupRevision));
        actor("admin@fcms.local");
        reporting.decide(setupRevision, decision("approve", 0));
        assertEquals(2, reporting.effective().components().size());
        assertEquals("APPROVED", reporting.revision(setupRevision).state());
        actor(maker);
        f.setReason("Changed retry");
        assertThrows(IllegalArgumentException.class, () -> reporting.propose(f));
    }

    public record OracleInput(StatementService.Comparison source, List<Movement> movements) {}

    @Test
    @Order(3)
    void equityMatrixReconcilesEveryComponentAndExcludesClosingTransfersFromProfit() {
        var c = statements.comparison("changes-in-equity", start, today);
        var m = c.equityMatrix();
        assertTrue(m.checks().isEmpty(), m.checks().toString());
        assertEquals(List.of("component:0", "component:1"), m.columns());
        assertEquals(
                new BigDecimal("1060.00"),
                m.current().stream()
                        .filter(r -> r.key().equals("profit"))
                        .findFirst()
                        .orElseThrow()
                        .total());
        assertEquals(new BigDecimal("1810.00"), m.current().getLast().total());
        assertEquals(new BigDecimal("720.00"), m.current().getLast().values().get("component:0"));
        assertEquals(new BigDecimal("1090.00"), m.current().getLast().values().get("component:1"));
        assertNotNull(m.previous());
        assertEquals(new BigDecimal("500.00"), m.previous().getLast().total());
        assertTrue(m.current().stream().noneMatch(r -> r.key().equals("EARNINGS_TRANSFER")));
        var movementRows =
                db.query(
                        "select l.account_id,coalesce(r.payload->>'category','UNCLASSIFIED')"
                            + " category,j.effective_date,l.debit,l.credit from gl_journals j join"
                            + " gl_lines l on l.journal_id=j.id join gl_accounts a on"
                            + " a.id=l.account_id left join gl_equity_reporting r on"
                            + " r.kind='JOURNAL' and r.target_id=coalesce(j.reverses_id,j.id) and"
                            + " r.state='APPROVED' where j.state='POSTED' and j.kind<>'OPENING' and"
                            + " (a.classification='EQUITY' or"
                            + " r.payload->>'category'='EARNINGS_TRANSFER' and a.classification in"
                            + " ('INCOME','EXPENSE'))",
                        (r, n) ->
                                new Movement(
                                        r.getLong(1),
                                        Category.valueOf(r.getString(2)),
                                        r.getObject(3, LocalDate.class).getYear() < today.getYear(),
                                        r.getBigDecimal(4),
                                        r.getBigDecimal(5)));
        try {
            Files.createDirectories(PROOF.resolve("reference"));
            Files.writeString(
                    PROOF.resolve("reference/equity-input.json"),
                    new tools.jackson.databind.json.JsonMapper()
                            .writeValueAsString(new OracleInput(c, movementRows)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var d = presentation.statement("changes-in-equity", c, locale);
            assertEquals(-1, d.detailsBefore());
            assertEquals(.235f, d.columns().getFirst().weight());
            assertTrue(d.rows().getLast().total());
            assertEquals("", d.rows().getFirst().cells().get(2));
            for (var row : d.rows())
                for (var link : row.cellDrills().entrySet()) {
                    var uri = java.net.URI.create(link.getValue());
                    String route = uri.getPath();
                    String line = route.substring(route.lastIndexOf('/') + 1);
                    var q = query();
                    if (uri.getQuery().contains(start.minusYears(1).toString())) {
                        q.setFrom(start.minusYears(1));
                        q.setTo(today.minusYears(1));
                    }
                    var a = drill.accounts("changes-in-equity", line, q);
                    String val = row.cells().get(link.getKey());
                    assertEquals(
                            0,
                            new BigDecimal(val.replace(",", "").replace("(", "-").replace(")", ""))
                                    .compareTo(a.total()),
                            line);
                }
            try {
                Files.write(
                        PROOF.resolve("equity-" + locale.getLanguage() + ".pdf"),
                        pdf.statement(d, locale));
                Files.write(
                        PROOF.resolve("reference/spring-equity-" + locale.getLanguage() + ".pdf"),
                        pdf.statement(d, locale));
                Files.writeString(
                        PROOF.resolve("reference/source-equity-" + locale.getLanguage() + ".json"),
                        new tools.jackson.databind.json.JsonMapper().writeValueAsString(d));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @Order(4)
    void contributionDrillUsesFullAccountTrialBalancesAndExactMembership() {
        var q = query();
        var id = accounts.account(receivable).parentId();
        var d = drill.accounts("cash-flow", "cash-adj-" + id, q);
        assertEquals(0, new BigDecimal("150.00").compareTo(d.total()));
        var row = d.trial().records().rows().getFirst();
        assertEquals(receivable, row.id());
        assertEquals(0, new BigDecimal("300.00").compareTo(row.debit()));
        assertEquals(0, new BigDecimal("150.00").compareTo(row.credit()));
        assertEquals(0, new BigDecimal("150.00").compareTo(row.closingDebit()));
        assertEquals(1, d.trial().totals().count());
        var entries = drill.entries("cash-flow", "cash-adj-" + id, receivable, q);
        assertEquals(1, entries.records().rows().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> drill.entries("cash-flow", "cash-adj-" + id, cash, q));
        q.setSearch("%_!");
        assertTrue(drill.accounts("cash-flow", "cash-adj-" + id, q).records().rows().isEmpty());
    }

    @Test
    @Order(5)
    void invalidMovementCategoriesAndForgedAccountsDoNotPublish() {
        var f = reporting.movementForm(earnings);
        f.setCategory(Category.CONTRIBUTION);
        f.setReason("Wrong category");
        assertThrows(IllegalArgumentException.class, () -> reporting.classify(earnings, f));
        var setup = reporting.form();
        setup.getComponents().getFirst().setAccounts("111001");
        setup.setReason("Wrong account type");
        assertThrows(IllegalArgumentException.class, () -> reporting.propose(setup));
        actor(stranger);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> statements.comparison("cash-flow", start, today));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> drill.accounts("cash-flow", "cash-adj-0", query()));
    }

    @Test
    @Order(6)
    void replacementsRetainHistoryAndDatabaseRejectsPayloadTampering() {
        var f = reporting.form();
        f.setReason("Retained reviewed replacement");
        long id = reporting.propose(f);
        actor("admin@fcms.local");
        reporting.decide(id, decision("approve", 0));
        assertEquals("RETIRED", reporting.revision(setupRevision).state());
        assertEquals("APPROVED", reporting.revision(id).state());
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from gl_equity_reporting where kind='SETUP' and"
                                + " state='APPROVED'",
                        Integer.class));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update gl_equity_reporting set payload='{}'::jsonb where id=?",
                                id));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("delete from gl_equity_reporting where id=?", setupRevision));
    }

    HttpResponse<String> get(HttpClient client, String route) throws Exception {
        var uri = URI.create(route.startsWith("/") ? "http://127.0.0.1:" + port + route : route);
        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(port, uri.getPort());
        return client.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(HttpClient client, String route, Map<String, String> fields)
            throws Exception {
        String body =
                fields.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(
                                                        e.getKey(),
                                                        java.nio.charset.StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(),
                                                        java.nio.charset.StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    Map<String, String> hidden(String html) {
        var fields = new LinkedHashMap<String, String>();
        var input = Pattern.compile("<input\\b[^>]*>").matcher(html);
        while (input.find()) {
            var tag = input.group();
            if (tag.contains("type=\"checkbox\"") && !tag.contains("checked")) continue;
            var name = Pattern.compile("\\bname=\"([^\"]+)\"").matcher(tag);
            var value = Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(tag);
            if (name.find() && value.find())
                fields.put(name.group(1), value.group(1).replace("&amp;", "&"));
        }
        return fields;
    }

    HttpClient login(String email) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(cookies)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var first = get(web, "/login");
        assertEquals(200, first.statusCode());
        var f = hidden(first.body());
        f.put("email", email);
        f.put("password", email.equals("admin@fcms.local") ? "ChangeMe123!" : "Verification123!");
        assertEquals(302, post(web, "/login", f).statusCode());
        return web;
    }

    @Test
    @Order(7)
    void nativeRoutesKeepLabelsDatesMembershipCsrfAndRecoverableValues() throws Exception {
        var web = login(maker);
        String range = "?from=" + start + "&to=" + today;
        for (String lang : List.of("en", "sw"))
            for (String type : List.of("cash-flow", "changes-in-equity"))
                for (String suffix : List.of("", "/print", "/print.pdf")) {
                    var response =
                            get(
                                    web,
                                    "/accounting/statements/"
                                            + type
                                            + suffix
                                            + range
                                            + "&lang="
                                            + lang);
                    assertEquals(200, response.statusCode(), type + suffix + lang);
                    if (suffix.equals("/print.pdf")) {
                        assertTrue(response.body().startsWith("%PDF-"));
                        assertTrue(
                                response.headers()
                                        .firstValue("Cache-Control")
                                        .orElse("")
                                        .contains("no-store"));
                    } else {
                        assertTrue(
                                response.body()
                                        .contains(
                                                type.equals("changes-in-equity")
                                                        ? "fs-equity-period"
                                                        : suffix.isEmpty()
                                                                ? "fs-paper"
                                                                : "fs-print-document"));
                        if (type.equals("changes-in-equity") && suffix.isEmpty())
                            assertTrue(
                                    response.body()
                                            .contains(
                                                    "/lines/matrix-closing-component-0?from="
                                                            + start
                                                            + "&amp;to="
                                                            + today));
                    }
                }
        long group = accounts.account(receivable).parentId();
        String line = "/accounting/statements/cash-flow/lines/cash-adj-" + group;
        var page = get(web, line + range);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("fs-paper fs-trial"));
        assertTrue(
                page.body()
                        .contains(
                                line
                                        + "/accounts/"
                                        + receivable
                                        + "?from="
                                        + start
                                        + "&amp;to="
                                        + today));
        assertEquals(400, get(web, line + "/accounts/" + cash + range).statusCode());
        for (String route :
                List.of(
                        "/accounting/equity-reporting",
                        "/accounting/equity-reporting/movements",
                        "/accounting/equity-reporting/new",
                        "/accounting/equity-reporting/movements/" + contribution,
                        "/accounting/equity-reporting/" + setupRevision)) {
            assertEquals(200, get(web, route).statusCode(), route);
        }
        for (String suffix : List.of("/print", "/print.pdf")) {
            var exported = get(web, line + suffix + range);
            assertEquals(200, exported.statusCode());
            if (suffix.endsWith("pdf")) {
                assertTrue(exported.body().startsWith("%PDF-"));
                assertTrue(
                        exported.headers()
                                .firstValue("Cache-Control")
                                .orElse("")
                                .contains("no-store"));
            } else assertTrue(exported.body().contains("fs-print-trial"));
        }
        var entryPaper = get(web, line + "/accounts/" + receivable + range);
        assertEquals(200, entryPaper.statusCode());
        assertTrue(entryPaper.body().contains("fs-paper fs-trial"));
        assertTrue(entryPaper.body().contains("/accounting/journals/"));
        var form = get(web, "/accounting/equity-reporting/new");
        var fields = hidden(form.body());
        fields.put("reason", "");
        var invalid = post(web, "/accounting/equity-reporting", fields);
        assertEquals(200, invalid.statusCode());
        assertTrue(invalid.body().contains("Owner capital"));
        assertTrue(invalid.body().contains("reason.errors"));
        fields.put("reason", "Native retained proposal <script>literal</script>");
        fields.put("operation", "save");
        var saved = post(web, "/accounting/equity-reporting", fields);
        assertEquals(302, saved.statusCode());
        var location = saved.headers().firstValue("Location").orElseThrow();
        var detail = get(web, location);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("&lt;script&gt;literal&lt;/script&gt;"));
        assertFalse(detail.body().contains("value=\"approve\""));
        var decision = hidden(detail.body());
        decision.put("action", "cancel");
        decision.put("reason", "Cancel native fixture");
        String decisionPath = URI.create(location).getPath() + "/decision";
        assertEquals(302, post(web, decisionPath, decision).statusCode());
        assertEquals(
                403,
                post(web, "/accounting/equity-reporting", Map.of("reason", "No CSRF"))
                        .statusCode());
        var read = login(reader);
        assertEquals(200, get(read, "/accounting/equity-reporting").statusCode());
        assertEquals(403, get(read, "/accounting/equity-reporting/new").statusCode());
        var denied = login(stranger);
        assertEquals(403, get(denied, line + range).statusCode());
    }

    @Test
    @Order(8)
    void paperSourcesRetainCompleteFinancialFaces() throws Exception {
        Files.createDirectories(PROOF.resolve("reference"));
        var mapper = new tools.jackson.databind.json.JsonMapper();
        var cashSource = statements.comparison("cash-flow", start, today);
        var trial =
                drill.accounts(
                                "cash-flow",
                                "cash-adj-" + accounts.account(receivable).parentId(),
                                query())
                        .trial();
        var ledger =
                drill.entries(
                                "cash-flow",
                                "cash-adj-" + accounts.account(receivable).parentId(),
                                receivable,
                                query())
                        .ledger();
        Files.writeString(
                PROOF.resolve("reference/trial-input.json"), mapper.writeValueAsString(trial));
        Files.writeString(
                PROOF.resolve("reference/ledger-input.json"), mapper.writeValueAsString(ledger));
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var faces =
                    Map.of(
                            "cash-flow",
                            presentation.statement("cash-flow", cashSource, locale),
                            "trial-drill",
                            presentation.trialPage(trial, "", locale),
                            "ledger-drill",
                            presentation.ledger(ledger, locale));
            for (var entry : faces.entrySet()) {
                String name = entry.getKey() + "-" + locale.getLanguage();
                Files.writeString(
                        PROOF.resolve("reference/source-" + name + ".json"),
                        mapper.writeValueAsString(entry.getValue()));
                Files.write(
                        PROOF.resolve("reference/spring-" + name + ".pdf"),
                        pdf.statement(entry.getValue(), locale));
            }
        }
    }

    @Test
    @Order(9)
    void concurrentRetriesHaveOneRevisionAndOneIndependentReview() throws Exception {
        var f = reporting.form();
        f.setReason("Concurrent reporting request");
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var jobs = new ArrayList<Future<Long>>();
        try {
            for (int i = 0; i < 2; i++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    actor(maker);
                                    ready.countDown();
                                    go.await();
                                    try {
                                        return reporting.propose(f);
                                    } finally {
                                        clear();
                                    }
                                }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            long id = jobs.getFirst().get(30, TimeUnit.SECONDS);
            assertEquals(id, jobs.getLast().get(30, TimeUnit.SECONDS));
            var reviews = new ArrayList<Future<Boolean>>();
            var gate = new CountDownLatch(1);
            for (String who : List.of("admin@fcms.local", "cash-equity-checker@test.invalid"))
                reviews.add(
                        pool.submit(
                                () -> {
                                    actor(who);
                                    gate.await();
                                    try {
                                        reporting.decide(id, decision("approve", 0));
                                        return true;
                                    } catch (org.springframework.dao
                                                    .OptimisticLockingFailureException
                                            | IllegalArgumentException e) {
                                        return false;
                                    } finally {
                                        clear();
                                    }
                                }));
            gate.countDown();
            int successes = 0;
            for (var result : reviews) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            actor(maker);
            assertEquals("APPROVED", reporting.revision(id).state());
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from gl_equity_reporting where kind='SETUP' and"
                                    + " state='APPROVED'",
                            Integer.class));
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Order(10)
    void failedAuditRollsBackReplacementAndUnclassifiedMovementsStayVisible() {
        var f = reporting.form();
        Long base = f.getBaseId();
        f.setReason("Audit rollback fixture");
        long id = reporting.propose(f);
        db.execute(
                "create function fail_equity_audit() returns trigger language plpgsql as $$ begin"
                    + " if new.action='accounting.equity.approve' then raise exception 'Synthetic"
                    + " audit failure'; end if; return new; end $$");
        db.execute(
                "create trigger fail_equity_audit before insert on audit_events for each row"
                        + " execute function fail_equity_audit()");
        try {
            actor("admin@fcms.local");
            assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () -> reporting.decide(id, decision("approve", 0)));
            assertEquals("APPROVED", reporting.revision(base).state());
            assertEquals("PENDING", reporting.revision(id).state());
        } finally {
            db.execute("drop trigger fail_equity_audit on audit_events");
            db.execute("drop function fail_equity_audit()");
        }
        actor(maker);
        reporting.decide(id, decision("cancel", 0));
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        tx.executeWithoutResult(
                status -> {
                    post(cash, capital, "10", "FINANCING", today);
                    actor(maker);
                    var c = statements.comparison("changes-in-equity", start, today);
                    assertTrue(c.equityMatrix().checks().contains("movements"));
                    assertTrue(
                            c.equityMatrix().current().stream()
                                    .anyMatch(
                                            r ->
                                                    r.key().equals("UNCLASSIFIED")
                                                            && r.total()
                                                                            .compareTo(
                                                                                    new BigDecimal(
                                                                                            "10"))
                                                                    == 0));
                    status.setRollbackOnly();
                });
        var unavailable = statements.comparison("changes-in-equity", opening, today);
        assertFalse(unavailable.previousAvailable());
        assertNull(unavailable.equityMatrix().previous());
    }

    @AfterAll
    void unchanged() throws Exception {
        if (moneyBefore != null) {
            assertEquals(moneyBefore, digest());
            Files.writeString(PROOF.resolve("financial-after.txt"), digest());
        }
        SecurityContextHolder.clearContext();
    }
}
