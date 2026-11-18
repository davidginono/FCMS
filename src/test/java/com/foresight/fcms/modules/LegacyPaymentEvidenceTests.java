package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.finance.expense.service.PaymentEvidenceService;
import com.foresight.fcms.finance.service.FinancePostingSourceService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(LegacyPaymentEvidenceTests.LegacyMigration.class)
class LegacyPaymentEvidenceTests {
    static final String SCHEMA = "legacy_evidence_" + UUID.randomUUID().toString().replace("-", "");
    static final String POLICY = "Reviewed legacy opening policy";
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam"));
    static final LocalDate OPENING = LocalDate.of(TODAY.getYear() - 2, 1, 1);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("fcms.backup.interval", () -> "3600000");
        p.add("fcms.mail.interval", () -> "3600000");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyMigration {
        @Bean
        FlywayMigrationStrategy historicalFixtures() {
            return flyway -> {
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .target("15")
                        .load()
                        .migrate();
                var db = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                long reviewer =
                        db.queryForObject(
                                "insert into"
                                    + " users(first_name,last_name,email,password,status_id,role_id)"
                                    + " select"
                                    + " 'Legacy','Reviewer','legacy-reviewer@test.invalid',password,1,1"
                                    + " from users where id=1 returning id",
                                Long.class);
                db.update(
                        "insert into"
                            + " gl_books(id,opening_date,policy_reference,opening_mode,state,maker_id,checker_id,approved_at)"
                            + " values (true,?,?,'ZERO','APPROVED',1,?,clock_timestamp())",
                        OPENING,
                        POLICY,
                        reviewer);
                long period =
                        db.queryForObject(
                                "insert into gl_periods(name,starts_on,ends_on,created_by) values"
                                        + " ('Retained closed year',?,?,1) returning id",
                                Long.class,
                                OPENING,
                                OPENING.plusYears(1).minusDays(1));
                db.update(
                        "update gl_periods set state='CLOSED',closed_by=?,close_reason='Retained"
                                + " reviewed closure',closed_at=clock_timestamp(),version=version+1"
                                + " where id=?",
                        reviewer,
                        period);
                for (int n = 1; n <= 6; n++) {
                    long id =
                            db.queryForObject(
                                    "insert into"
                                        + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id,payment_reference)"
                                        + " values (?,1,'Office',?,'Synthetic"
                                        + " supplier',12.34,?,4,?) returning id",
                                    Long.class,
                                    OPENING.minusYears(1),
                                    "Legacy verification " + n,
                                    String.valueOf(OPENING.getYear() - 1),
                                    n == 1 ? " LEGACY-1 " : "LEGACY-" + n);
                    if (n == 3) {
                        db.update(
                                "insert into"
                                    + " expense_reversals(expense_id,actor_id,reason,request_key)"
                                    + " values (?,1,'Retained historical correction',?)",
                                id,
                                UUID.randomUUID());
                        db.update(
                                "update expenses set status_id=(select id from expense_status where"
                                        + " name='reversed') where id=?",
                                id);
                    }
                }
                db.update(
                        "insert into"
                            + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id)"
                            + " values (?,1,'Office','Legacy zero-value history','Synthetic"
                            + " supplier',0,?,4)",
                        OPENING.minusYears(1),
                        String.valueOf(OPENING.getYear() - 1));
                flyway.migrate();
            };
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @Autowired PaymentEvidenceService evidence;
    @Autowired com.foresight.fcms.finance.expense.service.ExpenseService expenses;
    @Autowired AccessService access;
    @Autowired AccountingService accounting;
    @Autowired PostingMappingService mappings;
    @Autowired SourcePostingService sources;
    @Autowired JournalService journals;
    @Autowired FinancePostingSourceService finance;
    @Autowired SourceCoverageService coverage;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactionManager;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    String reverser = "evidence-reverser@test.invalid";
    String maker = "evidence-maker@test.invalid",
            payer = "evidence-payer@test.invalid",
            checker = "evidence-checker@test.invalid",
            staff = "evidence-staff@test.invalid";
    long makerId, payerId, checkerId, staffId, mapping, period;

    @BeforeAll
    void setup() {
        OperationalTestClaims.enable(db);
        makerId = user(maker, 1);
        user(reverser, 1);
        payerId = user(payer, 1);
        checkerId = user(checker, 1);
        staffId = user(staff, 2);
        actor(maker);
        period = AccountingHistoryFixtures.period(db, "Current verification year", TODAY.withDayOfYear(1), TODAY.withDayOfYear(1).plusYears(1).minusDays(1), maker);
        long bank = glAccount("141001", "Legacy bank", "ASSET", true),
                cost = glAccount("451001", "Legacy cost", "EXPENSE", false);
        var m = new PostingMappingForm();
        m.setEvent("EXPENSE_PAYMENT");
        m.setDebitAccountId(cost);
        m.setCreditAccountId(bank);
        m.setVoucherKind("PAYMENT");
        m.setCashFlow("OPERATING");
        m.setPolicyReference("Verified legacy payment treatment");
        mapping = mappings.create(m);
        actor(checker);
        mappings.review(mapping, 0, "approve", "Reviewed mapping");
    }

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    long user(String email, long role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Evidence','Verifier',?,?,1,?) returning id",
                Long.class,
                email,
                passwords.encode("Verification123!"),
                role);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long expense(int n) {
        return db.queryForObject(
                "select id from expenses where description=?",
                Long.class,
                "Legacy verification " + n);
    }

    PaymentEvidenceForm form(int n, LocalDate date) {
        var f = new PaymentEvidenceForm();
        f.setPaidOn(date);
        f.setPayerId(payerId);
        f.setPaymentReference("LEGACY-" + n);
        f.setDocumentReference("Payment document " + n);
        f.setExplanation("Retained dated payment evidence " + n);
        return f;
    }

    PaymentEvidenceReview approve(long version) {
        return new PaymentEvidenceReview(
                version, "approve", "Independently checked dated evidence");
    }

    long create(int n, PaymentEvidenceForm f) {
        actor(maker);
        return evidence.create(expense(n), f);
    }

    void postSource(String kind, long sourceId, Long mappingId) {
        actor(kind.equals("EXPENSE_REVERSAL") ? reverser : maker);
        var f = new SourcePreparationForm();
        f.setMappingId(mappingId);
        f.setEvidenceReference("Retained payment and reviewed source");
        long j = sources.prepare(kind, sourceId, f);
        journals.transition(j, 0, "submit", "");
        actor(checker);
        journals.transition(j, 1, "post", "Verified source payment");
        assertEquals("POSTED", journals.find(j).state());
    }

    @Test
    void reviewedHistoricalEvidenceCompletesMigrationResolutionCoverageAndPosting()
            throws Exception {
        actor(checker);
        assertEquals(
                6,
                db.queryForObject(
                        "select count(*) from expense_legacy_payment_candidates", Integer.class));
        assertTrue(
                evidence.list(new RegisterQuery()).rows().stream()
                        .allMatch(p -> p.evidenceState().equals("UNVERIFIED")));
        assertNull(finance.find("EXPENSE", expense(1)).date());
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from finance_gl_sources where source_kind='EXPENSE' and"
                                + " signed_amount=0",
                        Integer.class));
        double createdBefore = metrics.counter("fcms.expense.evidence", "action", "create").count();
        var first = form(1, TODAY);
        long proof = createHistoricalEvidence(first);
        actor(maker);
        assertEquals(proof, evidence.create(expense(1), first));
        assertEquals(
                createdBefore + 1,
                metrics.counter("fcms.expense.evidence", "action", "create").count());
        first.setDocumentReference("Changed retry");
        assertThrows(IllegalArgumentException.class, () -> evidence.create(expense(1), first));
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence.review(expense(1), proof, approve(0)));
        actor(payer);
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence.review(expense(1), proof, approve(0)));
        actor("admin@fcms.local");
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence.review(expense(1), proof, approve(0)));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update expense_payment_evidence set"
                                    + " state='APPROVED',checker_id=maker_id,review_comment='Self"
                                    + " review',reviewed_at=clock_timestamp(),version=version+1"
                                    + " where id=?",
                                proof));
        approveHistoricalEvidence(proof);
        actor(checker);
        evidence.review(expense(1), proof, approve(0));
        assertEquals(TODAY, finance.find("EXPENSE", expense(1)).date());
        assertEquals(payerId, finance.find("EXPENSE", expense(1)).actorId());
        assertNull(
                db.queryForObject(
                        "select paid_on from expenses where id=?", LocalDate.class, expense(1)));
        assertNull(
                db.queryForObject(
                        "select paid_at from expenses where id=?",
                        java.sql.Timestamp.class,
                        expense(1)));
        assertEquals(
                " LEGACY-1 ",
                db.queryForObject(
                        "select payment_reference from expenses where id=?",
                        String.class,
                        expense(1)));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update expense_payment_evidence set explanation='tampered' where"
                                        + " id=?",
                                proof));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from expense_payment_evidence where id=?", proof));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "delete from expense_legacy_payment_candidates where expense_id=?",
                                expense(1)));
        postSource("EXPENSE", expense(1), mapping);
        var preopening = form(2, OPENING.minusDays(1));
        long rejected = create(2, preopening);
        actor(checker);
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence.review(expense(2), rejected, approve(0)));
        assertNull(finance.find("EXPENSE", expense(2)).date());
        evidence.review(
                expense(2),
                rejected,
                new PaymentEvidenceReview(0, "reject", "Opening policy evidence required"));
        preopening.setRequestKey(UUID.randomUUID());
        preopening.setOpeningPolicyReference(POLICY);
        long covered = create(2, preopening);
        actor(checker);
        evidence.review(expense(2), covered, approve(0));
        assertEquals("OPENING_COVERED", sources.find("EXPENSE", expense(2)).coverage());
        assertTrue(
                Boolean.TRUE.equals(
                        db.queryForObject(
                                "select recorded_at>c.approved_at and opening_verified from"
                                    + " finance_gl_sources s cross join finance_gl_cutover c where"
                                    + " source_kind='EXPENSE' and source_id=?",
                                Boolean.class,
                                expense(2))),
                "Opening treatment must not backdate verification");
        assertEquals(
                2,
                evidence.workspace(expense(2), covered, new RegisterQuery())
                        .history()
                        .rows()
                        .size());
        assertEquals(" LEGACY-1 ", evidence.find(expense(1), proof).paymentReference());
        actor(reverser);
        expenses.transition(
                expense(2),
                0,
                "reverse",
                "Actual correction of opening-covered payment",
                "",
                UUID.randomUUID());
        long openingReversal =
                db.queryForObject(
                        "select id from expense_reversals where expense_id=?",
                        Long.class,
                        expense(2));
        long openingBank = glAccount("141001", "Legacy bank", "ASSET", true);
        long openingEquity = glAccount("331001", "Reviewed opening correction", "EQUITY", false);
        var correction = new PostingMappingForm();
        correction.setEvent("EXPENSE_OPENING_REVERSAL");
        correction.setDebitAccountId(openingBank);
        correction.setCreditAccountId(openingEquity);
        correction.setVoucherKind("RECEIPT");
        correction.setCashFlow("OPERATING");
        correction.setPolicyReference("Explicit reviewed opening correction treatment");
        long correctionMapping = mappings.create(correction);
        actor(checker);
        mappings.review(correctionMapping, 0, "approve", "Reviewed opening correction");
        assertEquals("READY", sources.find("EXPENSE_REVERSAL", openingReversal).coverage());
        postSource("EXPENSE_REVERSAL", openingReversal, correctionMapping);
        long reversed = create(3, form(3, TODAY));
        actor(checker);
        evidence.review(expense(3), reversed, approve(0));
        assertEquals("reversed", evidence.payment(expense(3)).expenseStatus());
        long reversal =
                db.queryForObject(
                        "select id from expense_reversals where expense_id=?",
                        Long.class,
                        expense(3));
        assertEquals("WAITING_ORIGINAL", sources.find("EXPENSE_REVERSAL", reversal).coverage());
        postSource("EXPENSE", expense(3), mapping);
        postSource("EXPENSE_REVERSAL", reversal, mapping);
        var cancelledForm = form(4, TODAY);
        long cancelled = create(4, cancelledForm);
        evidence.review(
                expense(4),
                cancelled,
                new PaymentEvidenceReview(0, "cancel", "Replace incomplete document"));
        cancelledForm.setRequestKey(UUID.randomUUID());
        long replacement = create(4, cancelledForm);
        actor(checker);
        evidence.review(expense(4), replacement, approve(0));
        postSource("EXPENSE", expense(4), mapping);
        var concurrent = form(5, TODAY);
        var pool = Executors.newFixedThreadPool(2);
        long concurrentId;
        try {
            Callable<Long> command =
                    () -> {
                        actor(maker);
                        try {
                            return evidence.create(expense(5), concurrent);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
            var one = pool.submit(command);
            var two = pool.submit(command);
            concurrentId = one.get(30, TimeUnit.SECONDS);
            assertEquals(concurrentId, two.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from expense_payment_evidence where expense_id=?",
                        Integer.class,
                        expense(5)));
        verifyPeriodLock(concurrentId);
        postSource("EXPENSE", expense(5), mapping);
        long closed = create(6, form(6, OPENING.plusDays(3)));
        actor(checker);
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> evidence.review(expense(6), closed, approve(0)));
        assertEquals("PENDING", evidence.find(expense(6), closed).state());
        assertNull(finance.find("EXPENSE", expense(6)).date());
        actor(maker);
        evidence.review(
                expense(6),
                closed,
                new PaymentEvidenceReview(0, "cancel", "Use corrected dated document"));
        long resolved = create(6, form(6, TODAY));
        actor(checker);
        evidence.review(expense(6), resolved, approve(0));
        postSource("EXPENSE", expense(6), mapping);
        actor(checker);
        assertTrue(coverage.through(TODAY).complete());
        AccountingHistoryFixtures.periodState(db, period, true, checker);
        assertEquals("CLOSED", db.queryForObject("select state from gl_periods where id=?", String.class, period));
        var sorted = new RegisterQuery();
        sorted.setSort("expenseStatus");
        sorted.setDirection("asc");
        var states = evidence.list(sorted).rows().stream().map(p -> p.expenseStatus()).toList();
        assertEquals(states.stream().sorted().toList(), states);
        verifyHttp(proof, covered);
        db.update(
                "insert into users(first_name,last_name,email,password,status_id,role_id) select"
                    + " 'Historical lookup','Person"
                    + " '||n,'historical-lookup-'||n||'@test.invalid',u.password,case when n=30"
                    + " then 2 else 1 end,2 from generate_series(1,30) n cross join users u where"
                    + " u.id=?",
                makerId);
        var firstPayers = evidence.payers("Historical lookup", 0);
        var secondPayers = evidence.payers("Historical lookup", 1);
        assertEquals(25, firstPayers.items().size());
        assertTrue(firstPayers.hasNext());
        assertEquals(5, secondPayers.items().size());
        assertFalse(secondPayers.hasNext());
        assertTrue(
                firstPayers.items().stream()
                        .noneMatch(
                                a ->
                                        secondPayers.items().stream()
                                                .anyMatch(b -> a.id() == b.id())));
        long inactivePayer =
                db.queryForObject(
                        "select id from users where email='historical-lookup-30@test.invalid'",
                        Long.class);
        assertEquals(inactivePayer, evidence.selectedPayer(inactivePayer).getFirst().id());

        actor(staff);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> evidence.list(new RegisterQuery()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> evidence.create(expense(1), form(1, TODAY)));
    }

    String encoded(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    String token(String html) {
        var matcher =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(html);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    java.net.http.HttpResponse<String> get(java.net.http.HttpClient web, String route)
            throws Exception {
        return web.send(
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:" + port + route))
                        .timeout(Duration.ofSeconds(45))
                        .GET()
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    java.net.http.HttpResponse<String> post(
            java.net.http.HttpClient web, String route, String input) throws Exception {
        return web.send(
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:" + port + route))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    long createHistoricalEvidence(PaymentEvidenceForm f) throws Exception {
        // Exercise retained source-accounting history; its former browser form is retired.
        var web = browser(maker, "Verification123!");
        assertEquals(410, get(web, "/finance/expenses/" + expense(1) + "/evidence/new").statusCode());
        actor(maker);
        long id = evidence.create(expense(1), f);
        assertEquals("PENDING", evidence.find(expense(1), id).state());
        assertEquals(0, new java.math.BigDecimal("12.34").compareTo(evidence.payment(expense(1)).amount()));
        return id;
    }

    void approveHistoricalEvidence(long proof) throws Exception {
        actor(checker);
        evidence.review(expense(1), proof, approve(0));
        assertEquals("APPROVED", evidence.find(expense(1), proof).state());
        evidence.review(expense(1), proof, approve(0));
        assertEquals("APPROVED", evidence.find(expense(1), proof).state());
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> evidence.review(expense(1), proof, new PaymentEvidenceReview(0, "reject", "Changed stale review")));
    }

    void verifyPeriodLock(long proof) throws Exception {
        var ds = db.getDataSource();
        assertNotNull(ds);
        var pool = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try (var connection = ds.getConnection()) {
            connection.setAutoCommit(false);
            int blocker;
            try (var st = connection.createStatement();
                    var r =
                            st.executeQuery(
                                    "select"
                                        + " pg_backend_pid(),pg_advisory_xact_lock(hashtextextended('FCMS_FINANCE_PERIOD_CLOSE',0))")) {
                assertTrue(r.next());
                blocker = r.getInt(1);
            }
            var work =
                    pool.submit(
                            () -> {
                                actor(checker);
                                try {
                                    return new TransactionTemplate(transactionManager)
                                            .execute(
                                                    tx -> {
                                                        pid.set(
                                                                db.queryForObject(
                                                                        "select pg_backend_pid()",
                                                                        Integer.class));
                                                        entered.countDown();
                                                        evidence.review(
                                                                expense(5), proof, approve(0));
                                                        return true;
                                                    });
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            });
            try {
                assertTrue(entered.await(15, TimeUnit.SECONDS));
                boolean blocked = false;
                for (int attempt = 0; attempt < 100 && !blocked; attempt++) {
                    blocked =
                            Boolean.TRUE.equals(
                                    db.queryForObject(
                                            "select ?=any(pg_blocking_pids(?))",
                                            Boolean.class,
                                            blocker,
                                            pid.get()));
                    if (!blocked) Thread.sleep(50);
                }
                assertTrue(blocked, "Evidence review waits for the close boundary");
            } finally {
                connection.commit();
            }
            assertTrue(work.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    void verifyHttp(long proof, long covered) throws Exception {
        var web = browser(checker, "Verification123!");
        String base = "http://127.0.0.1:" + port;
        for (String language : List.of("en", "sw"))
            for (String route :
                    List.of(
                            "/finance/expenses/evidence",
                            "/finance/expenses/" + expense(1) + "/evidence",
                            "/finance/expenses/" + expense(1) + "/evidence/" + proof,
                            "/finance/expenses/" + expense(2) + "/evidence/" + covered,
                            "/accounting/sources/EXPENSE/" + expense(1))) {
                var response =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        base + route + "?lang=" + language))
                                        .timeout(Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                if (route.startsWith("/finance/expenses")) {
                    assertEquals(410, response.statusCode(), route);
                    assertTrue(response.body().isEmpty());
                } else {
                    assertEquals(200, response.statusCode(), route);
                    assertTrue(response.body().contains("</html>"));
                    assertFalse(response.body().contains("/finance/expenses/"));
                }
            }
        var denied = browser(staff, "Verification123!");
        assertEquals(
                410,
                denied.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        base + "/finance/expenses/evidence"))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString())
                        .statusCode());
        var noCsrf =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                base
                                                        + "/finance/expenses/"
                                                        + expense(1)
                                                        + "/evidence/"
                                                        + proof
                                                        + "/review"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(
                                        java.net.http.HttpRequest.BodyPublishers.ofString(
                                                "version=0&action=approve&comment=Retry"))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrf.statusCode());
    }

    long glAccount(String code, String name, String classification, boolean cash) {
        var q = new RegisterQuery();
        q.setSearch(code);
        var existing =
                accounting.accounts(q, null).rows().stream()
                        .filter(a -> a.code().equals(code))
                        .findFirst();
        if (existing.isPresent()) return existing.get().id();
        long parent =
                db.queryForObject(
                        "select id from gl_accounts where code=?",
                        Long.class,
                        code.substring(0, 1) + "00000");
        for (int depth = 2; depth <= 4; depth++) {
            String next = depth == 4 ? code : code.substring(0, depth) + "0".repeat(6 - depth);
            q.setSearch(next);
            var found =
                    accounting.accounts(q, null).rows().stream()
                            .filter(a -> a.code().equals(next))
                            .findFirst();
            if (found.isPresent()) {
                parent = found.get().id();
                continue;
            }
            var form = new com.foresight.fcms.accounting.dto.AccountForm();
            form.setParentId(parent);
            form.setCode(next);
            form.setName(depth == 4 ? name : name + " group " + depth);
            form.setNormalBalance(
                    Set.of("ASSET", "EXPENSE").contains(classification) ? "DEBIT" : "CREDIT");
            form.setCashAccount(depth == 4 && cash);
            parent = accounting.saveAccount(null, form);
        }
        return parent;
    }

    private java.net.http.HttpClient browser(String email, String password) throws Exception {
        var cookies = new java.net.CookieManager();
        cookies.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
        var client =
                java.net.http.HttpClient.newBuilder()
                        .cookieHandler(cookies)
                        .connectTimeout(java.time.Duration.ofSeconds(15))
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                        .build();
        String html =
                client.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:" + port + "/login"))
                                        .timeout(java.time.Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString())
                        .body();
        var matcher =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(html);
        assertTrue(matcher.find(), "Login CSRF token is rendered");
        String body =
                "email="
                        + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                        + "&password="
                        + java.net.URLEncoder.encode(
                                password, java.nio.charset.StandardCharsets.UTF_8)
                        + "&_csrf="
                        + matcher.group(1);
        var response =
                client.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create("http://127.0.0.1:" + port + "/login"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .timeout(java.time.Duration.ofSeconds(30))
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode(), "Login succeeds");
        assertFalse(response.headers().firstValue("location").orElse("").contains("error"));
        return client;
    }
}
