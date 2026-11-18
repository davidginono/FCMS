package com.foresight.fcms.modules;

import static com.foresight.fcms.shared.time.ApplicationCalendar.today;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.dto.UserForm;
import com.foresight.fcms.accounting.dto.CoaQuery;
import com.foresight.fcms.auth.service.*;
import com.foresight.fcms.finance.expense.dto.ExpenseForm;
import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.dto.WorkForm;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresModuleTests {
    static final String SCHEMA="modules_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("FCMS_TEST_DB_URL")+"?currentSchema="+SCHEMA);
        p.add("spring.flyway.schemas",()->SCHEMA);
        p.add("spring.flyway.default-schema",()->SCHEMA);
        p.add("fcms.storage.avatar-dir", () -> ".verification/avatars");
        p.add("fcms.storage.contract-dir", () -> ".verification/contracts-tested");
        p.add("fcms.backup.interval", () -> "3600000");
        p.add("fcms.mail.interval", () -> "3600000");
    }

    @Autowired JdbcTemplate db;
    @Autowired AccessService access;
    @Autowired UserAdministrationService users;
    @Autowired WorkService work;
    @Autowired com.foresight.fcms.client.service.ClientArchiveService clientArchive;
    @Autowired LedgerService ledger;
    @Autowired ExpenseService expenses;
    @Autowired PasswordEncoder passwords;
    @Autowired PasswordRecoveryService recovery;
    long staffId, otherId, reviewerId, clientId;
    String staff, other, reviewer;
    final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void fixtures() {
        OperationalTestClaims.enable(db);
        staff = "staff-" + suffix + "@test.invalid";
        other = "other-" + suffix + "@test.invalid";
        reviewer = "review-" + suffix + "@test.invalid";
        staffId = user(staff, 2);
        otherId = user(other, 2);
        reviewerId = user(reviewer, 1);
        clientId =
                db.queryForObject(
                        "insert into saccos(saccos_name,region,financial_year_id,client_status_id)"
                                + " values (?,'Test',1,2) returning id",
                        Long.class,
                        "Verification " + suffix);
    }

    long user(String email, long role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Test','User',?,?,1,?) returning id",
                Long.class,
                email,
                passwords.encode("Verification123!"),
                role);
    }

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void effectiveClaimsAreDatabaseBackedAndRevocable() {
        actor(staff);
        assertTrue(access.current().has("task.write"));
        assertThrows(AccessDeniedException.class, () -> access.require("iam.user.manage"));
        db.update("insert into user_claims(user_id,claim) values (?,'client.write')", staffId);
        assertFalse(access.current().has("client.write"));
        db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin')", staffId);
        assertTrue(access.current().has("client.write"));
        db.update("delete from user_claims where user_id=? and claim='client.write'", staffId);
        assertFalse(access.current().has("client.write"));
        db.update("delete from user_claims where user_id=? and claim='workspace.admin'", staffId);
    }

    @Test
    void disabledAccountsLoseAccessImmediately() {
        actor(other);
        db.update("update users set status_id=2 where id=?", otherId);
        try {
            assertThrows(AccessDeniedException.class, () -> access.current());
        } finally {
            db.update("update users set status_id=1 where id=?", otherId);
        }
    }

    @Test
    void leaveIsOwnerScopedAndRequiresIndependentReview() {
        actor(staff);
        var f = new WorkForm();
        f.setSubject("Leave " + suffix);
        f.setDescription("Annual leave");
        long id = work.save(WorkKind.LEAVE, null, f);
        actor(other);
        assertThrows(IllegalArgumentException.class, () -> work.find(WorkKind.LEAVE, id));
        actor(staff);
        work.transition(WorkKind.LEAVE, id, 0, "submit", "");
        actor(reviewer);
        work.transition(WorkKind.LEAVE, id, 1, "approve", "Approved");
        assertEquals("APPROVED", work.find(WorkKind.LEAVE, id).status());
        assertThrows(
                IllegalArgumentException.class,
                () -> work.transition(WorkKind.LEAVE, id, 2, "reject", "Late"));
    }

    @Test
    void taskAssignmentAndCompletionWorkAcrossActors() {
        actor(reviewer);
        var f = new WorkForm();
        f.setOwnerId(staffId);
        f.setClientId(clientId);
        f.setSubject("Task " + suffix);
        f.setDescription("Implement");
        long id = work.save(WorkKind.TASK, null, f);
        actor(staff);
        assertEquals(staffId, work.find(WorkKind.TASK, id).ownerId());
        work.transition(WorkKind.TASK, id, 0, "start", "");
        work.transition(WorkKind.TASK, id, 1, "complete", "");
        assertEquals("COMPLETED", work.find(WorkKind.TASK, id).status());
    }

    @Test
    void customTaskManagersCanReadOnlyTheirOwnAssignmentsAndLoseScopeWhenRevoked()
            throws Exception {
        String manager = "task-manager-" + suffix + "@test.invalid";
        String stranger = "task-stranger-" + suffix + "@test.invalid";
        long managerId = user(manager, 2), strangerId = user(stranger, 2);
        db.update(
                "insert into user_claims(user_id,claim) values (?,'task.manage'),(?,'task.manage')",
                managerId,
                strangerId);
        db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin'),(?,'workspace.admin')",
                managerId, strangerId);
        actor(manager);
        assertFalse(access.current().has("task.read.all"));
        long before = work.count(WorkKind.TASK);
        var form = new WorkForm();
        form.setOwnerId(staffId);
        form.setClientId(clientId);
        form.setSubject("Managed task " + suffix);
        form.setDescription("Assigned by a custom claims manager");
        long assigned = work.save(WorkKind.TASK, null, form);
        assertEquals(staffId, work.find(WorkKind.TASK, assigned).ownerId());
        assertEquals(before + 1, work.count(WorkKind.TASK));
        var query = new RegisterQuery();
        query.setSearch(form.getSubject());
        assertEquals(
                List.of(assigned),
                work.list(WorkKind.TASK, query).rows().stream().map(r -> r.id()).toList());
        var edited = work.form(WorkKind.TASK, assigned);
        edited.setDescription("Updated assignment");
        work.save(WorkKind.TASK, assigned, edited);
        var web = browser(manager, "Verification123!");
        var detail =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:" + port + "/tasks/" + assigned))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("Updated assignment"));
        actor(stranger);
        assertThrows(IllegalArgumentException.class, () -> work.find(WorkKind.TASK, assigned));
        assertTrue(work.list(WorkKind.TASK, query).rows().isEmpty());
        assertEquals(0, work.count(WorkKind.TASK));
        actor(staff);
        work.transition(WorkKind.TASK, assigned, 1, "start", "");
        actor(manager);
        work.transition(WorkKind.TASK, assigned, 2, "cancel", "Cancelled by assigning manager");
        assertEquals("CANCELLED", work.find(WorkKind.TASK, assigned).status());
        db.update("delete from user_claims where user_id=? and claim='task.manage'", managerId);
        assertThrows(IllegalArgumentException.class, () -> work.find(WorkKind.TASK, assigned));
        assertTrue(work.list(WorkKind.TASK, query).rows().isEmpty());
        assertEquals(before, work.count(WorkKind.TASK));
        actor(staff);
        assertEquals("CANCELLED", work.find(WorkKind.TASK, assigned).status());
    }

    @Test
    void clientArchivesRetainValuesRestoreAllRegistersAndEnforceVersionsClaimsAndCsrf()
            throws Exception {
        actor(reviewer);
        var web = browser(reviewer, "Verification123!");
        for (String slug : List.of("saccos", "amcos", "unions", "microfinance", "others")) {
            var register = clientArchive.register(slug);
            long id =
                    db.queryForObject(
                            "insert into "
                                    + register.table()
                                    + "("
                                    + register.nameColumn()
                                    + ",region,financial_year_id,client_status_id,annual_fee,amount)"
                                    + " values (?,'Test',1,2,100,12.34) returning id",
                            Long.class,
                            "Archive " + slug + " " + suffix);
            var original = clientArchive.find(slug, id);
            assertTrue(original.visible());
            clientArchive.visibility(
                    slug, id, original.version(), false, "Duplicate registry entry");
            clientArchive.visibility(
                    slug, id, original.version(), false, "Duplicate registry entry");
            var archived = clientArchive.find(slug, id);
            assertFalse(archived.visible());
            assertEquals(original.version() + 1, archived.version());
            assertEquals(0, original.amount().compareTo(archived.amount()));
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from audit_events where resource=? and resource_id=?"
                                    + " and action='client.archive'",
                            Integer.class,
                            register.table(),
                            id));
            assertThrows(
                    org.springframework.dao.OptimisticLockingFailureException.class,
                    () ->
                            clientArchive.visibility(
                                    slug, id, original.version(), true, "Stale restore"));
            var query = new RegisterQuery();
            query.setSearch("Archive " + slug + " " + suffix);
            assertEquals(
                    List.of(id),
                    clientArchive.list(slug, query).rows().stream().map(r -> r.id()).toList());
            for (String language : List.of("en", "sw")) {
                var page =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/clients/"
                                                                + slug
                                                                + "/archive?lang="
                                                                + language))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, page.statusCode());
                assertTrue(page.body().contains("</html>"));
                assertTrue(
                        page.body()
                                .contains(
                                        language.equals("sw")
                                                ? "Wateja waliohifadhiwa"
                                                : "Archived clients"));
            }
            String detailUrl =
                    "http://127.0.0.1:" + port + "/clients/" + slug + "/archive/" + id + "?lang=sw";
            var detail =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(detailUrl))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detail.statusCode());
            assertTrue(detail.body().contains("Rejesha mteja"));
            var csrf =
                    java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                            .matcher(detail.body());
            assertTrue(csrf.find());
            if (slug.equals("saccos")) {
                String staleInput =
                        "version="
                                + original.version()
                                + "&visible=true&reason=%3Cimg+src%3Dx%3E&_csrf="
                                + java.net.URLEncoder.encode(
                                        csrf.group(1), java.nio.charset.StandardCharsets.UTF_8);
                var stale =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/clients/"
                                                                + slug
                                                                + "/"
                                                                + id
                                                                + "/visibility"))
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .POST(
                                                java.net.http.HttpRequest.BodyPublishers.ofString(
                                                        staleInput))
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, stale.statusCode());
                assertTrue(stale.body().contains("&lt;img src=x&gt;"));
                assertFalse(stale.body().contains("<img src=x>"));
                assertTrue(stale.body().contains("class=\"fcms-form\""));
                assertFalse(clientArchive.find(slug, id).visible());
            }
            String commandUrl =
                    "http://127.0.0.1:" + port + "/clients/" + slug + "/" + id + "/visibility";
            String input =
                    "version=" + archived.version() + "&visible=true&reason=Reviewed+restore";
            var noCsrf =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(commandUrl))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(403, noCsrf.statusCode());
            var restored =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(commandUrl))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    input
                                                            + "&_csrf="
                                                            + java.net.URLEncoder.encode(
                                                                    csrf.group(1),
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(302, restored.statusCode());
            assertTrue(clientArchive.find(slug, id).visible());
            assertEquals(0, original.amount().compareTo(clientArchive.find(slug, id).amount()));
            assertTrue(clientArchive.list(slug, query).rows().isEmpty());
            var active =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/clients/"
                                                            + slug
                                                            + "/"
                                                            + id
                                                            + "?lang=sw"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, active.statusCode());
            assertTrue(active.body().contains("Ada ya mwaka"));
            assertTrue(active.body().contains("Hifadhi mteja"));
            var legacy =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/clients/"
                                                            + slug
                                                            + "/"
                                                            + id
                                                            + "/delete"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "_csrf="
                                                            + java.net.URLEncoder.encode(
                                                                    csrf.group(1),
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(302, legacy.statusCode());
            assertTrue(
                    legacy.headers()
                            .firstValue("Location")
                            .orElseThrow()
                            .endsWith("/" + id + "/lifecycle"));
            assertTrue(clientArchive.find(slug, id).visible());
            actor(staff);
            assertThrows(AccessDeniedException.class, () -> clientArchive.find(slug, id));
            assertThrows(
                    AccessDeniedException.class,
                    () -> clientArchive.visibility(slug, id, 2, false, "Forbidden"));
            actor(reviewer);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> clientArchive.list("saccos;drop table users", new RegisterQuery()));
    }

    @Test
    void localeLinksKeepReportFiltersAndExcludeSubmittedCredentials() throws Exception {
        var web = browser(staff, "Verification123!");
        String base = "http://127.0.0.1:" + port;
        var form =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                base
                                                        + "/account/password?year=2025&category=Travel%26Training&dateBasis=CREATED&from=2026-01-01&to=2026-11-03&unexpected=PrivateValue"))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, form.statusCode());
        var csrf =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(form.body());
        assertTrue(csrf.find());
        String language = languageForm(form.body());
        assertTrue(language.contains("name=\"year\" value=\"2025\""));
        assertTrue(language.contains("name=\"category\" value=\"Travel&amp;Training\""));
        assertTrue(language.contains("name=\"dateBasis\" value=\"CREATED\""));
        assertTrue(language.contains("name=\"from\" value=\"2026-01-01\""));
        assertTrue(language.contains("name=\"to\" value=\"2026-11-03\""));
        assertFalse(language.contains("PrivateValue"));
        String input =
                "_csrf="
                        + java.net.URLEncoder.encode(
                                csrf.group(1), java.nio.charset.StandardCharsets.UTF_8)
                        + "&currentPassword=NeverCopyCurrent&newPassword=x&confirmPassword=NeverCopyConfirmation&requestKey=NeverCopyRequest";
        var invalid =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(base + "/account/password"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, invalid.statusCode());
        assertTrue(invalid.body().contains("</html>"));
        language = languageForm(invalid.body());
        assertFalse(language.contains("Password"));
        assertFalse(language.contains("NeverCopy"));
        assertFalse(language.contains("_csrf"));
        assertFalse(language.contains("requestKey"));
    }

    @Test
    void journalRetryDoesNotDoublePostAndChangedPayloadIsRejected() {
        actor(reviewer);
        var f = money("25.00", "Retry " + suffix);
        var before = ledger.balance(LedgerKind.INCOME).amount();
        long id = ledger.post(LedgerKind.INCOME, f);
        assertEquals(id, ledger.post(LedgerKind.INCOME, f));
        assertEquals(
                0,
                before.add(new BigDecimal("25.00"))
                        .compareTo(ledger.balance(LedgerKind.INCOME).amount()));
        f.setAmount(new BigDecimal("26.00"));
        assertThrows(IllegalArgumentException.class, () -> ledger.post(LedgerKind.INCOME, f));
    }

    @Test
    void operationalRetryRejectsDelimiterFingerprintCollisions() {
        actor(reviewer);
        var first = money("1.00", "Collision|Office");
        first.setCategory("Original");
        long id = ledger.post(LedgerKind.INCOME, first);
        assertEquals(id, ledger.post(LedgerKind.INCOME, first));
        first.setDescription("Collision");
        first.setCategory("Office|Original");
        assertThrows(IllegalArgumentException.class, () -> ledger.post(LedgerKind.INCOME, first));
        assertEquals("Collision|Office", ledger.find(LedgerKind.INCOME, id).description());
    }

    @Test
    void reversalIsIndependentLinkedAndIdempotent() {
        actor("admin@fcms.local");
        long id = ledger.post(LedgerKind.INCOME, money("20.00", "Reverse " + suffix));
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.reverse(LedgerKind.INCOME, id, UUID.randomUUID(), "Correction"));
        actor(reviewer);
        long r = ledger.reverse(LedgerKind.INCOME, id, UUID.randomUUID(), "Correction");
        assertEquals(id, ledger.find(LedgerKind.INCOME, r).reversalOf());
        assertEquals(r, ledger.reverse(LedgerKind.INCOME, id, UUID.randomUUID(), "Retry"));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("delete from ledger_entries where id=?", id));
    }

    @Test
    void concurrentWithdrawalsNeverOverdrawPettyCash() throws Exception {
        actor(reviewer);
        ledger.post(LedgerKind.PETTY, money("100.00", "Funding " + suffix));
        BigDecimal balance = ledger.balance(LedgerKind.PETTY).amount();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 2; i++)
                jobs.add(
                        () -> {
                            actor(reviewer);
                            try {
                                var f = money(balance.toPlainString(), "Concurrent " + suffix);
                                f.setDirection("OUT");
                                ledger.post(LedgerKind.PETTY, f);
                                return true;
                            } catch (IllegalArgumentException e) {
                                return false;
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        });
            var results = pool.invokeAll(jobs);
            assertEquals(
                    1,
                    results.stream()
                            .filter(
                                    f -> {
                                        try {
                                            return f.get();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .count());
            assertEquals(0, ledger.balance(LedgerKind.PETTY).amount().signum());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expenseApprovalCannotBeSelfApproved() throws Exception {
        actor(staff);
        var f = new ExpenseForm();
        f.setCategory("Office");
        f.setDescription("Test expense");
        f.setPayee("Supplier");
        f.setAmount(new BigDecimal("10.00"));
        long id = expenses.save(null, f);
        expenses.transition(id, 0, "submit", "", "", UUID.randomUUID());
        assertThrows(
                AccessDeniedException.class,
                () -> expenses.transition(id, 1, "approve", "OK", "", UUID.randomUUID()));
        actor(reviewer);
        expenses.transition(id, 1, "approve", "Approved", "", UUID.randomUUID());
        expenses.transition(id, 2, "pay", "", "PAY-" + suffix, UUID.randomUUID());
        assertEquals("paid", expenses.find(id).status());
        assertThrows(IllegalArgumentException.class, () -> expenses.save(id, f));
        assertNotNull(
                db.queryForObject(
                        "select paid_at from expenses where id=?",
                        java.time.OffsetDateTime.class,
                        id));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("update expenses set amount=99 where id=?", id));
        expenses.transition(id, 3, "reverse", "Verified correction", "", UUID.randomUUID());
        assertEquals("PAY-" + suffix, expenses.find(id).paymentReference());
        var events = expenses.history(id, new RegisterQuery()).rows();
        assertEquals(4, events.size());
        assertTrue(
                events.stream()
                        .anyMatch(
                                e ->
                                        e.toStatus().equals("reversed")
                                                && e.reason().equals("Verified correction")
                                                && e.amount().compareTo(new BigDecimal("10.00"))
                                                        == 0));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from expense_events where expense_id=?", id));
        var web = browser(reviewer, "Verification123!");
        var response =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/finance/expenses/"
                                                        + id))
                                .timeout(java.time.Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(410, response.statusCode());
        assertTrue(response.body().isEmpty());
        actor(other);
        assertThrows(
                IllegalArgumentException.class, () -> expenses.history(id, new RegisterQuery()));
    }

    @Test
    void otpAttemptsPersistAndRecoveryTokensAreHashed() {
        recovery.request(staff);
        String body =
                db.queryForObject(
                        "select body from mail_outbox where recipient=? order by id desc limit 1",
                        String.class,
                        staff);
        String token = body.substring(body.indexOf("Token: ") + 7, body.indexOf("\nOTP:"));
        assertFalse(recovery.reset(token, "wrong", "NewPassword123!"));
        assertEquals(
                1,
                db.queryForObject(
                        "select attempts from password_challenges where user_id=? order by"
                                + " created_at desc limit 1",
                        Integer.class,
                        staffId));
        assertNotEquals(
                token,
                db.queryForObject(
                        "select token_hash from password_challenges where user_id=? order by"
                                + " created_at desc limit 1",
                        String.class,
                        staffId));
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

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

    @Test
    void allModulePagesRenderWithJspAndCorrectPermissions() throws Exception {
        var admin = browser("admin@fcms.local", "ChangeMe123!");
        for (String route :
                java.util.List.of(
                        "/accounting/dashboard",
                        "/admin/users",
                        "/admin/users/new",
                        "/account",
                        "/account/password",
                        "/clients/saccos",
                        "/clients/saccos/new",
                        "/clients/amcos",
                        "/clients/amcos/new",
                        "/clients/unions",
                        "/clients/microfinance",
                        "/clients/others",
                        "/tasks",
                        "/tasks/new",
                        "/leaves",
                        "/leaves/new",
                        "/staff-reports",
                        "/staff-reports/new",
                        "/accounting/journals?kind=PAYMENT",
                        "/accounting/vouchers/payment/new",
                        "/accounting/journals?kind=RECEIPT",
                        "/accounting/vouchers/receipt/new",
                        "/finance/petty-cash/reconciliation",
                        "/finance/petty-cash",
                        "/finance/cloud-backup",
                        "/finance/bulk-sms",
                        "/finance/bulk-sms/new?operation=STOCK_PURCHASE",
                        "/finance/petty-cash/new?operation=DEPOSIT",
                        "/finance/cloud-backup/new",
                        "/clients/unions/new",
                        "/clients/microfinance/new",
                        "/clients/others/new",
                        "/clients/saccos?view=revenue",
                        "/clients/amcos?view=revenue",
                        "/account",
                        "/backups",
                        "/accounting/accounts",
                        "/accounting/journals")) {
            var response =
                    admin.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), route);
            assertFalse(response.body().contains("Migration pending"), route);
            assertFalse(response.body().contains("app-page-description"), route);

        }
        var sw =
                admin.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:" + port + "/tasks?lang=sw"))
                                .timeout(java.time.Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertTrue(sw.body().contains("Kazi"));
        var pdf =
                admin.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/reports/statement.pdf?year=2026&lang=sw"))
                                .timeout(java.time.Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(410, pdf.statusCode());
        assertEquals(0, pdf.body().length);
        assertTrue(pdf.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        var noCsrf =
                admin.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:" + port + "/accounting/journals?kind=RECEIPT"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("amount=1"))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
        assertEquals(403, noCsrf.statusCode());
        var member = browser(staff, "Verification123!");
        for (String route :
                java.util.List.of(
                        "/admin/users",
                        "/finance/petty-cash",
                        "/backups",
                        "/accounting/trial-balance",
                        "/accounting/trial-balance/print",
                        "/accounting/trial-balance/print.pdf",
                        "/accounting/accounts/1/ledger",
                        "/accounting/accounts/1/ledger/print",
                        "/accounting/accounts/1/ledger/print.pdf")) {
            var denied =
                    member.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
            assertEquals(403, denied.statusCode(), route);
        }
    }

    @Test
    void staffGroupReportsAreVisibleOnlyToTheirOwnerAndAuthorizedReviewers() {
        actor(staff);
        var f = new WorkForm();
        f.setSubject("Group report");
        f.setDescription("Team activity");
        f.setWorkType("GROUP");
        f.setParticipants(java.util.List.of(otherId));
        f.setClientId(clientId);
        f.setOutcome("COMPLETED");
        long id = work.save(WorkKind.REPORT, null, f);
        actor(other);
        assertThrows(IllegalArgumentException.class, () -> work.find(WorkKind.REPORT, id));
        assertThrows(
                IllegalArgumentException.class,
                () -> work.save(WorkKind.REPORT, id, f));
        actor(reviewer);
        assertEquals(id, work.find(WorkKind.REPORT, id).id());
    }

    @Test
    void amcosCreationAndOptimisticEditsAreProtected() {
        actor(reviewer);
        var f = new com.foresight.fcms.client.category.dto.ClientCategoryForm();
        f.setClientName("AMCOS " + suffix);
        f.setRegion("Test");
        f.setPaymentStatusId(2L);
        f.setFinancialYearId(1L);
        long id =
                categories.createClient(
                        com.foresight.fcms.client.category.model.ClientCategory.AMCOS, f, null);
        var edit =
                categories.formFor(
                        com.foresight.fcms.client.category.model.ClientCategory.AMCOS, id);
        categories.updateClient(
                com.foresight.fcms.client.category.model.ClientCategory.AMCOS, id, edit, null);
        assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () ->
                        categories.updateClient(
                                com.foresight.fcms.client.category.model.ClientCategory.AMCOS,
                                id,
                                edit,
                                null));
        actor(staff);
        assertThrows(
                AccessDeniedException.class,
                () ->
                        categories.createClient(
                                com.foresight.fcms.client.category.model.ClientCategory.AMCOS,
                                f,
                                null));
    }

    @Autowired com.foresight.fcms.client.category.service.ClientCategoryService categories;

    @Test
    void staffAndClientFiltersRemainInsideOwnershipScope() {
        actor(reviewer);
        var f = new WorkForm();
        f.setOwnerId(staffId);
        f.setClientId(clientId);
        f.setSubject("Filtered task");
        f.setDescription("Scoped filter");
        long id = work.save(WorkKind.TASK, null, f);
        var q = new RegisterQuery();
        q.setOwnerId(staffId);
        q.setClientId(clientId);
        assertTrue(work.list(WorkKind.TASK, q).rows().stream().anyMatch(r -> r.id() == id));
        actor(other);
        assertTrue(work.list(WorkKind.TASK, q).rows().isEmpty());
    }

    @Test
    void remoteDirectoryQueriesAreBounded() {
        actor(reviewer);
        assertTrue(directory.search("", 0).items().size() <= 25);
        assertTrue(clientDirectory.search("", 0).items().size() <= 25);
        assertThrows(IllegalArgumentException.class, () -> directory.search("x", -1));
    }

    @Autowired UserDirectoryService directory;
    @Autowired com.foresight.fcms.client.service.ClientDirectoryService clientDirectory;

    @Autowired AccountService accounts;
    @Autowired com.foresight.fcms.client.saccos.service.SaccosService saccosClients;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.foresight.fcms.upload.UploadInspectionService uploadInspection;

    @Test
    void contractMetadataIsAtomicAndStaleUploadsLeaveNoNewFiles() throws Exception {
        actor("admin@fcms.local");
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            assertFalse(
                                    org.springframework.transaction.support
                                            .TransactionSynchronizationManager
                                            .isActualTransactionActive());
                            return invocation.callRealMethod();
                        })
                .when(uploadInspection)
                .inspect(org.mockito.ArgumentMatchers.any(byte[].class));
        for (var category : com.foresight.fcms.client.category.model.ClientCategory.values()) {
            var form = categories.newForm();
            form.setClientName("Upload " + category + " " + suffix);
            form.setRegion("Test");
            form.setAnnualFee(new BigDecimal("321.09"));
            long id = categories.createClient(category, form, contractUpload());
            String old = categories.findClient(category, id).contractFile();
            var edit = categories.formFor(category, id);
            categories.updateClient(category, id, edit, contractUpload());
            String current = categories.findClient(category, id).contractFile();
            assertNotEquals(old, current);
            assertTrue(
                    java.nio.file.Files.exists(
                            java.nio.file.Path.of(".verification/contracts-tested", old)));
            assertTrue(categories.contract(category, id).exists());
            var files = contractFiles();
            assertThrows(
                    org.springframework.dao.OptimisticLockingFailureException.class,
                    () -> categories.updateClient(category, id, edit, contractUpload()));
            assertEquals(files, contractFiles());
            assertEquals(current, categories.findClient(category, id).contractFile());
            assertEquals(
                    0,
                    new BigDecimal("321.09")
                            .compareTo(categories.findClient(category, id).annualFee()));
            assertTrue(
                    db.queryForObject(
                            "select exists(select 1 from audit_events where action='client.update'"
                                    + " and resource=? and resource_id=? and detail like ?)",
                            Boolean.class,
                            category.getTableName(),
                            id,
                            "%before=" + old + "; after=" + current + "%"));
        }
        var form = saccosClients.newForm();
        form.setSaccosName("Uploaded SACCOS " + suffix);
        form.setRegion("Test");
        long id = saccosClients.createSaccos(form, contractUpload());
        var edit = saccosClients.formFor(id);
        String old =
                db.queryForObject("select contract_file from saccos where id=?", String.class, id);
        saccosClients.updateSaccos(id, edit, contractUpload());
        var files = contractFiles();
        assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> saccosClients.updateSaccos(id, edit, contractUpload()));
        assertEquals(files, contractFiles());
        assertTrue(
                java.nio.file.Files.exists(
                        java.nio.file.Path.of(".verification/contracts-tested", old)));
        String current =
                db.queryForObject("select contract_file from saccos where id=?", String.class, id);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("error.uploadRejected"))
                .when(uploadInspection)
                .inspect(org.mockito.ArgumentMatchers.any(byte[].class));
        assertThrows(
                IllegalArgumentException.class,
                () -> saccosClients.updateSaccos(id, saccosClients.formFor(id), contractUpload()));
        assertEquals(
                current,
                db.queryForObject("select contract_file from saccos where id=?", String.class, id));
        assertEquals(files, contractFiles());
    }

    @Test
    void concurrentPhotosUseVersionsAndCleanTheLosingFile() throws Exception {
        String email = "photo-concurrent-" + suffix + "@test.invalid";
        long id = user(email, 2);
        var barrier = new CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            assertFalse(
                                    org.springframework.transaction.support
                                            .TransactionSynchronizationManager
                                            .isActualTransactionActive());
                            barrier.await(10, TimeUnit.SECONDS);
                            return invocation.callRealMethod();
                        })
                .when(uploadInspection)
                .inspect(org.mockito.ArgumentMatchers.any(byte[].class));
        var folder = java.nio.file.Path.of(".verification/avatars");
        java.nio.file.Files.createDirectories(folder);
        Set<String> before;
        try (var files = java.nio.file.Files.list(folder)) {
            before =
                    files.map(f -> f.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet());
        }
        var image =
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<String>>();
            for (int n = 0; n < 2; n++)
                results.add(
                        executor.submit(
                                () -> {
                                    actor(email);
                                    try {
                                        accounts.photo(
                                                new org.springframework.mock.web.MockMultipartFile(
                                                        "photo",
                                                        "profile.png",
                                                        "image/png",
                                                        bytes.toByteArray()));
                                        return "saved";
                                    } catch (
                                            org.springframework.dao
                                                            .OptimisticLockingFailureException
                                                    e) {
                                        return "stale";
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                }));
            assertEquals(
                    Set.of("saved", "stale"),
                    Set.of(
                            results.get(0).get(20, TimeUnit.SECONDS),
                            results.get(1).get(20, TimeUnit.SECONDS)));
        }
        String stored =
                db.queryForObject("select image_path from users where id=?", String.class, id);
        assertTrue(java.nio.file.Files.exists(folder.resolve(stored)));
        try (var files = java.nio.file.Files.list(folder)) {
            var added =
                    files.map(f -> f.getFileName().toString())
                            .filter(f -> !before.contains(f))
                            .toList();
            assertEquals(List.of(stored), added);
        }
        org.mockito.Mockito.reset(uploadInspection);
        actor(email);
        accounts.photo(
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "replacement.png", "image/png", bytes.toByteArray()));
        assertFalse(java.nio.file.Files.exists(folder.resolve(stored)));
        assertTrue(accounts.photo().exists());
    }

    Set<String> contractFiles() throws Exception {
        try (var files =
                java.nio.file.Files.list(java.nio.file.Path.of(".verification/contracts-tested"))) {
            return files.map(f -> f.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    org.springframework.mock.web.MockMultipartFile contractUpload() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var document = new com.lowagie.text.Document();
        com.lowagie.text.pdf.PdfWriter.getInstance(document, bytes);
        document.open();
        document.add(new com.lowagie.text.Paragraph("Synthetic contract verification"));
        document.close();
        return new org.springframework.mock.web.MockMultipartFile(
                "contractUpload", "contract.pdf", "application/pdf", bytes.toByteArray());
    }

    @Test
    void profilePhotosAreContentCheckedAndOwnerScoped() throws Exception {
        actor(staff);
        var image =
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        accounts.photo(
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "profile.png", "image/png", bytes.toByteArray()));
        assertTrue(accounts.hasPhoto());
        assertTrue(accounts.photo().exists());
        actor(other);
        assertFalse(accounts.hasPhoto());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        accounts.photo(
                                new org.springframework.mock.web.MockMultipartFile(
                                        "photo",
                                        "bad.png",
                                        "image/png",
                                        "not an image".getBytes())));
    }

    @Test
    void userAdministrationCreatesHashesAndRejectsStaleChanges() {
        actor(reviewer);
        var f = new UserForm();
        f.setFirstName("New");
        f.setLastName("Account");
        f.setEmail("new-" + suffix + "@test.invalid");
        f.setRoleId(2L);
        f.setStatusId(1L);
        f.setPassword("NewAccountPassword123!");
        f.setClaims(java.util.List.of("workspace.admin", "client.export"));
        long id = users.save(null, f);
        assertFalse(
                db.queryForObject("select password from users where id=?", String.class, id)
                        .equals(f.getPassword()));
        var edit = users.form(id);
        edit.setFirstName("Updated");
        users.save(id, edit);
        assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> users.save(id, edit));
        actor("new-" + suffix + "@test.invalid");
        assertTrue(access.current().has("client.export"));
    }

    @Autowired com.foresight.fcms.notifications.service.MailOutboxService outbox;

    @Test
    void expiredMailLeasesTerminateAndStaleWorkersCannotAcknowledge() {
        long id =
                db.queryForObject(
                        "insert into"
                            + " mail_outbox(recipient,subject,body,status,attempts,available_at)"
                            + " values"
                            + " ('test@test.invalid','Test','Synthetic','SENDING',2,current_timestamp+interval"
                            + " '1 hour') returning id",
                        Long.class);
        outbox.sent(id, 1);
        assertEquals(
                "SENDING",
                db.queryForObject("select status from mail_outbox where id=?", String.class, id));
        outbox.failed(id, 2);
        assertEquals(
                "PENDING",
                db.queryForObject("select status from mail_outbox where id=?", String.class, id));
        db.update(
                "update mail_outbox set"
                        + " status='SENDING',attempts=5,available_at=current_timestamp-interval '1"
                        + " hour' where id=?",
                id);
        outbox.claim();
        assertEquals(
                "FAILED",
                db.queryForObject("select status from mail_outbox where id=?", String.class, id));
    }

    @Test
    void reconciliationDetectsDriftAndDeniesStaff() {
        actor(reviewer);
        assertTrue(ledger.reconcile().stream().allMatch(LedgerService.Reconciliation::matches));
        db.update("update ledger_accounts set balance=balance+1 where kind='CLOUD'");
        try {
            assertFalse(
                    ledger.reconcile().stream()
                            .filter(r -> r.kind() == LedgerKind.CLOUD)
                            .findFirst()
                            .orElseThrow()
                            .matches());
        } finally {
            db.update("update ledger_accounts set balance=balance-1 where kind='CLOUD'");
        }
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> ledger.reconcile());
    }

    @Test
    void sensitiveMutationAuditRetainsRequestCorrelation() {
        actor(staff);
        String request = java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("requestId", request);
        long id;
        try {
            var form = new WorkForm();
            form.setSubject("Correlated leave");
            form.setDescription("Synthetic verification leave");
            id = work.save(WorkKind.LEAVE, null, form);
        } finally {
            org.slf4j.MDC.remove("requestId");
        }
        assertEquals(
                request,
                db.queryForObject(
                        "select correlation_id from audit_events where resource='work_items' and"
                                + " resource_id=? order by id desc limit 1",
                        String.class,
                        id));
    }

    @Test
    void bulkSmsCanRecordUnitOnlyMovementsAndReverseThem() {
        actor(reviewer);
        var stock = money("0", "Synthetic promotional SMS stock");
        stock.setUnits(5);
        long id = ledger.post(LedgerKind.BULK, stock);
        assertEquals(5, ledger.find(LedgerKind.BULK, id).units());
        actor("admin@fcms.local");
        long reversal =
                ledger.reverse(LedgerKind.BULK, id, UUID.randomUUID(), "Verification reversal");
        assertEquals(-5, ledger.find(LedgerKind.BULK, reversal).units());
    }

    @Autowired com.foresight.fcms.backup.service.BackupCatalogService backups;

    @Test
    void backupHistoryUsesServerFiltersAndDeterministicPages() {
        actor(reviewer);
        var q = new RegisterQuery();
        q.setSize(10);
        q.setStatus("READY");
        var page = backups.list(q);
        assertTrue(page.rows().size() <= 10);
        assertTrue(page.rows().stream().allMatch(b -> b.status().equals("READY")));
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> backups.list(q));
    }

    @Autowired com.foresight.fcms.accounting.service.AccountingService accounting;
    @Autowired com.foresight.fcms.accounting.service.JournalService journals;
    @Autowired com.foresight.fcms.accounting.service.StatementService statements;
    @Autowired com.foresight.fcms.accounting.service.StatementPresentation statementPresentation;

    @Autowired com.foresight.fcms.accounting.service.PostingMappingService mappings;

    @Autowired com.foresight.fcms.accounting.service.ActivityTransactionService configurations;

    @Test
    void transactionConfigurationCreatesAndApprovesThroughCsrfProtectedHttp() throws Exception {
        actor(reviewer);
        long debit = glAccount("431001", "HTTP expense template", "EXPENSE", false),
                credit = glAccount("131001", "HTTP bank template", "ASSET", true);
        long activity =
                db.queryForObject("select id from gl_activities where code='EXPENSE'", Long.class);
        var maker = browser(reviewer, "Verification123!");
        var checker = browser("admin@fcms.local", "ChangeMe123!");
        String base = "http://127.0.0.1:" + port;
        var page =
                maker.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                base
                                                        + "/accounting/config/transactions?create=true"))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("data-config-open"));
        String input =
                "_csrf="
                        + csrfToken(page.body())
                        + "&activityId="
                        + activity
                        + "&code=HTTP-"
                        + suffix
                        + "&name=HTTP+payment&event=EXPENSE_PAYMENT&debitAccountId="
                        + debit
                        + "&creditAccountId="
                        + credit
                        + "&voucherKind=PAYMENT&cashFlow=OPERATING&policyReference=HTTP+policy+"
                        + suffix
                        + "&requestKey="
                        + UUID.randomUUID();
        var created =
                maker.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                base + "/accounting/config/transactions"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, created.statusCode());
        String resource = CreationFeedback.follow(maker, created);
        long transaction = Long.parseLong(resource.substring(resource.lastIndexOf('/') + 1));
        long template =
                db.queryForObject(
                        "select id from gl_posting_mappings where transaction_id=?",
                        Long.class,
                        transaction);
        long createdVersion = configurations.transaction(transaction).version();
        var uri = java.net.URI.create(base + "/accounting/config/transactions/" + transaction);
        String approve = uri + "/templates/" + template + "/actions/approve",
                reject = uri + "/templates/" + template + "/actions/reject";
        try {
            var makerPage =
                    maker.send(
                            java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, makerPage.statusCode());
            assertTrue(makerPage.body().contains("/actions/cancel"));
            assertFalse(makerPage.body().contains("/actions/approve"));
            var self =
                    maker.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(approve))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "_csrf="
                                                            + csrfToken(makerPage.body())
                                                            + "&version=0&transactionVersion="
                                                            + createdVersion
                                                            + "&reason=Preserved+self+review"))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, self.statusCode());
            assertTrue(self.body().contains("Preserved self review"));
            assertEquals("PENDING", mappings.find(template).state());
            var checkerPage =
                    checker.send(
                            java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertTrue(checkerPage.body().contains("/actions/approve"));
            assertTrue(checkerPage.body().contains("/actions/reject"));
            assertFalse(checkerPage.body().contains("/actions/cancel"));
            var approved =
                    checker.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(approve))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "_csrf="
                                                            + csrfToken(checkerPage.body())
                                                            + "&version=0&transactionVersion="
                                                            + createdVersion
                                                            + "&reason=Independent+HTTP+review"))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(302, approved.statusCode());
            assertEquals("APPROVED", mappings.find(template).state());
            assertEquals("Independent HTTP review", mappings.find(template).reviewReason());
            var stale =
                    checker.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(reject))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(
                                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                                    "_csrf="
                                                            + csrfToken(checkerPage.body())
                                                            + "&version=0&transactionVersion="
                                                            + createdVersion
                                                            + "&reason=Stale+review+evidence"))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, stale.statusCode());
            assertTrue(stale.body().contains("Stale review evidence"));
            assertTrue(stale.body().contains("Unapplied review reason"));
            assertEquals("APPROVED", mappings.find(template).state());
            var legacy =
                    checker.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    base
                                                            + "/accounting/posting-mappings/"
                                                            + template))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(404, legacy.statusCode());
            var oldCommand =
                    maker.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    base + "/accounting/posting-mappings"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(404, oldCommand.statusCode());
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from gl_posting_mappings where transaction_id=?",
                            Integer.class,
                            transaction));
        } finally {
            actor(reviewer);
            configurations.transactionState(
                    transaction,
                    configurations.transaction(transaction).version(),
                    false,
                    "Finished isolated HTTP configuration fixture");
        }
    }

    String csrfToken(String html) {
        var token =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(html);
        assertTrue(token.find(), "CSRF token is rendered");
        return java.net.URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void postingMappingsRequireIndependentReviewAndKeepEveryApprovedRevision() throws Exception {
        actor(reviewer);
        long cash = glAccount("121001", "Mapping bank", "ASSET", true);
        long income = glAccount("521001", "Mapping revenue", "INCOME", false);
        long replacementIncome = glAccount("521002", "Mapping revenue revision", "INCOME", false);
        var form = postingMapping(cash, income, "Mapping policy " + suffix);
        long first = mappings.create(form);
        assertEquals(first, mappings.create(form));
        form.setPolicyReference("Changed retry " + suffix);
        assertThrows(IllegalArgumentException.class, () -> mappings.create(form));
        assertThrows(
                IllegalArgumentException.class,
                () -> mappings.review(first, 0, "approve", "Self approval"));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_posting_mappings set"
                                    + " state='APPROVED',checker_id=maker_id,review_reason='Self"
                                    + " approval',reviewed_at=current_timestamp,version=version+1"
                                    + " where id=?",
                                first));
        actor("admin@fcms.local");
        mappings.review(first, 0, "approve", "Independent policy review");
        assertEquals(first, mappings.current("INCOME_IN").orElseThrow().id());
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_posting_mappings set"
                                    + " policy_reference='tampered',version=version+1 where id=?",
                                first));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from gl_posting_mappings where id=?", first));
        actor(reviewer);
        var invalid = postingMapping(cash, income, "Invalid pair");
        invalid.setCashFlow("NONE");
        assertThrows(IllegalArgumentException.class, () -> mappings.create(invalid));
        invalid.setCashFlow("OPERATING");
        invalid.setVoucherKind("PAYMENT");
        assertThrows(IllegalArgumentException.class, () -> mappings.create(invalid));
        invalid.setVoucherKind("JOURNAL");
        invalid.setCreditAccountId(cash);
        assertThrows(IllegalArgumentException.class, () -> mappings.create(invalid));
        invalid.setCreditAccountId(accounting.account(income).parentId());
        assertThrows(IllegalArgumentException.class, () -> mappings.create(invalid));
        long second =
                mappings.create(
                        postingMapping(cash, replacementIncome, "Replacement policy " + suffix));
        var target = accounting.account(replacementIncome);
        accounting.accountState(target.id(), target.version(), false);
        actor("admin@fcms.local");
        assertThrows(
                IllegalArgumentException.class,
                () -> mappings.review(second, 0, "approve", "Inactive target"));
        assertEquals(
                first,
                mappings.current("INCOME_IN").orElseThrow().id(),
                "Failed replacement keeps the existing mapping");
        assertEquals("PENDING", mappings.find(second).state());
        accounting.accountState(target.id(), accounting.account(target.id()).version(), true);
        mappings.review(second, 0, "approve", "Reviewed replacement policy");
        assertEquals(second, mappings.current("INCOME_IN").orElseThrow().id());
        assertEquals("RETIRED", mappings.find(first).state());
        assertEquals("Independent policy review", mappings.find(first).reviewReason());
        assertNotNull(mappings.find(first).retiredAt());
        assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> mappings.review(second, 0, "reject", "Stale review"));

        actor(reviewer);
        long raceOne = mappings.create(postingMapping(cash, income, "Concurrent one " + suffix));
        long raceTwo =
                mappings.create(
                        postingMapping(cash, replacementIncome, "Concurrent two " + suffix));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs =
                    List.of(raceOne, raceTwo).stream()
                            .<Callable<Boolean>>map(
                                    id ->
                                            () -> {
                                                actor("admin@fcms.local");
                                                try {
                                                    mappings.review(
                                                            id,
                                                            0,
                                                            "approve",
                                                            "Serialized replacement review");
                                                    return true;
                                                } finally {
                                                    SecurityContextHolder.clearContext();
                                                }
                                            })
                            .toList();
            for (var result : pool.invokeAll(jobs)) assertTrue(result.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        actor(reviewer);
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from gl_posting_mappings where source_event='INCOME_IN'"
                                + " and state='APPROVED'",
                        Integer.class));
        assertTrue(
                Set.of(raceOne, raceTwo)
                        .contains(mappings.current("INCOME_IN").orElseThrow().id()));
        assertEquals(
                1,
                List.of(mappings.find(raceOne), mappings.find(raceTwo)).stream()
                        .filter(m -> m.state().equals("RETIRED"))
                        .count());
        var cancelForm = postingMapping(cash, income, "Cancelled policy " + suffix);
        cancelForm.setEvent("PETTY_IN");
        long cancelled = mappings.create(cancelForm);
        mappings.review(cancelled, 0, "cancel", "Withdraw proposed policy");
        assertEquals("CANCELLED", mappings.find(cancelled).state());
        var rejectForm = postingMapping(cash, income, "Rejected policy " + suffix);
        rejectForm.setEvent("CLOUD_IN");
        long rejected = mappings.create(rejectForm);
        actor("admin@fcms.local");
        mappings.review(rejected, 0, "reject", "Policy evidence is incomplete");
        assertEquals("REJECTED", mappings.find(rejected).state());
        assertTrue(mappings.current("CLOUD_IN").isEmpty());
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_posting_mappings set state='PENDING',version=version+1"
                                        + " where id=?",
                                rejected));
        var web = browser(reviewer, "Verification123!");
        for (String route :
                List.of(
                        "/accounting/config/activities",
                        "/accounting/config/transactions",
                        "/accounting/config/transactions?create=true",
                        "/accounting/config/transactions/"
                                + mappings.find(first).transactionId()
                                + "?review="
                                + first,
                        "/accounting/config/transactions/"
                                + mappings.find(second).transactionId()
                                + "?review="
                                + second
                                + "&lang=sw",
                        "/accounting/config/transactions/"
                                + mappings.find(rejected).transactionId()
                                + "?review="
                                + rejected)) {
            var response =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), route);
            assertFalse(response.body().contains("app-page-description"));
            assertFalse(response.body().contains("data-table-actions-header"));
        }
        var page =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/config/transactions?create=true&lang=sw"))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        var token =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(page.body());
        assertTrue(token.find());
        String input =
                "_csrf="
                        + java.net.URLEncoder.encode(
                                token.group(1), java.nio.charset.StandardCharsets.UTF_8)
                        + "&activityId="
                        + db.queryForObject(
                                "select id from gl_activities where code='INCOME'", Long.class)
                        + "&code=INVALID-"
                        + suffix
                        + "&name=Retained+transaction&event=INCOME_IN&debitAccountId="
                        + cash
                        + "&creditAccountId="
                        + income
                        + "&voucherKind=RECEIPT&cashFlow=NONE&policyReference=Retained+mapping+evidence&requestKey="
                        + UUID.randomUUID();
        var failed =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/config/transactions?lang=sw"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, failed.statusCode());
        assertTrue(failed.body().contains("Retained mapping evidence"));
        assertTrue(failed.body().contains("Chagua kundi la mtiririko wa fedha"));
        var csrfDenied =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/config/transactions"))
                                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403, csrfDenied.statusCode());
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> mappings.current("PETTY_IN"));
        assertThrows(
                AccessDeniedException.class,
                () -> mappings.create(postingMapping(cash, income, "Forbidden")));
        db.update("insert into user_claims(user_id,claim) values (?,'accounting.read')", staffId);
        try {
            assertThrows(AccessDeniedException.class, () -> mappings.find(first));
            db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin')", staffId);
            assertEquals(first, mappings.find(first).id());
            assertThrows(
                    AccessDeniedException.class,
                    () -> mappings.review(second, 1, "approve", "Forbidden"));
            var reader = browser(staff, "Verification123!");
            var denied =
                    reader.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/posting-mappings/new"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
        } finally {
            db.update(
                    "delete from user_claims where user_id=? and claim in ('accounting.read','workspace.admin')", staffId);
        }
    }

    com.foresight.fcms.accounting.dto.PostingMappingForm postingMapping(
            long debit, long credit, String policy) {
        var form = new com.foresight.fcms.accounting.dto.PostingMappingForm();
        form.setDebitAccountId(debit);
        form.setCreditAccountId(credit);
        form.setVoucherKind("RECEIPT");
        form.setCashFlow("OPERATING");
        form.setPolicyReference(policy);
        return form;
    }

    @Test
    void chartOfAccountsPreservesHierarchyMetadataAndLocalizedValidation() throws Exception {
        actor("admin@fcms.local");
        long posting = glAccount("221001", "Synthetic payable", "LIABILITY", false);
        var account = accounting.account(posting);
        var parent = accounting.account(account.parentId());
        var choice = accounting.selected(List.of(parent.id())).getFirst();
        assertEquals(3, choice.depth());
        assertEquals("LIABILITY", choice.classification());
        assertEquals("CREDIT", choice.normalBalance());
        var children = accounting.accounts(new RegisterQuery(), parent.id()).rows();
        assertTrue(children.stream().anyMatch(a -> a.id() == posting));
        assertTrue(children.stream().allMatch(a -> Objects.equals(a.parentId(), parent.id())));
        var invalid = new com.foresight.fcms.accounting.dto.AccountForm();
        invalid.setParentId(parent.id());
        invalid.setCode("221002");
        invalid.setName("Invalid cash liability");
        invalid.setNormalBalance("CREDIT");
        invalid.setCashAccount(true);
        assertThrows(IllegalArgumentException.class, () -> accounting.saveAccount(null, invalid));
        invalid.setCashAccount(false);
        invalid.setCode("111999");
        assertThrows(IllegalArgumentException.class, () -> accounting.saveAccount(null, invalid));
        var edit = new com.foresight.fcms.accounting.dto.AccountForm();
        edit.setParentId(parent.id());
        edit.setCode(account.code());
        edit.setName("Reviewed payable");
        edit.setNormalBalance(account.normalBalance());
        edit.setVersion(account.version());
        accounting.saveAccount(posting, edit);
        assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> accounting.saveAccount(posting, edit));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> accounting.accountState(parent.id(), parent.version(), false));
        var updated = accounting.account(posting);
        accounting.accountState(posting, updated.version(), false);
        assertFalse(
                accounting.choices("221001", 0, true).items().stream()
                        .anyMatch(c -> c.id() == posting));
        accounting.accountState(posting, accounting.account(posting).version(), true);
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_accounts set normal_balance='DEBIT' where id=?",
                                posting));

        for (int n = 3; n <= 5; n++)
            glAccount("22100" + n, "Pager payable " + n, "LIABILITY", false);
        var web = browser("admin@fcms.local", "ChangeMe123!");
        for (String language : List.of("en", "sw")) {
            var register =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/accounts?size=10&lang="
                                                            + language))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            assertTrue(register.body().contains("</html>"), "The full parent-account register renders");
            assertTrue(register.body().contains("data-coa-tree"));
            assertTrue(register.body().contains("data-expandable=\"true\""));
            assertTrue(register.body().contains("data-table-selection=\"single\""));
            assertTrue(register.body().contains(language.equals("sw") ? "Madeni" : "Liabilities"));
            assertFalse(register.body().contains("data-table-actions-header"));
        }
        var page =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/accounts/new?lang=sw"))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        var token =
                java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                        .matcher(page.body());
        assertTrue(token.find());
        String input =
                "_csrf="
                        + java.net.URLEncoder.encode(
                                token.group(1), java.nio.charset.StandardCharsets.UTF_8)
                        + "&parentId="
                        + parent.id()
                        + "&code=221009&name=&description=Retained+evidence&normalBalance=CREDIT&version=0";
        var response =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/accounts?lang=sw"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("Sehemu hii inahitajika."),
                "Swahili validation is rendered");
        assertTrue(response.body().contains("Retained evidence"), "Failed input is preserved");
        assertTrue(
                response.body().contains("data-normal-balance=\"CREDIT\""),
                "Parent metadata survives failed submission");
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from gl_accounts where code='221009'", Integer.class));
    }

    @Test
    void coaTreeCountsEveryMatchAndLoadsBoundedStableBatches() throws Exception {
        actor("admin@fcms.local");
        glAccount("331099", "Batch setup", "EQUITY", false);
        for (int n = 100; n < 125; n++)
            glAccount("331" + n, "Grouped verification " + n, "EQUITY", false);
        var q = new CoaQuery();
        q.setSearch("Grouped verification");
        q.setSize(10);
        var initial = accounting.chart(q, null);
        assertEquals(25, initial.total());
        assertEquals(10, initial.records().rows().size());
        assertTrue(initial.records().hasNext());
        assertTrue(initial.records().rows().stream().allMatch(r -> r.kind().equals("POSTING") && !r.expandable()));
        q.setPage(1);
        var middle = accounting.chart(q, null).records();
        q.setPage(2);
        var last = accounting.chart(q, null).records();
        assertEquals(10, middle.rows().size());
        assertEquals(5, last.rows().size());
        assertFalse(last.hasNext());
        var combined = new ArrayList<com.foresight.fcms.accounting.dto.AccountingViews.CoaRow>();
        combined.addAll(initial.records().rows());combined.addAll(middle.rows());combined.addAll(last.rows());
        assertEquals(25, combined.stream().map(r -> r.account().id()).distinct().count());
        assertEquals(combined.stream().map(r -> r.account().code()).sorted().toList(),
                combined.stream().map(r -> r.account().code()).toList());
        q.setPage(0);q.setDirection("desc");
        assertEquals(combined.getLast().account().id(), accounting.chart(q, null).records().rows().getFirst().account().id());
        var first = combined.getFirst().account();
        accounting.accountState(first.id(), first.version(), false);
        try {
            q.setStatus("ACTIVE");assertEquals(24, accounting.chart(q, null).total());
            q.setStatus("INACTIVE");assertEquals(1, accounting.chart(q, null).total());
            q.setStatus("");q.setSearch("%");assertEquals(0, accounting.chart(q, null).total());
            q.setSearch("Grouped verification");
            assertEquals(25, accounting.chart(q, first.parentId()).total());
            assertThrows(IllegalArgumentException.class, () -> accounting.chart(q, first.id()));
            q.setClassification("ASSET OR TRUE");
            assertThrows(IllegalArgumentException.class, () -> accounting.chart(q, null));
            q.setClassification("");
            for (String sort : List.of("code", "name", "parent", "normal", "status", "classification", "kind")) {
                q.setSort(sort);assertEquals(10, accounting.chart(q, null).records().rows().size());
            }
            q.setSort("invalid");assertThrows(IllegalArgumentException.class, () -> accounting.chart(q, null));q.setSort("code");
            var web = browser("admin@fcms.local", "ChangeMe123!");
            for (String lang : List.of("en", "sw")) {
                var response = web.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"
                        + port + "/accounting/accounts?search=Grouped%20verification&size=10&page=1&kind=POSTING&classification=EQUITY&lang=" + lang))
                        .GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("data-page=\"1\""));
                assertTrue(response.body().contains("page=2"));assertTrue(response.body().contains("page=0"));
                assertTrue(response.body().contains("classification=EQUITY"));assertTrue(response.body().contains("kind=POSTING"));
                assertTrue(response.body().contains("data-expandable=\"false\""));
            }
            actor(staff);assertThrows(AccessDeniedException.class, () -> accounting.chart(q, null));
        } finally {
            actor("admin@fcms.local");accounting.accountState(first.id(), accounting.account(first.id()).version(), true);
        }
    }

    @Test
    void doubleEntryAccountingRetainsReviewPostingStatementsAndHistoricalPeriodGuards()
            throws Exception {
        actor(reviewer);
        var opening = today().withDayOfYear(1);
        var today = today();
        var book = accounting.books();
        Long openingSource = null;
        if (book.isEmpty()) {
            var legacySource = money("13.25", "Reviewed pre-cutover source " + suffix);
            legacySource.setDate(opening.minusDays(1));
            openingSource = ledger.post(LedgerKind.INCOME, legacySource);
            assertThrows(
                    IllegalArgumentException.class, () -> statements.statements(opening, today));
            AccountingHistoryFixtures.books(db, opening,
                    System.getenv().getOrDefault("FCMS_TEST_OPENING_MODE", "ZERO"), reviewer, "admin@fcms.local");
        }
        actor(reviewer);
        long period = AccountingHistoryFixtures.period(db, "GL verification", opening,
                today.withMonth(12).withDayOfMonth(31), reviewer);
        long cash = glAccount("111001", "Cash", "ASSET", true);
        long equity = glAccount("311001", "Owner capital", "EQUITY", false);
        long income = glAccount("511001", "Service revenue", "INCOME", false);
        long expense = glAccount("411001", "Office expenses", "EXPENSE", false);
        if (!accounting.books().orElseThrow().ready()) {
            var seed = glVoucher("OPENING", cash, equity, "500.00", "NONE");
            seed.setDate(opening);
            glPost(seed);
        }
        actor(reviewer);
        assertTrue(accounting.books().orElseThrow().ready());
        var before = statements.statements(opening, today);
        if (accounting.books().orElseThrow().openingMode().equals("JOURNAL")) {
            assertEquals(0, before.equityOpening().compareTo(new BigDecimal("500.00")));
            assertEquals(0, before.cashOpening().compareTo(new BigDecimal("500.00")));
            var openingQuery = new com.foresight.fcms.accounting.dto.LedgerQuery();
            openingQuery.setFrom(opening);
            openingQuery.setTo(today);
            var openingLedger = statements.ledger(cash, openingQuery);
            assertEquals(0, new BigDecimal("500.00").compareTo(openingLedger.opening()));
            assertEquals(openingLedger.opening(), openingLedger.closing());
            assertEquals(0, openingLedger.debit().signum());
            assertEquals(0, openingLedger.credit().signum());
            assertEquals(0, openingLedger.matchingCount());
            assertTrue(openingLedger.records().rows().isEmpty());
        }
        var receipt = glVoucher("RECEIPT", cash, income, "100.35", "OPERATING");
        var invalid = glVoucher("JOURNAL", cash, income, "1.00", "OPERATING");
        invalid.getLines().get(1).setCredit(new BigDecimal("0.99"));
        assertThrows(IllegalArgumentException.class, () -> journals.createDraft(invalid));
        long receiptId = glPost(receipt);
        actor(reviewer);
        assertEquals(receiptId, journals.createDraft(receipt));
        receipt.setDescription("Changed retry payload");
        assertThrows(IllegalArgumentException.class, () -> journals.createDraft(receipt));
        glPost(glVoucher("PAYMENT", expense, cash, "20.10", "OPERATING"));
        glPost(glVoucher("JOURNAL", cash, equity, "1000.00", "FINANCING"));
        actor(reviewer);
        var statement = statements.statements(opening, today);
        assertEquals(
                0, statement.profit().subtract(before.profit()).compareTo(new BigDecimal("80.25")));
        assertEquals(
                0,
                statement
                        .cashClosing()
                        .subtract(before.cashClosing())
                        .compareTo(new BigDecimal("1080.25")));
        assertTrue(statement.balanceSheetMatches());
        assertTrue(statement.cashFlowMatches());
        assertTrue(statement.equityMatches());
        var trial = new RegisterQuery();
        trial.setFrom(opening);
        trial.setTo(today);
        trial.setSize(100);
        var rows = statements.trial(trial).rows();
        var dr =
                rows.stream()
                        .map(
                                com.foresight.fcms.accounting.service.StatementService.TrialRow
                                        ::closingDebit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        var cr =
                rows.stream()
                        .map(
                                com.foresight.fcms.accounting.service.StatementService.TrialRow
                                        ::closingCredit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_journals set description='tampered' where id=?",
                                receiptId));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from gl_lines where journal_id=?", receiptId));
        long correctionUser = user("correction-" + suffix + "@test.invalid", 1);
        actor("correction-" + suffix + "@test.invalid");
        long reversal =
                journals.reverse(
                        receiptId, UUID.randomUUID(), today, "Synthetic receipt correction");
        journals.transition(reversal, 0, "submit", "");
        actor(reviewer);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        journals.transition(
                                reversal, 1, "post", "Original maker cannot check a reversal"));
        actor("admin@fcms.local");
        journals.transition(reversal, 1, "post", "Independent correction review");
        actor(reviewer);
        var after = statements.statements(opening, today);
        assertEquals(
                0, after.profit().subtract(before.profit()).compareTo(new BigDecimal("-20.10")));
        assertTrue(after.balanceSheetMatches());
        assertTrue(after.cashFlowMatches());
        assertTrue(after.equityMatches());
        var duplicate = glVoucher("JOURNAL", cash, equity, "2.00", "FINANCING");
        var executor = Executors.newFixedThreadPool(2);
        long draft;
        try {
            var one =
                    executor.submit(
                            () -> {
                                actor(reviewer);
                                try {
                                    return journals.createDraft(duplicate);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            });
            var two =
                    executor.submit(
                            () -> {
                                actor(reviewer);
                                try {
                                    return journals.createDraft(duplicate);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            });
            draft = one.get(15, TimeUnit.SECONDS);
            assertEquals(draft, two.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        actor(reviewer);
        ledger.post(LedgerKind.INCOME, money("0.12", "Unposted closing evidence " + suffix));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> AccountingHistoryFixtures.periodState(db, period, true, reviewer));
        var web = browser(reviewer, "Verification123!");
        for (String route :
                List.of(
                        "/accounting/accounts",
                        "/accounting/accounts/new",
                        "/accounting/accounts/" + cash,
                        "/accounting/accounts/" + cash + "/edit",
                        "/accounting/journals",
                        "/accounting/vouchers/receipt/new",
                        "/accounting/vouchers/payment/new",
                        "/accounting/journals/" + draft + "/edit",
                        "/accounting/journals/" + receiptId,
                        "/accounting/statements/income-statement",
                        "/accounting/statements/balance-sheet",
                        "/accounting/statements/cash-flow",
                        "/accounting/statements/changes-in-equity",
                        "/accounting/trial-balance",
                        "/accounting/accounts/" + cash + "/ledger")) {
            var response =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), route);
            assertFalse(response.body().contains("app-page-description"), route);
            if (route.endsWith("/ledger")) {
                assertTrue(response.body().contains("fs-paper fs-trial"));
                assertTrue(response.body().contains("data-table-export-for=\"gl-account-ledger\""));
                assertTrue(response.body().contains("Running balance"));
                assertFalse(response.body().contains("data-table-selection=\"single\""));
            }
            if (route.equals("/accounting/trial-balance")) {
                assertTrue(response.body().contains("data-table-presentation=\"statement\""));
                assertTrue(response.body().contains("fs-paper fs-trial"));
                assertTrue(response.body().contains("/accounting/trial-balance/print"));
                assertTrue(response.body().contains("name=\"sort\""));
            }
        }
        for (String language : List.of("en", "sw")) {
            var print =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/trial-balance/print?size=10&page=1&lang="
                                                            + language))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, print.statusCode());
            assertTrue(print.body().contains("fs-print-document fs-print-trial"));
            assertTrue(
                    print.body()
                            .contains(
                                    language.equals("sw")
                                            ? "Mizania ya majaribio"
                                            : "Trial balance"));
            assertFalse(print.body().contains("data-resource-table"));
        }
        for (String language : List.of("en", "sw")) {
            var print =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/accounts/"
                                                            + cash
                                                            + "/ledger/print?size=10&page=1&lang="
                                                            + language))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, print.statusCode());
            assertTrue(print.body().contains("fs-print-document fs-print-trial"));
            assertTrue(
                    print.body()
                            .contains(
                                    language.equals("en")
                                            ? "Full period total"
                                            : "Jumla ya kipindi chote"));
            assertFalse(print.body().contains("data-resource-table"));
        }
        var scopedJournal =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/journals/"
                                                        + receiptId
                                                        + "?ledgerAccount="
                                                        + cash
                                                        + "&from="
                                                        + opening
                                                        + "&to="
                                                        + today
                                                        + "&lang=en"))
                                .timeout(java.time.Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, scopedJournal.statusCode());
        assertTrue(scopedJournal.body().contains("Back to ledger"));
        assertTrue(
                scopedJournal
                        .body()
                        .contains(
                                "/accounting/accounts/"
                                        + cash
                                        + "/ledger?from="
                                        + opening
                                        + "&amp;to="
                                        + today));
        assertTrue(languageForm(scopedJournal.body()).contains("name=\"ledgerAccount\" value=\"" + cash + "\""));
        var forgedJournal =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/journals/"
                                                        + receiptId
                                                        + "?ledgerAccount="
                                                        + expense
                                                        + "&from="
                                                        + opening
                                                        + "&to="
                                                        + today))
                                .timeout(java.time.Duration.ofSeconds(45))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
        assertEquals(403, forgedJournal.statusCode());
        for (String route :
                List.of(
                        "/accounting/journals/" + receiptId + "/print.pdf",
                        "/accounting/statements/balance-sheet/print.pdf",
                        "/accounting/trial-balance/print.pdf",
                        "/accounting/accounts/" + cash + "/ledger/print.pdf")) {
            var response =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode(), route);
            assertTrue(
                    new String(response.body(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                            .startsWith("%PDF-"));
        }
        verifyOperationalSourcePosting(openingSource, opening, today, cash, equity);
        actor(reviewer);
        long closedProbe =
                ledger.post(LedgerKind.INCOME, money("1.23", "Period eligibility probe " + suffix));
        assertEquals("READY", sources.find("LEDGER_INCOME", closedProbe).coverage());
        assertFalse(statements.statements(opening, today).sourceCoverage().complete());
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> AccountingHistoryFixtures.periodState(db, period, true, reviewer));
        assertEquals("OPEN", db.queryForObject("select state from gl_periods where id=?", String.class, period));
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from finance_gl_period_guards where period_id=?",
                        Integer.class,
                        period));
        verifyStatementDrilling(opening, today);
        reconcileRemainingSyntheticSources();
        actor(reviewer);
        assertTrue(statements.statements(opening, today).sourceCoverage().complete());
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        AccountingHistoryFixtures.periodState(db, period, true, reviewer));
        journals.transition(draft, 0, "cancel", "Cancel synthetic duplicate draft");
        AccountingHistoryFixtures.periodState(db, period, true, reviewer);
        assertTrue(
                db.queryForObject(
                        "select closed from finance_gl_period_guards where period_id=?",
                        Boolean.class,
                        period));
        assertThrows(
                IllegalArgumentException.class,
                () -> journals.createDraft(glVoucher("JOURNAL", cash, equity, "1.00", "FINANCING")));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> ledger.post(LedgerKind.INCOME, money("1.00", "Closed period source")));
        actor("admin@fcms.local");
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        ledger.reverse(
                                LedgerKind.INCOME,
                                closedProbe,
                                UUID.randomUUID(),
                                "Closed period reversal"));
        actor(reviewer);
        AccountingHistoryFixtures.periodState(db, period, false, reviewer);
        assertEquals("OPEN", db.queryForObject("select state from gl_periods where id=?", String.class, period));
        assertFalse(
                db.queryForObject(
                        "select closed from finance_gl_period_guards where period_id=?",
                        Boolean.class,
                        period));
        long reopenedSource =
                ledger.post(LedgerKind.INCOME, money("1.23", "Source after controlled reopening"));
        assertEquals("READY", sources.find("LEDGER_INCOME", reopenedSource).coverage());
        long reopened =
                sources.prepare(
                        "LEDGER_INCOME",
                        reopenedSource,
                        sourcePreparation(
                                sources.find("LEDGER_INCOME", reopenedSource).mappingId(),
                                "Reviewed reopened period source"));
        journals.transition(reopened, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(reopened, 1, "post", "Independent review after controlled reopening");
        actor(staff);
        assertThrows(
                AccessDeniedException.class, () -> accounting.accounts(new RegisterQuery(), null));
        assertThrows(AccessDeniedException.class, () -> statements.statements(opening, today));
    }

    @Autowired com.foresight.fcms.accounting.service.StatementDrillService statementDrill;
    @Autowired com.foresight.fcms.accounting.service.JournalDocumentService journalDocuments;
    @Autowired com.foresight.fcms.accounting.service.AccountingDocumentPdfService documentPdf;

    void verifyStatementDrilling(java.time.LocalDate from, java.time.LocalDate to)
            throws Exception {
        actor(reviewer);
        var report = statements.statements(from, to);
        var web = browser(reviewer, "Verification123!");
        for (String type :
                List.of("income-statement", "balance-sheet", "cash-flow", "changes-in-equity")) {
            var comparison = statements.comparison(type, from, to);
            assertFalse(comparison.previousAvailable(), "Fixture books begin in the current year");
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
                String params = "?from=" + from + "&to=" + to + "&lang=" + locale.getLanguage();
                var preview =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/accounting/statements/"
                                                                + type
                                                                + params))
                                        .timeout(java.time.Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, preview.statusCode(), type + " " + locale);
                assertTrue(preview.body().contains("data-fs-paper"));
                assertTrue(
                        preview.body()
                                .contains(
                                        type.equals("changes-in-equity")
                                                ? "fs-equity"
                                                : "fs-statement"));
                assertFalse(preview.body().contains("fs-help"));
                if (!type.equals("changes-in-equity")) {
                    assertTrue(
                            preview.body()
                                    .contains(
                                            locale.getLanguage().equals("sw")
                                                    ? "Haipatikani"
                                                    : "Unavailable"));
                    assertTrue(preview.body().contains("data-fs-comparison"));
                }
                var display = statementPresentation.statement(type, comparison, locale);
                for (var row : display.rows())
                    if (row.accountId() != null) {
                        assertTrue(
                                preview.body()
                                        .contains(
                                                "/accounting/accounts/"
                                                        + row.accountId()
                                                        + "/ledger"));
                    }
            }
            var export =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/statements/"
                                                            + type
                                                            + "/print.pdf?from="
                                                            + from
                                                            + "&to="
                                                            + to))
                                    .timeout(java.time.Duration.ofSeconds(45))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, export.statusCode());
            assertEquals("application/pdf", export.headers().firstValue("content-type").orElse(""));
            assertTrue(
                    export.headers().firstValue("cache-control").orElse("").contains("no-store"));
            var exportedReader = new com.lowagie.text.pdf.PdfReader(export.body());
            try {
                assertTrue(exportedReader.getNumberOfPages() >= 2);
            } finally {
                exportedReader.close();
            }
            for (var row :
                    com.foresight.fcms.accounting.service.StatementService.rows(type, report)) {
                var q = new RegisterQuery();
                q.setFrom(from);
                q.setTo(to);
                q.setSize(100);
                var breakdown = statementDrill.accounts(type, row.label(), q);
                assertEquals(
                        0, row.amount().compareTo(breakdown.total()), type + "/" + row.label());
                assertEquals(
                        0,
                        row.amount()
                                .compareTo(
                                        breakdown.records().rows().stream()
                                                .map(
                                                        com.foresight.fcms.accounting.service
                                                                        .StatementDrillService
                                                                        .AccountContribution
                                                                ::amount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add)));
                for (var account : breakdown.records().rows()) {
                    var entries = statementDrill.entries(type, row.label(), account.id(), q);
                    assertEquals(0, account.amount().compareTo(entries.account().amount()));
                    assertEquals(
                            0,
                            account.amount()
                                    .compareTo(
                                            entries.records().rows().stream()
                                                    .map(
                                                            com.foresight.fcms.accounting.service
                                                                            .StatementDrillService
                                                                            .Contribution
                                                                    ::amount)
                                                    .reduce(BigDecimal.ZERO, BigDecimal::add)));
                    assertTrue(
                            entries.records().rows().stream()
                                    .allMatch(
                                            e ->
                                                    journals.find(e.journalId())
                                                            .state()
                                                            .equals("POSTED")));
                }
                String route =
                        "/accounting/statements/"
                                + type
                                + "/lines/"
                                + row.label()
                                + "?from="
                                + from
                                + "&to="
                                + to;
                var response =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:" + port + route))
                                        .timeout(java.time.Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), route);
                assertTrue(response.body().contains("fs-paper"));
                if (!breakdown.records().rows().isEmpty()) {
                    long id = breakdown.records().rows().getFirst().id();
                    var entryPage =
                            web.send(
                                    java.net.http.HttpRequest.newBuilder(
                                                    java.net.URI.create(
                                                            "http://127.0.0.1:"
                                                                    + port
                                                                    + "/accounting/statements/"
                                                                    + type
                                                                    + "/lines/"
                                                                    + row.label()
                                                                    + "/accounts/"
                                                                    + id
                                                                    + "?from="
                                                                    + from
                                                                    + "&to="
                                                                    + to))
                                            .timeout(java.time.Duration.ofSeconds(45))
                                            .GET()
                                            .build(),
                                    java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, entryPage.statusCode());
                    assertTrue(entryPage.body().contains("/accounting/journals/"));
                    String language = languageForm(entryPage.body());
                    assertTrue(language.contains("name=\"from\" value=\"" + from + "\""));
                    assertTrue(language.contains("name=\"to\" value=\"" + to + "\""));
                }
            }
            var printable =
                    web.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create(
                                                    "http://127.0.0.1:"
                                                            + port
                                                            + "/accounting/statements/"
                                                            + type
                                                            + "/print?from="
                                                            + from
                                                            + "&to="
                                                            + to))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, printable.statusCode());
            assertFalse(printable.body().contains("fcms-sidebar"));
            assertFalse(printable.body().contains("fs-details-button"));
            assertTrue(printable.body().contains(" — "), "Statement fragment uses UTF-8");
            var pdf =
                    documentPdf.statement(
                            statementPresentation.statement(
                                    type, statements.comparison(type, from, to), Locale.ENGLISH),
                            Locale.ENGLISH);
            java.nio.file.Files.write(
                    java.nio.file.Path.of(".verification/statement-" + type + ".pdf"), pdf);
            var pdfReader = new com.lowagie.text.pdf.PdfReader(pdf);
            try {
                assertTrue(pdfReader.getNumberOfPages() > 0);
                String content =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(pdfReader)
                                .getTextFromPage(1);
                assertTrue(content.contains("FCMS"));
                assertTrue(content.contains("Draft"));
            } finally {
                pdfReader.close();
            }
        }
        var q = new RegisterQuery();
        q.setFrom(from);
        q.setTo(to);
        q.setSize(10);
        q.setSearch("No match " + suffix);
        var filtered = statementDrill.accounts("balance-sheet", "gl.class.ASSET", q);
        assertTrue(filtered.records().rows().isEmpty());
        assertEquals(0, filtered.filteredTotal().signum());
        assertEquals(0, report.assets().compareTo(filtered.total()));
        assertThrows(
                IllegalArgumentException.class,
                () -> statementDrill.accounts("balance-sheet", "untrusted sql", q));
        assertThrows(
                IllegalArgumentException.class,
                () -> statementDrill.accounts("cash-flow", "gl.income", q));
        var jq = new RegisterQuery();
        jq.setSize(100);
        var receipt =
                journals.list(jq, "RECEIPT").rows().stream()
                        .filter(j -> j.state().equals("POSTED"))
                        .findFirst()
                        .orElseThrow();
        var doc = journalDocuments.document(receipt.id(), Locale.ENGLISH);
        assertNotNull(doc.postedAt());
        assertEquals(
                0,
                doc.amount()
                        .compareTo(
                                doc.receiptItems().stream()
                                        .map(
                                                com.foresight.fcms.accounting.service
                                                                .JournalDocumentService.ReceiptItem
                                                        ::amount)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertFalse(doc.moneyAccounts().isEmpty());
        var voucherPage =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/journals/"
                                                        + receipt.id()))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, voucherPage.statusCode());
        assertTrue(voucherPage.body().contains(" · "), "Voucher fragment uses UTF-8");
        assertTrue(doc.words().contains("Tanzanian"));
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var bytes =
                    documentPdf.voucher(journalDocuments.document(receipt.id(), locale), locale);
            java.nio.file.Files.write(
                    java.nio.file.Path.of(
                            ".verification/voucher-receipt-" + locale.getLanguage() + ".pdf"),
                    bytes);
            var reader = new com.lowagie.text.pdf.PdfReader(bytes);
            try {
                String text =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
                assertTrue(text.contains("FCMS"));
                assertTrue(text.contains(receipt.evidenceReference()));
                assertFalse(text.contains("(TZS) (TZS)"));
                assertTrue(
                        text.toUpperCase(locale)
                                .contains(
                                        (locale.getLanguage().equals("sw")
                                                        ? "Kiasi kwa maneno"
                                                        : "Amount in words")
                                                .toUpperCase(locale)));
            } finally {
                reader.close();
            }
        }
        var payment =
                journals.list(jq, "PAYMENT").rows().stream()
                        .filter(j -> j.state().equals("POSTED"))
                        .findFirst()
                        .orElseThrow();
        var paymentDocument = journalDocuments.document(payment.id(), Locale.ENGLISH);
        assertTrue(paymentDocument.amount().signum() > 0);
        java.nio.file.Files.write(
                java.nio.file.Path.of(".verification/voucher-payment-en.pdf"),
                documentPdf.voucher(paymentDocument, Locale.ENGLISH));
        var journalExports = new ArrayList<Long>();
        for (String kind : List.of("JOURNAL", "OPENING", "REVERSAL")) {
            var matches =
                    journals.list(jq, kind).rows().stream()
                            .filter(j -> j.state().equals("POSTED"))
                            .toList();
            if (kind.equals("OPENING")
                    && !accounting.books().orElseThrow().openingMode().equals("JOURNAL")) {
                assertTrue(matches.isEmpty());
                continue;
            }
            assertFalse(matches.isEmpty(), kind + " fixture must exist");
            var journal = matches.getFirst();
            journalExports.add(journal.id());
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
                var response =
                        web.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/accounting/journals/"
                                                                + journal.id()
                                                                + "/print.pdf?lang="
                                                                + locale.getLanguage()))
                                        .timeout(java.time.Duration.ofSeconds(45))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode());
                assertEquals(
                        "application/pdf",
                        response.headers().firstValue("content-type").orElse(""));
                assertTrue(
                        response.headers()
                                .firstValue("cache-control")
                                .orElse("")
                                .contains("no-store"));
                assertTrue(
                        response.headers()
                                .firstValue("content-disposition")
                                .orElse("")
                                .contains(
                                        "fcms-"
                                                + kind.toLowerCase(Locale.ROOT)
                                                + "-"
                                                + journal.id()
                                                + ".pdf"));
                var pdf = new com.lowagie.text.pdf.PdfReader(response.body());
                try {
                    var content = new StringBuilder();
                    var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(pdf);
                    for (int n = 1; n <= pdf.getNumberOfPages(); n++) {
                        String page = extractor.getTextFromPage(n);
                        assertTrue(page.contains(n + " / " + pdf.getNumberOfPages()));
                        content.append(page);
                    }
                    assertTrue(content.toString().contains(journal.evidenceReference()));
                    assertTrue(content.toString().contains(journal.maker()));
                    assertTrue(content.toString().contains(journal.checker()));
                    assertTrue(content.toString().contains("EAT"));
                    for (var line : journal.lines())
                        assertTrue(content.toString().contains(line.accountCode()));
                    if (journal.reversesId() != null)
                        assertTrue(content.toString().contains("#" + journal.reversesId()));
                    assertFalse(content.toString().contains("null"));
                } finally {
                    pdf.close();
                }
            }
        }
        assertEquals(
                "one Tanzanian shilling and one cent",
                com.foresight.fcms.accounting.view.AmountInWords.amountInWords(
                        new BigDecimal("1.01"), Locale.ENGLISH));
        assertEquals(
                "Shilingi za Tanzania moja na senti moja",
                com.foresight.fcms.accounting.view.AmountInWords.amountInWords(
                        new BigDecimal("1.01"), Locale.forLanguageTag("sw")));
        actor(staff);
        assertThrows(
                AccessDeniedException.class,
                () -> statementDrill.accounts("income-statement", "gl.income", q));
        var denied = browser(staff, "Verification123!");
        for (long journalId : journalExports)
            assertEquals(
                    403,
                    denied.send(
                                    java.net.http.HttpRequest.newBuilder(
                                                    java.net.URI.create(
                                                            "http://127.0.0.1:"
                                                                    + port
                                                                    + "/accounting/journals/"
                                                                    + journalId
                                                                    + "/print.pdf"))
                                            .timeout(java.time.Duration.ofSeconds(45))
                                            .GET()
                                            .build(),
                                    java.net.http.HttpResponse.BodyHandlers.ofString())
                            .statusCode());
        assertEquals(
                403,
                denied.send(
                                java.net.http.HttpRequest.newBuilder(
                                                java.net.URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/accounting/statements/income-statement/lines/gl.income"))
                                        .GET()
                                        .build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString())
                        .statusCode());
    }

    @Autowired com.foresight.fcms.accounting.service.SourceCoverageService sourceCoverage;

    void reconcileRemainingSyntheticSources() {
        String maker = "reconcile-maker-" + suffix + "@test.invalid",
                reverser = "reconcile-reverser-" + suffix + "@test.invalid",
                checker = "reconcile-checker-" + suffix + "@test.invalid";
        user(maker, 1);
        user(reverser, 1);
        user(checker, 1);
        for (int iteration = 0; iteration < 10; iteration++) {
            actor(maker);
            var query = new RegisterQuery();
            query.setSize(100);
            query.setSort("id");
            query.setDirection("asc");
            var rows = sources.list(query).rows();
            boolean progress = false;
            for (var item : rows) {
                if (!item.coverage().equals("READY")) continue;
                actor(item.source().reversesId() == null ? maker : reverser);
                long journal =
                        sources.prepare(
                                item.source().kind(),
                                item.source().id(),
                                sourcePreparation(
                                        item.mappingId(),
                                        "Synthetic source reconciliation "
                                                + item.source().kind()
                                                + "/"
                                                + item.source().id()));
                journals.transition(journal, 0, "submit", "");
                actor(checker);
                journals.transition(
                        journal, 1, "post", "Independent synthetic source reconciliation");
                progress = true;
            }
            actor(maker);
            if (sourceCoverage
                    .through(today().withMonth(12).withDayOfMonth(31))
                    .complete()) return;
            assertTrue(
                    progress,
                    "Remaining synthetic source must have reviewed mapping and original evidence");
        }
        fail("Synthetic source reconciliation exceeded its bounded passes");
    }

    @Autowired com.foresight.fcms.accounting.service.SourcePostingService sources;
    @Autowired com.foresight.fcms.accounting.service.SourceBindingService sourceBindings;

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    void verifyOperationalSourcePosting(
            Long openingSource,
            java.time.LocalDate opening,
            java.time.LocalDate today,
            long openingCash,
            long openingEquity)
            throws Exception {
        actor(reviewer);
        assertNotNull(openingSource, "Use a fresh isolated accounting verification database");
        var before = sources.list(new RegisterQuery());
        assertTrue(before.rows().size() <= 25);
        long bank = glAccount("141001", "Source bank", "ASSET", true);
        long revenue = glAccount("541001", "Source revenue", "INCOME", false);
        long revisedRevenue = glAccount("541002", "Source revised revenue", "INCOME", false);
        var m = postingMapping(bank, revenue, "Source policy " + suffix);
        long mapping = mappings.create(m);
        actor("admin@fcms.local");
        mappings.review(mapping, 0, "approve", "Reviewed source policy");
        actor(reviewer);
        var baseline = statements.statements(opening, today);
        long sourceId = ledger.post(LedgerKind.INCOME, money("7.15", "Source receipt " + suffix));
        assertEquals("READY", sources.find("LEDGER_INCOME", sourceId).coverage());
        var browser = browser(reviewer, "Verification123!");
        var page =
                browser.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/sources/LEDGER_INCOME/"
                                                        + sourceId))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Source receipt " + suffix));
        var f = sourcePreparation(mapping, "Source evidence " + suffix);
        String input =
                "_csrf="
                        + csrfToken(page.body())
                        + "&mappingId="
                        + mapping
                        + "&evidenceReference="
                        + java.net.URLEncoder.encode(
                                f.getEvidenceReference(), java.nio.charset.StandardCharsets.UTF_8)
                        + "&requestKey="
                        + f.getRequestKey()
                        + "&amount=99999&date=1900-01-01";
        var prepared =
                browser.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/accounting/sources/LEDGER_INCOME/"
                                                        + sourceId
                                                        + "/prepare"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, prepared.statusCode());
        long abandoned =
                sourceBindings.current("LEDGER_INCOME", sourceId).orElseThrow().journalId();
        assertTrue(journals.find(abandoned).sourceLinked());
        assertEquals(today, journals.find(abandoned).date());
        var actualSource = sources.find("LEDGER_INCOME", sourceId).source();
        var sourceVoucher = journals.find(abandoned);
        assertEquals(actualSource.reference(), sourceVoucher.reference());
        assertEquals(actualSource.description(), sourceVoucher.description());
        assertTrue(
                sourceVoucher.lines().stream()
                        .allMatch(line -> line.description().equals(actualSource.description())));
        assertEquals(0, new BigDecimal("7.15").compareTo(journals.find(abandoned).total()));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_lines set debit=100 where journal_id=? and debit>0",
                                abandoned));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_journals set evidence_reference='tampered' where id=?",
                                abandoned));
        journals.transition(abandoned, 0, "cancel", "Superseded preparation evidence");
        f.setRequestKey(UUID.randomUUID());
        long journal = sources.prepare("LEDGER_INCOME", sourceId, f);
        assertNotEquals(abandoned, journal);
        assertEquals(
                2,
                sourceBindings
                        .history("LEDGER_INCOME", sourceId, new RegisterQuery())
                        .rows()
                        .size());
        assertEquals(journal, sources.prepare("LEDGER_INCOME", sourceId, f));
        f.setEvidenceReference("Changed evidence");
        assertThrows(
                IllegalArgumentException.class,
                () -> sources.prepare("LEDGER_INCOME", sourceId, f));
        f.setEvidenceReference("Source evidence " + suffix);
        var wrongAmount = glVoucher("RECEIPT", bank, revenue, "7.16", "OPERATING");
        long wrongVoucher = journals.createDraft(wrongAmount);
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "insert into"
                                    + " gl_source_links(source_kind,source_id,journal_id,evidence_reference,linked_by,mapping_id,source_date,signed_amount)"
                                    + " select"
                                    + " s.source_kind,s.source_id,j.id,j.evidence_reference,j.maker_id,?,s.source_date,s.signed_amount"
                                    + " from gl_journals j cross join finance_gl_sources s where"
                                    + " j.id=? and s.source_kind='LEDGER_INCOME' and s.source_id=?",
                                mapping,
                                wrongVoucher,
                                sourceId));
        assertTrue(sourceBindings.forJournal(wrongVoucher).isEmpty());
        journals.transition(wrongVoucher, 0, "cancel", "Reject mismatched source preparation");
        long nextMapping =
                mappings.create(
                        postingMapping(
                                bank, revisedRevenue, "Replacement source policy " + suffix));
        actor("admin@fcms.local");
        mappings.review(nextMapping, 0, "approve", "Reviewed replacement source policy");
        actor(reviewer);
        assertEquals(
                mapping,
                sources.workspace("LEDGER_INCOME", sourceId, new RegisterQuery()).mapping().id());
        journals.transition(journal, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(journal, 1, "post", "Reviewed immutable source and mapping revision");
        assertEquals("POSTED", sources.find("LEDGER_INCOME", sourceId).coverage());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        journals.reverse(
                                journal,
                                UUID.randomUUID(),
                                today,
                                "Unlinked correction forbidden"));
        long reversedSource =
                ledger.reverse(
                        LedgerKind.INCOME, sourceId, UUID.randomUUID(), "Actual source correction");
        var reverseForm = sourcePreparation(mapping, "Actual reversal evidence");
        long reversal = sources.prepare("LEDGER_INCOME", reversedSource, reverseForm);
        assertEquals(journal, journals.find(reversal).reversesId());
        assertEquals("REVERSAL", journals.find(reversal).kind());
        assertEquals(reversal, sources.prepare("LEDGER_INCOME", reversedSource, reverseForm));
        journals.transition(reversal, 0, "submit", "");
        actor(reviewer);
        assertFalse(sourceBindings.canReview(reversal));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        journals.transition(
                                reversal, 1, "post", "Original source maker cannot check"));
        var checker = "source-checker-" + suffix + "@test.invalid";
        user(checker, 1);
        actor(checker);
        journals.transition(reversal, 1, "post", "Independent source reversal review");
        assertEquals("POSTED", sources.find("LEDGER_INCOME", reversedSource).coverage());
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from gl_source_links where journal_id=?", journal));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("delete from gl_source_reservations where journal_id=?", journal));
        var originalLines = journals.find(journal).lines();
        var reversalLines = journals.find(reversal).lines();
        for (int i = 0; i < originalLines.size(); i++) {
            assertEquals(originalLines.get(i).accountId(), reversalLines.get(i).accountId());
            assertEquals(originalLines.get(i).debit(), reversalLines.get(i).credit());
            assertEquals(originalLines.get(i).credit(), reversalLines.get(i).debit());
        }

        actor(reviewer);
        long racedSource =
                ledger.post(LedgerKind.INCOME, money("2.10", "Concurrent source " + suffix));
        var concurrentForm = sourcePreparation(nextMapping, "Concurrent preparation");
        var pool = Executors.newFixedThreadPool(2);
        long raceJournal;
        try {
            var jobs = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 2; i++)
                jobs.add(
                        () -> {
                            actor(reviewer);
                            try {
                                return sources.prepare(
                                        "LEDGER_INCOME", racedSource, concurrentForm);
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        });
            var results = pool.invokeAll(jobs);
            raceJournal = results.get(0).get(15, TimeUnit.SECONDS);
            assertEquals(raceJournal, results.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        actor(reviewer);
        assertEquals(
                1,
                sourceBindings
                        .history("LEDGER_INCOME", racedSource, new RegisterQuery())
                        .rows()
                        .size());
        journals.transition(raceJournal, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(raceJournal, 1, "post", "Reviewed concurrent preparation");
        long raceReversalSource =
                ledger.reverse(
                        LedgerKind.INCOME,
                        racedSource,
                        UUID.randomUUID(),
                        "Concurrent receipt correction");
        long raceReversal =
                sources.prepare(
                        "LEDGER_INCOME",
                        raceReversalSource,
                        sourcePreparation(nextMapping, "Concurrent receipt reversal"));
        journals.transition(raceReversal, 0, "submit", "");
        actor(checker);
        journals.transition(raceReversal, 1, "post", "Independent race correction review");

        actor(reviewer);
        long earlyOriginal =
                ledger.post(LedgerKind.INCOME, money("3.30", "Reversed before GL " + suffix));
        actor("admin@fcms.local");
        long earlyReversal =
                ledger.reverse(
                        LedgerKind.INCOME,
                        earlyOriginal,
                        UUID.randomUUID(),
                        "Source reversed before preparation");
        assertEquals("WAITING_ORIGINAL", sources.find("LEDGER_INCOME", earlyReversal).coverage());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        sources.prepare(
                                "LEDGER_INCOME",
                                earlyReversal,
                                sourcePreparation(nextMapping, "Too early")));
        actor(reviewer);
        long historical =
                sources.prepare(
                        "LEDGER_INCOME",
                        earlyOriginal,
                        sourcePreparation(nextMapping, "Original historical source"));
        journals.transition(historical, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(historical, 1, "post", "Review retained original event");
        long historicalReversal =
                sources.prepare(
                        "LEDGER_INCOME",
                        earlyReversal,
                        sourcePreparation(nextMapping, "Historical reversal evidence"));
        journals.transition(historicalReversal, 0, "submit", "");
        actor(checker);
        journals.transition(historicalReversal, 1, "post", "Review actual historical reversal");
        var net = statements.statements(opening, today);
        assertEquals(0, net.profit().compareTo(baseline.profit()));
        assertEquals(0, net.cashClosing().compareTo(baseline.cashClosing()));

        actor(reviewer);
        assertEquals("OPENING_COVERED", sources.find("LEDGER_INCOME", openingSource).coverage());
        var late = money("1.00", "Late pre-opening entry");
        late.setDate(opening.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> ledger.post(LedgerKind.INCOME, late));
        actor("admin@fcms.local");
        long openingReversal =
                ledger.reverse(
                        LedgerKind.INCOME,
                        openingSource,
                        UUID.randomUUID(),
                        "Opening-covered source correction");
        assertEquals("UNMAPPED", sources.find("LEDGER_INCOME", openingReversal).coverage());
        actor(reviewer);
        var openingMap =
                postingMapping(
                        openingEquity, openingCash, "Explicit opening correction policy " + suffix);
        openingMap.setEvent("INCOME_OPENING_REVERSAL");
        openingMap.setVoucherKind("PAYMENT");
        long openingMapping = mappings.create(openingMap);
        actor("admin@fcms.local");
        mappings.review(openingMapping, 0, "approve", "Reviewed opening correction treatment");
        long openingCorrection =
                sources.prepare(
                        "LEDGER_INCOME",
                        openingReversal,
                        sourcePreparation(openingMapping, "Reviewed opening source correction"));
        assertNull(journals.find(openingCorrection).reversesId());
        assertEquals("PAYMENT", journals.find(openingCorrection).kind());
        journals.transition(openingCorrection, 0, "submit", "");
        actor(checker);
        journals.transition(openingCorrection, 1, "post", "Independent opening correction review");
        var corrected = statements.statements(opening, today);
        assertEquals(0, corrected.profit().compareTo(baseline.profit()));
        assertEquals(
                0,
                corrected
                        .cashClosing()
                        .subtract(baseline.cashClosing())
                        .compareTo(new BigDecimal("-13.25")));
        assertTrue(corrected.balanceSheetMatches());
        assertTrue(corrected.cashFlowMatches());
        assertTrue(corrected.equityMatches());
        verifyOtherOperationalSourceKinds(bank, revenue, checker);
        verifyPreparationRecoversFromChangedMapping(bank, revisedRevenue, nextMapping);
        for (String route :
                List.of(
                        "/accounting/sources",
                        "/accounting/sources?lang=sw",
                        "/accounting/sources/LEDGER_INCOME/" + sourceId,
                        "/accounting/sources/LEDGER_INCOME/" + reversedSource + "?lang=sw",
                        "/accounting/journals/" + journal)) {
            var response =
                    browser.send(
                            java.net.http.HttpRequest.newBuilder(
                                            java.net.URI.create("http://127.0.0.1:" + port + route))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), route);
            assertFalse(response.body().contains("app-page-description"));
        }
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> sources.list(new RegisterQuery()));
    }

    void verifyPreparationRecoversFromChangedMapping(long bank, long revenue, long staleMapping)
            throws Exception {
        actor(reviewer);
        long source =
                ledger.post(LedgerKind.INCOME, money("2.40", "Stale mapping source " + suffix));
        var web = browser(reviewer, "Verification123!");
        String route = "http://127.0.0.1:" + port + "/accounting/sources/LEDGER_INCOME/" + source;
        var page =
                web.send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(route))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        long current = reviewedSourceMapping("INCOME_IN", bank, revenue, "RECEIPT", "OPERATING");
        String evidence = "Preserved mapping evidence";
        var oldKey = UUID.randomUUID();
        String body =
                "_csrf="
                        + csrfToken(page.body())
                        + "&mappingId="
                        + staleMapping
                        + "&evidenceReference=Preserved+mapping+evidence&requestKey="
                        + oldKey;
        var failed =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(route + "/prepare"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, failed.statusCode());
        assertTrue(failed.body().contains(evidence));
        assertTrue(failed.body().contains("Transaction template changed"));
        assertTrue(failed.body().contains("<option value=\"" + current + "\""));
        var nonce =
                java.util.regex.Pattern.compile("name=\"requestKey\" value=\"([^\"]+)\"")
                        .matcher(failed.body());
        assertTrue(nonce.find());
        assertNotEquals(oldKey.toString(), nonce.group(1));
        actor(reviewer);
        assertTrue(sourceBindings.current("LEDGER_INCOME", source).isEmpty());
        String retry =
                "_csrf="
                        + csrfToken(failed.body())
                        + "&mappingId="
                        + current
                        + "&evidenceReference=Preserved+mapping+evidence&requestKey="
                        + nonce.group(1);
        var completed =
                web.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(route + "/prepare"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(retry))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, completed.statusCode());
        long journal = sourceBindings.current("LEDGER_INCOME", source).orElseThrow().journalId();
        journals.transition(journal, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(
                journal, 1, "post", "Reviewed refreshed mapping and preserved evidence");
        assertEquals(current, sourceBindings.forJournal(journal).orElseThrow().mappingId());
    }

    void verifyOtherOperationalSourceKinds(long bank, long revenue, String checker) {
        actor(reviewer);
        long petty = glAccount("151001", "Source petty cash", "ASSET", true);
        long expense = glAccount("451001", "Source operating expense", "EXPENSE", false);
        var cases =
                List.of(
                        new SourceCase(
                                LedgerKind.PETTY,
                                "PETTY_IN",
                                "IN",
                                "50.00",
                                0,
                                "IN",
                                petty,
                                bank,
                                "JOURNAL",
                                "INTERNAL"),
                        new SourceCase(
                                LedgerKind.PETTY,
                                "PETTY_OUT",
                                "OUT",
                                "2.25",
                                0,
                                "IN",
                                expense,
                                petty,
                                "PAYMENT",
                                "OPERATING"),
                        new SourceCase(
                                LedgerKind.CLOUD,
                                "CLOUD_IN",
                                "IN",
                                "5.50",
                                0,
                                "IN",
                                bank,
                                revenue,
                                "RECEIPT",
                                "OPERATING"),
                        new SourceCase(
                                LedgerKind.CLOUD,
                                "CLOUD_OUT",
                                "OUT",
                                "1.10",
                                0,
                                "IN",
                                expense,
                                bank,
                                "PAYMENT",
                                "OPERATING"),
                        new SourceCase(
                                LedgerKind.BULK,
                                "BULK_OUT",
                                "OUT",
                                "3.00",
                                100,
                                "IN",
                                expense,
                                bank,
                                "PAYMENT",
                                "OPERATING"),
                        new SourceCase(
                                LedgerKind.BULK,
                                "BULK_IN",
                                "IN",
                                "4.20",
                                20,
                                "OUT",
                                bank,
                                revenue,
                                "RECEIPT",
                                "OPERATING"));
        for (var c : cases) {
            actor(reviewer);
            var f = money(c.amount(), "Source " + c.event() + " " + suffix);
            f.setDirection(c.direction());
            f.setUnits(c.units());
            f.setUnitDirection(c.unitDirection());
            long source = ledger.post(c.kind(), f);
            long mapping =
                    reviewedSourceMapping(c.event(), c.debit(), c.credit(), c.voucher(), c.flow());
            actor(reviewer);
            long voucher =
                    sources.prepare(
                            "LEDGER_" + c.kind(),
                            source,
                            sourcePreparation(mapping, "Evidence " + c.event()));
            journals.transition(voucher, 0, "submit", "");
            actor("admin@fcms.local");
            journals.transition(voucher, 1, "post", "Reviewed " + c.event());
            assertEquals("POSTED", sources.find("LEDGER_" + c.kind(), source).coverage());
            assertEquals(0, new BigDecimal(c.amount()).compareTo(journals.find(voucher).total()));
            assertEquals(c.flow(), journals.find(voucher).cashFlow());
        }
        actor(staff);
        var expenseForm = new ExpenseForm();
        expenseForm.setCategory("Office");
        expenseForm.setDescription("Source expense " + suffix);
        expenseForm.setPayee("Synthetic supplier");
        expenseForm.setAmount(new BigDecimal("4.40"));
        long expenseId = expenses.save(null, expenseForm);
        expenses.transition(expenseId, 0, "submit", "", "", UUID.randomUUID());
        actor(reviewer);
        expenses.transition(
                expenseId, 1, "approve", "Reviewed source expense", "", UUID.randomUUID());
        actor("admin@fcms.local");
        expenses.transition(expenseId, 2, "pay", "", "SOURCE-PAY-" + suffix, UUID.randomUUID());
        long mapping =
                reviewedSourceMapping("EXPENSE_PAYMENT", expense, bank, "PAYMENT", "OPERATING");
        actor(reviewer);
        long voucher =
                sources.prepare(
                        "EXPENSE",
                        expenseId,
                        sourcePreparation(mapping, "Expense payment evidence"));
        journals.transition(voucher, 0, "submit", "");
        actor("admin@fcms.local");
        assertFalse(sourceBindings.canReview(voucher));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        journals.transition(
                                voucher, 1, "post", "Payer cannot check their own payment"));
        actor(checker);
        journals.transition(voucher, 1, "post", "Independent expense payment review");
        assertEquals("POSTED", sources.find("EXPENSE", expenseId).coverage());
        actor("admin@fcms.local");
        expenses.transition(
                expenseId, 3, "reverse", "Actual paid expense correction", "", UUID.randomUUID());
        long reversalSource =
                db.queryForObject(
                        "select id from expense_reversals where expense_id=?",
                        Long.class,
                        expenseId);
        long reversal =
                sources.prepare(
                        "EXPENSE_REVERSAL",
                        reversalSource,
                        sourcePreparation(mapping, "Expense reversal evidence"));
        journals.transition(reversal, 0, "submit", "");
        actor(checker);
        journals.transition(reversal, 1, "post", "Independent expense reversal review");
        assertEquals(voucher, journals.find(reversal).reversesId());
        assertEquals("POSTED", sources.find("EXPENSE_REVERSAL", reversalSource).coverage());
        var statement =
                statements.statements(
                        today().withDayOfYear(1), today());
        assertTrue(statement.balanceSheetMatches());
        assertTrue(statement.cashFlowMatches());
        assertTrue(statement.equityMatches());
    }

    long reviewedSourceMapping(String event, long debit, long credit, String kind, String flow) {
        actor(reviewer);
        var f = postingMapping(debit, credit, "Reviewed source policy " + event + " " + suffix);
        f.setEvent(event);
        f.setVoucherKind(kind);
        f.setCashFlow(flow);
        long id = mappings.create(f);
        actor("admin@fcms.local");
        mappings.review(id, 0, "approve", "Reviewed source account treatment");
        return id;
    }

    record SourceCase(
            LedgerKind kind,
            String event,
            String direction,
            String amount,
            long units,
            String unitDirection,
            long debit,
            long credit,
            String voucher,
            String flow) {}

    com.foresight.fcms.accounting.dto.SourcePreparationForm sourcePreparation(
            long mapping, String evidence) {
        var f = new com.foresight.fcms.accounting.dto.SourcePreparationForm();
        f.setMappingId(mapping);
        f.setEvidenceReference(evidence);
        return f;
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

    com.foresight.fcms.accounting.dto.JournalForm glVoucher(
            String kind, long debit, long credit, String amount, String cashFlow) {
        var f = new com.foresight.fcms.accounting.dto.JournalForm();
        f.setKind(kind);
        f.setReference(UUID.randomUUID().toString());
        f.setDescription("Synthetic accounting transaction");
        f.setEvidenceReference("Verification " + suffix);
        f.setCashFlow(cashFlow);
        f.getLines().get(0).setAccountId(debit);
        f.getLines().get(0).setDebit(new BigDecimal(amount));
        f.getLines().get(1).setAccountId(credit);
        f.getLines().get(1).setCredit(new BigDecimal(amount));
        return f;
    }

    long glPost(com.foresight.fcms.accounting.dto.JournalForm f) {
        actor(reviewer);
        long id = journals.createDraft(f);
        journals.transition(id, 0, "submit", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> journals.transition(id, 1, "post", "Self checking forbidden"));
        actor("admin@fcms.local");
        journals.transition(id, 1, "post", "Reviewed synthetic evidence");
        return id;
    }

    LedgerForm money(String amount, String description) {
        var f = new LedgerForm();
        f.setDescription(description);
        f.setCategory("Test");
        f.setReference(UUID.randomUUID().toString());
        f.setAmount(new BigDecimal(amount));
        return f;
    }
    private String languageForm(String html) {
        var form = java.util.regex.Pattern.compile("<form[^>]*data-shell-language[^>]*>(.*?)</form>", java.util.regex.Pattern.DOTALL).matcher(html);
        assertTrue(form.find());
        String body = form.group(1);
        assertTrue(body.contains("id=\"lang\" name=\"lang\""));
        assertTrue(body.contains("value=\"en\""));
        assertTrue(body.contains("value=\"sw\""));
        return body;
    }

}
