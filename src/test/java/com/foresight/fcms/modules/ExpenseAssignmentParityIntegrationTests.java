package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.finance.expense.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.flywaydb.core.Flyway;
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

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.target=26",
            "fcms.mail.initial-delay=3600000",
            "fcms.mail.interval=3600000",
            "fcms.backup.interval=3600000"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseAssignmentParityIntegrationTests {
    static final String SCHEMA =
            "expense_assignment_" + UUID.randomUUID().toString().replace("-", "");
    static final String PASSWORD = "AssignmentVerification123!";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired ExpenseService expenses;
    @Autowired ExpenseCategoryService categories;
    @Autowired PasswordEncoder passwords;
    @Autowired AuditService audit;
    @Autowired Flyway flyway;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    long owner,
            other,
            maker,
            preparer,
            reviewer,
            limited,
            inactive,
            legacyKnown,
            legacyUnknown,
            legacyEditable,
            legacyReportKnown,
            legacyReportUnchanged,
            legacyReportUnknown,
            year2025;

    void actor(String user) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user + "@test.invalid", "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    long user(String name, int role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Synthetic',?,?,?,1,?) returning id",
                Long.class,
                name,
                name + "@test.invalid",
                passwords.encode(PASSWORD),
                role);
    }

    long legacy(long user, String year) {
        return db.queryForObject(
                "insert into"
                    + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id,request_key)"
                    + " values ('2026-11-01',?,'Legacy','Synthetic"
                    + " historical','Synthetic',25.01,?,1,?) returning id",
                Long.class,
                user,
                year,
                UUID.randomUUID());
    }

    @BeforeAll
    void migrateAndSeed() throws Exception {
        OperationalTestClaims.enable(db);
        db.update("insert into roles(role_id,role_name) values (98,'Synthetic assignment claims')");
        owner = user("assign-owner", 2);
        other = user("assign-other", 2);
        maker = user("assign-maker", 1);
        preparer = user("assign-preparer", 1);
        reviewer = user("assign-reviewer", 1);
        limited = user("assign-limited", 98);
        inactive = user("assign-inactive", 2);
        db.update("update users set status_id=2 where id=?", inactive);
        year2025 =
                db.queryForObject("select id from financial_year where fyear='2025'", Long.class);
        legacyKnown = legacy(owner, "2026");
        legacyUnknown = legacy(owner, "UNMAPPED");
        legacyEditable = legacy(owner, "UNMAPPED");
        legacyReportKnown = legacyReport(2);
        legacyReportUnchanged = legacyReport(0);
        legacyReportUnknown = legacyReport(3);
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(tx -> {
                    audit.record(
                            maker,
                            "expense.save",
                            "expenses",
                            legacyKnown,
                            "Synthetic original preparation");
                    audit.record(
                            preparer,
                            "expense.save",
                            "expenses",
                            legacyKnown,
                            "Synthetic last preparation");
                    audit.record(
                            preparer,
                            "staff-report.save",
                            "work_items",
                            legacyReportKnown,
                            "Synthetic last report preparation");
                });
        assertEquals("26", flyway.info().current().getVersion().getVersion());
        Flyway.configure()
                .configuration(flyway.getConfiguration())
                .target("latest")
                .load()
                .migrate();
        OperationalTestClaims.enable(db);
        for (String claim :
                List.of("workspace.admin", "expense.read", "expense.write", "expense.assign", "dashboard.read"))
            db.update("insert into user_claims(user_id,claim) values (?,?)", limited, claim);
        for (String claim : List.of("workspace.admin", "expense.approve", "finance.reverse"))
            db.update("insert into user_claims(user_id,claim) values (?,?)", owner, claim);
        Files.createDirectories(Path.of(".verification"));
        Files.writeString(Path.of(".verification/expense-assignment-schema.txt"), SCHEMA);
    }

    long legacyReport(long version) {
        return db.queryForObject(
                "insert into"
                    + " work_items(kind,owner_id,subject,description,start_at,end_at,status,created_by,request_key,version)"
                    + " values ('REPORT',?,'Synthetic retained report','Synthetic',timestamp"
                    + " '2026-11-01 09:00',timestamp '2026-11-01 17:00','DRAFT',?,?,?) returning"
                    + " id",
                Long.class,
                owner,
                maker,
                UUID.randomUUID(),
                version);
    }

    ExpenseForm form(long user) {
        var f = new ExpenseForm();
        f.setOwnerId(user);
        f.setDate(LocalDate.of(2026, 11, 1));
        f.setCategory("Assignment " + UUID.randomUUID());
        f.setDescription("Synthetic on-behalf expense");
        f.setPayee("Synthetic payee");
        f.setAmount(n("123.45"));
        return f;
    }

    long create(String actor, long user) {
        actor(actor);
        return expenses.save(null, form(user));
    }

    void action(String actor, long id, String action) {
        actor(actor);
        var record = expenses.find(id);
        expenses.transition(
                id,
                record.version(),
                action,
                "Synthetic verification",
                "SYN-PAYMENT",
                UUID.randomUUID());
    }

    @Test
    void migrationRecoversOnlyAttestedCreatorsAndPreparersWithoutFabricatingUnknownHistory() {
        actor("assign-maker");
        var known = expenses.find(legacyKnown);
        assertEquals(maker, known.createdBy());
        assertEquals(preparer, known.preparedBy());
        assertTrue(known.creator().contains("assign-maker"));
        assertTrue(known.preparer().contains("assign-preparer"));
        var unknown = expenses.find(legacyUnknown);
        assertNull(unknown.createdBy());
        assertNull(unknown.preparedBy());
        assertEquals("", unknown.creator());
        assertEquals("UNMAPPED", unknown.year());
        assertNull(
                db.queryForObject(
                        "select creation_hash from expenses where id=?",
                        String.class,
                        legacyKnown));
        assertEquals(
                preparer,
                db.queryForObject(
                        "select prepared_by from work_items where id=?",
                        Long.class,
                        legacyReportKnown));
        assertEquals(
                maker,
                db.queryForObject(
                        "select prepared_by from work_items where id=?",
                        Long.class,
                        legacyReportUnchanged));
        assertNull(
                db.queryForObject(
                        "select prepared_by from work_items where id=?",
                        Long.class,
                        legacyReportUnknown));
    }

    @Test
    void selectedOwnerYearAndExactAmountsReachCanonicalStorageAuditAndScopedFilters() {
        actor("assign-maker");
        var f = form(owner);
        f.setFinancialYearId(year2025);
        long id = expenses.save(null, f);
        var record = expenses.find(id);
        assertEquals(owner, record.ownerId());
        assertEquals(maker, record.createdBy());
        assertEquals(maker, record.preparedBy());
        assertEquals("2025", record.year());
        assertEquals(LocalDate.of(2026, 11, 1), record.date());
        assertEquals(n("123.45"), record.amount());
        var q = new ExpenseQuery();
        q.setYear("2025");
        q.setSearch(f.getCategory());
        assertEquals(1, expenses.list(q).rows().size());
        assertEquals(n("123.45"), categories.summary(q).total());
        q.setYear("2026");
        assertTrue(expenses.list(q).rows().isEmpty());
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from audit_events where resource='expenses' and"
                                + " resource_id=? and action='expense.save' and actor_id=?",
                        Integer.class,
                        id,
                        maker));
        var edit = expenses.form(id);
        assertEquals(owner, edit.getOwnerId());
        assertEquals(year2025, edit.getFinancialYearId());
    }

    @Test
    void creatorOnlyClaimScopeRevocationAndForgedInactiveOrUnknownRecipientsAreEnforced() {
        long id = create("assign-limited", owner);
        actor("assign-limited");
        assertEquals(owner, expenses.find(id).ownerId());
        assertTrue(expenses.options(expenses.form(id), id).assignmentAllowed());
        long unrelated = create("assign-maker", other);
        actor("assign-limited");
        assertThrows(IllegalArgumentException.class, () -> expenses.find(unrelated));
        actor("assign-other");
        long before = db.queryForObject("select count(*) from expenses", Long.class);
        assertThrows(AccessDeniedException.class, () -> expenses.save(null, form(owner)));
        assertEquals(before, db.queryForObject("select count(*) from expenses", Long.class));
        actor("assign-maker");
        assertThrows(IllegalArgumentException.class, () -> expenses.save(null, form(inactive)));
        assertThrows(
                IllegalArgumentException.class, () -> expenses.save(null, form(Long.MAX_VALUE)));
        db.update("delete from user_claims where user_id=? and claim='expense.assign'", limited);
        try {
            actor("assign-limited");
            assertThrows(IllegalArgumentException.class, () -> expenses.find(id));
        } finally {
            db.update(
                    "insert into user_claims(user_id,claim) values (?,'expense.assign')", limited);
        }
        actor("assign-owner");
        assertEquals(id, expenses.find(id).id());
    }

    @Test
    void unchangedRetriesAndConcurrentRequestsProduceOneResultAndChangedPayloadsConflict()
            throws Exception {
        actor("assign-maker");
        var f = form(owner);
        var pool = Executors.newFixedThreadPool(3);
        var gate = new CyclicBarrier(3);
        var tasks = new ArrayList<Future<Long>>();
        try {
            for (int i = 0; i < 3; i++)
                tasks.add(
                        pool.submit(() -> {
                            actor("assign-maker");
                            try {
                                gate.await(10, TimeUnit.SECONDS);
                                return expenses.save(null, f);
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        }));
            long id = tasks.getFirst().get(30, TimeUnit.SECONDS);
            for (var task : tasks) assertEquals(id, task.get(30, TimeUnit.SECONDS));
            assertEquals(id, expenses.save(null, f));
            f.setOwnerId(other);
            assertThrows(IllegalArgumentException.class, () -> expenses.save(null, f));
            f.setOwnerId(owner);
            f.setAmount(n("123.46"));
            assertThrows(IllegalArgumentException.class, () -> expenses.save(null, f));
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from expenses where created_by=? and request_key=?",
                            Integer.class,
                            maker,
                            f.getRequestKey()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentMakersCanUseTheSameUuidForTheSameStaffWithoutSharingResults() {
        var first = form(owner);
        actor("assign-maker");
        long a = expenses.save(null, first);
        actor("assign-preparer");
        long b = expenses.save(null, first);
        assertNotEquals(a, b);
        assertEquals(
                2,
                db.queryForObject(
                        "select count(*) from expenses where user_id=? and request_key=?",
                        Integer.class,
                        owner,
                        first.getRequestKey()));
    }

    @Test
    void creatorAndActualPreparerCannotReviewOrReverseTheirOwnOnBehalfWork() {
        long id = create("assign-maker", owner);
        actor("assign-preparer");
        var f = expenses.form(id);
        f.setAmount(n("130.01"));
        expenses.save(id, f);
        assertEquals(preparer, expenses.find(id).preparedBy());
        assertEquals(maker, expenses.find(id).createdBy());
        action("assign-maker", id, "submit");
        for (String user : List.of("assign-maker", "assign-preparer", "assign-owner")) {
            actor(user);
            var record = expenses.find(id);
            assertFalse(expenses.details(id, new RegisterQuery()).commands().review());
            for (String command : List.of("approve", "reject"))
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                expenses.transition(
                                        id,
                                        record.version(),
                                        command,
                                        "Self review",
                                        "",
                                        UUID.randomUUID()));
        }
        action("assign-reviewer", id, "approve");
        action("assign-maker", id, "pay");
        for (String user : List.of("assign-maker", "assign-preparer", "assign-owner")) {
            actor(user);
            var record = expenses.find(id);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            expenses.transition(
                                    id,
                                    record.version(),
                                    "reverse",
                                    "Self reversal",
                                    "",
                                    UUID.randomUUID()));
        }
        action("assign-reviewer", id, "reverse");
        actor("assign-reviewer");
        assertEquals("reversed", expenses.find(id).status());
        assertEquals(4, expenses.history(id, new RegisterQuery()).rows().size());
        assertEquals(n("130.01"), expenses.find(id).amount());
    }

    @Test
    void databasePreservesOriginAndDraftPreparationAndRejectsMakerApprovalBypass() {
        long id = create("assign-maker", owner);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("update expenses set created_by=? where id=?", reviewer, id));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("update expenses set creation_hash=repeat('0',64) where id=?", id));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("update expenses set prepared_by=? where id=?", preparer, id));
        action("assign-maker", id, "submit");
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update expenses set status_id=(select id from expense_status where"
                                        + " name='approved'),approved_by=?,version=version+1 where"
                                        + " id=?",
                                maker,
                                id));
        actor("assign-maker");
        assertEquals("submitted", expenses.find(id).status());
    }

    @Test
    void longAcceptedCategoryCompletesHistoryAndUnknownRetainedYearSurvivesEdits() {
        actor("assign-maker");
        var f = form(owner);
        f.setCategory("K".repeat(255));
        long id = expenses.save(null, f);
        action("assign-maker", id, "submit");
        action("assign-reviewer", id, "approve");
        action("assign-reviewer", id, "pay");
        assertEquals(
                255,
                db.queryForObject(
                        "select max(length(category)) from expense_events where expense_id=?",
                        Integer.class,
                        id));
        actor("assign-owner");
        var edit = expenses.form(legacyEditable);
        assertNull(edit.getFinancialYearId());
        assertEquals("UNMAPPED", expenses.options(edit, legacyEditable).retainedYear());
        edit.setDescription("Synthetic historical edit");
        expenses.save(legacyEditable, edit);
        assertEquals("UNMAPPED", expenses.find(legacyEditable).year());
        assertNull(expenses.find(legacyEditable).createdBy());
        assertEquals(owner, expenses.find(legacyEditable).preparedBy());
    }

    String field(String html, String name) {
        var m = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find(), name);
        return m.group(1);
    }

    HttpResponse<String> get(HttpClient web, String path) throws Exception {
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(45))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(HttpClient web, String path, Map<String, String> values)
            throws Exception {
        String body =
                values.entrySet().stream()
                        .map(e ->
                                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                        + "="
                                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpClient browser(String name) throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        var page = get(web, "/login");
        assertEquals(
                302,
                post(
                                web,
                                "/login",
                                Map.of(
                                        "email",
                                        name + "@test.invalid",
                                        "password",
                                        PASSWORD,
                                        "_csrf",
                                        field(page.body(), "_csrf")))
                        .statusCode());
        return web;
    }

    @Test
    void retiredExpensePagesAndCommandsAreGoneAndCannotCreateLegacyRecords() throws Exception {
        var web=browser("assign-maker");
        var page=get(web,"/finance/expenses/new?lang=sw");
        assertEquals(410,page.statusCode());
        assertEquals(410,get(web,"/finance/expenses").statusCode());
        var values=new HashMap<String,String>();values.put("description","Retired entry");
        assertEquals(403,post(web,"/finance/expenses",values).statusCode());
        values.put("_csrf",field(get(web,"/account").body(),"_csrf"));
        long before=db.queryForObject("select count(*) from expenses",Long.class);
        assertEquals(410,post(web,"/finance/expenses",values).statusCode());
        assertEquals(before,db.queryForObject("select count(*) from expenses",Long.class));
        assertEquals(410,get(browser("assign-other"),"/finance/expenses/new").statusCode());
    }
}
