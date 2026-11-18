package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.service.UserDirectoryService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.dto.WorkForm;
import com.foresight.fcms.workforce.dto.WorkQuery;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

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
            "fcms.mail.initial-delay=3600000",
            "fcms.mail.interval=3600000",
            "fcms.backup.interval=3600000"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffReportAssignmentParityIntegrationTests {
    static final String SCHEMA =
            "report_assignment_" + UUID.randomUUID().toString().replace("-", "");
    static final String PASSWORD = "ReportVerification123!";
    static final WorkKind REPORT = WorkKind.REPORT;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired WorkService work;
    @Autowired UserDirectoryService users;
    @Autowired PasswordEncoder passwords;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    long owner, member, maker, preparer, reviewer, limited, inactive;

    void actor(String name) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                name + "@test.invalid", "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
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

    @BeforeAll
    void seed() throws Exception {
        OperationalTestClaims.enable(db);
        db.update("insert into roles(role_id,role_name) values (98,'Synthetic report claims')");
        owner = user("report-owner", 2);
        member = user("report-member", 2);
        db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin'),(?,'workspace.admin')", owner, member);
        maker = user("report-maker", 1);
        preparer = user("report-preparer", 1);
        reviewer = user("report-reviewer", 1);
        limited = user("report-limited", 98);
        inactive = user("report-inactive", 2);
        db.update("update users set status_id=2 where id=?", inactive);
        for (String claim :
                List.of(
                        "staff-report.read",
                        "staff-report.write",
                        "staff-report.assign",
                        "workspace.admin",
                        "dashboard.read"))
            db.update("insert into user_claims(user_id,claim) values (?,?)", limited, claim);
        for (long id : List.of(owner, member))
            db.update(
                    "insert into user_claims(user_id,claim) values (?,'staff-report.review')", id);
        Files.createDirectories(Path.of(".verification"));
        Files.writeString(Path.of(".verification/report-assignment-schema.txt"), SCHEMA);
    }

    WorkForm form(long owner) {
        var f = new WorkForm();
        f.setOwnerId(owner);
        f.setSubject("Report " + UUID.randomUUID());
        f.setDescription("Synthetic selected-staff activity");
        f.setStartAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        f.setEndAt(LocalDateTime.of(2026, 11, 1, 17, 0));
        f.setWorkType("INDIVIDUAL");
        f.setOutcome("COMPLETED");
        return f;
    }

    long create(String actor, long owner) {
        actor(actor);
        return work.save(REPORT, null, form(owner));
    }

    void action(String actor, long id, String action) {
        actor(actor);
        work.transition(REPORT, id, work.find(REPORT, id).version(), action, "Synthetic review");
    }

    @Test
    void selectedOwnerAndActualMakerReachScopedStorageAndAssignmentRevocationTakesEffect() {
        long id = create("report-limited", owner);
        actor("report-limited");
        var r = work.find(REPORT, id);
        assertEquals(owner, r.ownerId());
        assertEquals(limited, r.createdBy());
        assertEquals(limited, r.preparedBy());
        assertTrue(work.details(REPORT, id).commands().prepare());
        long unrelated = create("report-maker", member);
        actor("report-limited");
        assertThrows(IllegalArgumentException.class, () -> work.find(REPORT, unrelated));
        db.update(
                "delete from user_claims where user_id=? and claim='staff-report.assign'", limited);
        try {
            assertThrows(IllegalArgumentException.class, () -> work.find(REPORT, id));
        } finally {
            db.update(
                    "insert into user_claims(user_id,claim) values (?,'staff-report.assign')",
                    limited);
        }
        actor("report-owner");
        assertThrows(AccessDeniedException.class, () -> work.save(REPORT, null, form(member)));
        actor("report-maker");
        assertThrows(IllegalArgumentException.class, () -> work.save(REPORT, null, form(inactive)));
        assertThrows(
                IllegalArgumentException.class,
                () -> work.save(REPORT, null, form(Long.MAX_VALUE)));
    }

    @Test
    void groupUsesSelectedOwnerAndParticipantsWithoutSilentlyAddingItsMaker() {
        actor("report-maker");
        var f = form(owner);
        f.setWorkType("GROUP");
        f.setParticipants(List.of(member, member));
        long id = work.save(REPORT, null, f);
        assertEquals(
                Set.of(owner, member),
                new HashSet<>(
                        db.queryForList(
                                "select user_id from work_participants where work_id=?",
                                Long.class,
                                id)));
        actor("report-member");
        assertEquals(owner, work.find(REPORT, id).ownerId());
        assertFalse(work.details(REPORT, id).commands().prepare());
        actor("report-maker");
        var edit = work.form(REPORT, id);
        edit.setDescription("Synthetic group edit");
        work.save(REPORT, id, edit);
        assertEquals(maker, work.find(REPORT, id).createdBy());
    }

    @Test
    void independentReviewExcludesOwnerMakerPreparerAndGroupParticipantIncludingDatabaseBypass() {
        actor("report-maker");
        var f = form(owner);
        f.setWorkType("GROUP");
        f.setParticipants(List.of(member));
        long id = work.save(REPORT, null, f);
        actor("report-preparer");
        var edit = work.form(REPORT, id);
        edit.setExplanation("Synthetic preparation");
        work.save(REPORT, id, edit);
        assertEquals(preparer, work.find(REPORT, id).preparedBy());
        action("report-maker", id, "submit");
        for (String name :
                List.of("report-owner", "report-member", "report-maker", "report-preparer")) {
            actor(name);
            assertFalse(work.details(REPORT, id).commands().review());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            work.transition(
                                    REPORT,
                                    id,
                                    work.find(REPORT, id).version(),
                                    "approve",
                                    "Self review"));
        }
        for (long actor : List.of(owner, member, maker, preparer))
            assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () ->
                            db.update(
                                    "update work_items set"
                                        + " status='APPROVED',reviewed_by=?,version=version+1 where"
                                        + " id=?",
                                    actor,
                                    id));
        action("report-reviewer", id, "reject");
        actor("report-preparer");
        var returned = work.form(REPORT, id);
        returned.setDescription("Synthetic corrected group");
        work.save(REPORT, id, returned);
        assertEquals("REJECTED", work.find(REPORT, id).status());
        action("report-maker", id, "submit");
        action("report-reviewer", id, "approve");
        actor("report-reviewer");
        assertEquals("APPROVED", work.find(REPORT, id).status());
        assertFalse(work.details(REPORT, id).commands().prepare());
    }

    @Test
    void originIsImmutableAndSubmittedPreparationCannotBeRewritten() {
        long id = create("report-maker", owner);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("update work_items set created_by=? where id=?", reviewer, id));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update work_items set creation_hash=repeat('0',64) where id=?",
                                id));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("update work_items set prepared_by=? where id=?", preparer, id));
        action("report-maker", id, "submit");
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update work_items set prepared_by=?,version=version+1 where id=?",
                                preparer,
                                id));
    }

    @Test
    void concurrentRetryHasOneResultAndChangedPayloadCannotReuseItsIdentity() throws Exception {
        var f = form(owner);
        f.setWorkType("GROUP");
        f.setParticipants(List.of(member));
        try (var pool = Executors.newFixedThreadPool(3)) {
            var gate = new CountDownLatch(1);
            var futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < 3; i++)
                futures.add(
                        pool.submit(() -> {
                            actor("report-maker");
                            gate.await();
                            try {
                                return work.save(REPORT, null, f);
                            } finally {
                                clear();
                            }
                        }));
            gate.countDown();
            var ids = new HashSet<Long>();
            for (var future : futures) ids.add(future.get(45, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            actor("report-maker");
            f.setParticipants(List.of(owner, member, member));
            assertEquals(ids.iterator().next(), work.save(REPORT, null, f));
            f.setOwnerId(member);
            assertEquals(
                    "work.requestConflict",
                    assertThrows(IllegalArgumentException.class, () -> work.save(REPORT, null, f))
                            .getMessage());
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from work_items where created_by=? and request_key=?",
                            Integer.class,
                            maker,
                            f.getRequestKey()));
        }
    }

    @Test
    void participantFilterAndActivityExportPreserveCanonicalOwnerAndEmitEachActualMember() {
        actor("report-maker");
        var f = form(owner);
        f.setWorkType("GROUP");
        f.setParticipants(List.of(member));
        long id = work.save(REPORT, null, f);
        var q = new RegisterQuery();
        q.setSearch(f.getSubject());
        q.setOwnerId(member);
        assertEquals(id, work.list(REPORT, q).rows().getFirst().id());
        var memberRows = work.report(REPORT, q);
        assertEquals(1, memberRows.size());
        assertEquals("Synthetic report-member", memberRows.getFirst().activityStaff());
        assertEquals(owner, memberRows.getFirst().ownerId());
        assertEquals(maker, memberRows.getFirst().createdBy());
        q.setOwnerId(null);
        var all = work.report(REPORT, q);
        assertEquals(2, all.size());
        assertEquals(
                Set.of("Synthetic report-owner", "Synthetic report-member"),
                all.stream()
                        .map(r -> r.activityStaff())
                        .collect(java.util.stream.Collectors.toSet()));
        actor("report-member");
        assertEquals(2, work.report(REPORT, q).size());
        actor("report-limited");
        assertTrue(work.report(REPORT, q).isEmpty());
    }

    @Test
    void selectedStaffBeyondFirstPageSurvivesReadonlyRegisterFilterRecovery() throws Exception {
        var hash = passwords.encode(PASSWORD);
        db.batchUpdate(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Ahead',?,?,?,1,2)",
                java.util.stream.IntStream.range(0, 55).boxed().toList(),
                55,
                (s, i) -> {
                    s.setString(1, "Staff" + i);
                    s.setString(2, "ahead" + i + "@test.invalid");
                    s.setString(3, hash);
                });
        long readOnly = user("report-readonly", 98);
        for (String claim : List.of("workspace.admin", "staff-report.read", "staff-report.read.all"))
            db.update("insert into user_claims(user_id,claim) values (?,?)", readOnly, claim);
        actor("report-readonly");
        assertFalse(users.staff("").stream().anyMatch(c -> c.id() == owner));
        @SuppressWarnings("unchecked")
        var choices =
                (List<UserDirectoryService.Choice>)
                        work.filters(REPORT, queryFor(owner)).get("filterStaff");
        assertTrue(choices.stream().anyMatch(c -> c.id() == owner));
        assertTrue(choices.size() <= 51);
        var web = browser("report-readonly");
        var page = get(web, "/staff-reports?ownerId=" + owner);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Synthetic report-owner"));
    }

    RegisterQuery queryFor(long owner) {
        var q = new RegisterQuery();
        q.setOwnerId(owner);
        return q;
    }

    @Test
    void reportWithdrawalRetainsGroupOriginsReasonAndAuditWithoutDeletingActivity() {
        actor("report-maker");
        var f = form(owner);
        f.setWorkType("GROUP");
        f.setParticipants(List.of(member));
        long id = work.save(REPORT, null, f);
        action("report-maker", id, "submit");
        actor("report-member");
        assertFalse(work.details(REPORT, id).commands().cancel());
        assertThrows(
                IllegalArgumentException.class,
                () -> work.transition(REPORT, id, 1, "cancel", "Unauthorized member"));
        actor("report-maker");
        assertTrue(work.details(REPORT, id).commands().cancel());
        assertThrows(
                IllegalArgumentException.class, () -> work.transition(REPORT, id, 1, "cancel", ""));
        work.transition(REPORT, id, 1, "cancel", "Synthetic duplicate activity");
        var r = work.find(REPORT, id);
        assertEquals("CANCELLED", r.status());
        assertEquals(maker, r.createdBy());
        assertEquals(owner, r.ownerId());
        assertEquals(2, work.participants(REPORT, id).size());
        assertFalse(work.details(REPORT, id).commands().prepare());
        assertThrows(IllegalArgumentException.class, () -> work.form(REPORT, id));
        assertThrows(
                IllegalArgumentException.class,
                () -> work.transition(REPORT, id, 1, "cancel", "Duplicate retry"));
        String retained =
                db.queryForObject(
                        "select detail from audit_events where resource='work_items' and"
                                + " resource_id=? and action='staff-report.cancel'",
                        String.class,
                        id);
        assertTrue(retained.contains("before=SUBMITTED; after=CANCELLED"));
        assertTrue(retained.contains("Synthetic duplicate activity"));
        var q = new RegisterQuery();
        q.setStatus("CANCELLED");
        q.setSearch(f.getSubject());
        assertEquals(2, work.report(REPORT, q).size());
    }

    @Test
    void approvedReportsCannotBeWithdrawnAndLeaveCommandsStayUnchanged() {
        long id = create("report-maker", owner);
        action("report-maker", id, "submit");
        action("report-reviewer", id, "approve");
        actor("report-maker");
        assertFalse(work.details(REPORT, id).commands().cancel());
        assertThrows(
                IllegalArgumentException.class,
                () -> work.transition(REPORT, id, 2, "cancel", "Reviewed history"));
        var f = form(maker);
        long leave = work.save(WorkKind.LEAVE, null, f);
        assertFalse(work.details(WorkKind.LEAVE, leave).commands().cancel());
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    actor("report-owner");
                    work.transition(WorkKind.LEAVE, leave, 0, "cancel", "Unrelated leave");
                });
    }

    @Test
    void maximumLengthWithdrawalAndPriorReviewAreRetainedWithoutTruncatingAuditEvidence() {
        long id = create("report-maker", owner);
        action("report-maker", id, "submit");
        String prior = "R".repeat(1000), reason = "W".repeat(1000);
        actor("report-reviewer");
        work.transition(REPORT, id, 1, "reject", prior);
        actor("report-maker");
        work.transition(REPORT, id, 2, "cancel", reason);
        var record = work.find(REPORT, id);
        assertEquals("CANCELLED", record.status());
        assertEquals(reason, record.reviewComment());
        String evidence =
                db.queryForObject(
                        "select detail from audit_events where resource='work_items' and"
                                + " resource_id=? and action='staff-report.cancel'",
                        String.class,
                        id);
        assertTrue(evidence.contains("reason=" + reason));
        assertTrue(evidence.endsWith("priorReview=" + prior));
        assertTrue(evidence.length() > 2000);
        assertThrows(
                IllegalArgumentException.class,
                () -> work.transition(REPORT, id, 3, "cancel", "W".repeat(1001)));
    }

    @Test
    void taskCreatedDateBasisUsesApplicationDayBoundsAndPreservesStartDateAndScope() {
        actor("report-maker");
        long client =
                db.queryForObject(
                        "insert into saccos(saccos_name,region,financial_year_id,client_status_id)"
                            + " values ('Synthetic task client','Synthetic',(select max(id) from"
                            + " financial_year),1) returning id",
                        Long.class);
        var f = form(owner);
        f.setClientId(client);
        f.setWorkType("Synthetic support");
        f.setStartAt(LocalDateTime.of(2025, 1, 1, 9, 0));
        f.setEndAt(LocalDateTime.of(2025, 1, 1, 17, 0));
        long id = work.save(WorkKind.TASK, null, f);
        var q = new WorkQuery();
        q.setSearch(f.getSubject());
        q.setFrom(LocalDate.of(2026, 11, 1));
        q.setTo(LocalDate.of(2026, 11, 1));
        db.update("update work_items set created_at='2026-10-31T21:00:00Z' where id=?", id);
        assertTrue(work.list(WorkKind.TASK, q).rows().isEmpty());
        q.setDateBasis("CREATED");
        assertEquals(id, work.list(WorkKind.TASK, q).rows().getFirst().id());
        db.update("update work_items set created_at='2026-11-01T20:59:59.999999Z' where id=?", id);
        assertEquals(id, work.report(WorkKind.TASK, q).getFirst().id());
        db.update("update work_items set created_at='2026-11-01T21:00:00Z' where id=?", id);
        assertTrue(work.report(WorkKind.TASK, q).isEmpty());
        db.update("update work_items set created_at='2026-10-31T20:59:59.999999Z' where id=?", id);
        assertTrue(work.report(WorkKind.TASK, q).isEmpty());
        db.update("update work_items set created_at='2026-11-01T00:00:00Z' where id=?", id);
        actor("report-member");
        assertTrue(work.report(WorkKind.TASK, q).isEmpty());
        actor("report-owner");
        assertEquals(id, work.report(WorkKind.TASK, q).getFirst().id());
        assertThrows(
                IllegalArgumentException.class,
                () -> q.setDateBasis("created_at; drop table users"));
        assertThrows(IllegalArgumentException.class, () -> work.list(REPORT, q));
    }

    @Test
    void expandedGroupExportRejectsMoreThanOneThousandRowsAndRestoresCallerPaging() {
        actor("report-maker");
        String subject = "Synthetic expansion " + UUID.randomUUID();
        db.update(
                "insert into"
                    + " work_items(kind,owner_id,subject,description,start_at,end_at,work_type,status,created_by,request_key)"
                    + " select 'REPORT',?,?,'Synthetic',timestamp '2026-11-01 09:00',timestamp"
                    + " '2026-11-01 17:00','GROUP','DRAFT',?,md5('expanded-'||g)::uuid from"
                    + " generate_series(1,501) g",
                owner,
                subject,
                maker);
        db.update(
                "insert into work_participants(work_id,user_id) select w.id,u.id from work_items w"
                        + " cross join (values (?::bigint),(?::bigint)) u(id) where w.subject=?",
                owner,
                member,
                subject);
        var q = new RegisterQuery();
        q.setSearch(subject);
        q.setPage(2);
        q.setSize(10);
        assertEquals(
                "error.narrowReport",
                assertThrows(IllegalArgumentException.class, () -> work.report(REPORT, q))
                        .getMessage());
        assertEquals(2, q.getPage());
        assertEquals(10, q.getSize());
        q.setOwnerId(member);
        assertEquals(501, work.report(REPORT, q).size());
    }

    String field(String html, String name) {
        var m = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find(), name);
        return m.group(1);
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
    void customReportAssignmentFormsDoNotRequireAnUnrelatedClientDirectoryClaim() throws Exception {
        var web = browser("report-limited");
        var page = get(web, "/staff-reports/new");
        assertEquals(200, page.statusCode());
        assertFalse(page.body().contains("data-choice-url=\"/lookups/clients\""));
        var fields = new HashMap<String, String>();
        fields.put("_csrf", field(page.body(), "_csrf"));
        fields.put("requestKey", field(page.body(), "requestKey"));
        fields.put("ownerId", Long.toString(owner));
        fields.put("subject", "Synthetic minimal-claim assignment");
        fields.put("description", "Synthetic activity without a client association");
        fields.put("startAt", "2026-11-01T09:00");
        fields.put("endAt", "2026-11-01T17:00");
        fields.put("workType", "INDIVIDUAL");
        fields.put("outcome", "COMPLETED");
        fields.put("allowance", "0");
        fields.put("amount", "0");
        var saved = post(web, "/staff-reports", fields);
        assertEquals(302, saved.statusCode());
        var detail =
                get(
                        web,
                        URI.create(saved.headers().firstValue("location").orElseThrow()).getPath());
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("Synthetic report-owner"));
        fields.put("requestKey", UUID.randomUUID().toString());
        fields.put("clientId", "1");
        assertEquals(403, post(web, "/staff-reports", fields).statusCode());
    }

    HttpResponse<String> get(HttpClient web, String path) throws Exception {
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
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
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void nativeFormsProtectCsrfRetainInvalidValuesAndIgnoreForgedMaker() throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        var login = get(web, "/login");
        assertEquals(
                302,
                post(
                                web,
                                "/login",
                                Map.of(
                                        "email",
                                        "report-maker@test.invalid",
                                        "password",
                                        PASSWORD,
                                        "_csrf",
                                        field(login.body(), "_csrf")))
                        .statusCode());
        var page = get(web, "/staff-reports/new?lang=sw");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("name=\"ownerId\""));
        var fields = new HashMap<String, String>();
        fields.put("ownerId", Long.toString(owner));
        fields.put("subject", "Synthetic native report");
        fields.put("description", "Synthetic on-behalf native activity");
        fields.put("startAt", "2026-11-01T09:00");
        fields.put("endAt", "2026-11-01T17:00");
        fields.put("workType", "INDIVIDUAL");
        fields.put("outcome", "COMPLETED");
        fields.put("allowance", "0.00");
        fields.put("amount", "0.00");
        fields.put("version", "0");
        fields.put("requestKey", field(page.body(), "requestKey"));
        assertEquals(403, post(web, "/staff-reports", fields).statusCode());
        fields.put("_csrf", field(page.body(), "_csrf"));
        fields.put("createdBy", Long.toString(reviewer));
        fields.put("preparedBy", Long.toString(reviewer));
        fields.put("endAt", "2026-10-01T17:00");
        var invalid = post(web, "/staff-reports", fields);
        assertEquals(200, invalid.statusCode());
        assertTrue(invalid.body().contains("Synthetic native report"));
        assertTrue(invalid.body().contains(fields.get("requestKey")));
        fields.put("endAt", "2026-11-01T17:00");
        var saved = post(web, "/staff-reports", fields);
        assertEquals(302, saved.statusCode());
        String path = URI.create(saved.headers().firstValue("location").orElseThrow()).getPath();
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        actor("report-maker");
        var r = work.find(REPORT, id);
        assertEquals(owner, r.ownerId());
        assertEquals(maker, r.createdBy());
        assertEquals(maker, r.preparedBy());
        var detail = get(web, path + "?saved");
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("Synthetic report-maker"));
        assertTrue(detail.body().contains("/actions/submit"));
    }
}
