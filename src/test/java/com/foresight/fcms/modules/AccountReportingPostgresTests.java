package com.foresight.fcms.modules;

import static com.foresight.fcms.shared.time.ApplicationCalendar.today;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.*;
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
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

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
class AccountReportingPostgresTests {
    static final String SCHEMA = "reporting_notes_" + UUID.randomUUID().toString().replace("-", "");
    static final String LONG_NOTE =
            "Equipment & recorded costs <script>literal</script>.\n"
                    + "Long financial note ".repeat(70);
    static final Path PROOF = Path.of(".verification/reporting-notes-20261113");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("fcms.storage.avatar-dir", () -> ".verification/avatars");
        p.add("fcms.storage.contract-dir", () -> ".verification/contracts-tested");
    }

    @Autowired JdbcTemplate db;
    @Autowired AccountingService accounts;
    @Autowired AccountReportingService settings;
    @Autowired StatementService statements;
    @Autowired StatementPresentation presentation;
    @Autowired StatementDrillService drill;
    @Autowired JournalService journals;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    final String maker = "reporting-maker@test.invalid",
            reader = "reporting-reader@test.invalid",
            staff = "reporting-staff@test.invalid",
            reviewer = "reporting-reviewer@test.invalid";
    final LocalDate today = today(),
            start = today.withDayOfYear(1),
            opening = start.minusYears(2);
    long makerId,
            readerId,
            reviewerId,
            cash,
            contra,
            equipment,
            income,
            expense,
            equity,
            liability,
            cashGroup,
            equipmentGroup,
            liabilityGroup;
    String financialBefore;

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
                        + " ('Reporting','Fixture',?,?,1,?) returning id",
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
            var prior =
                    db.queryForList("select id from gl_accounts where code=?", Long.class, next);
            if (!prior.isEmpty()) {
                parent = prior.getFirst();
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

    void post(String kind, long debit, long credit, String amount, String flow, LocalDate date) {
        actor(maker);
        var f = new JournalForm();
        f.setKind(kind);
        f.setDate(date);
        f.setReference(UUID.randomUUID().toString());
        f.setDescription("Reporting fixture");
        f.setEvidenceReference("Reviewed reporting evidence");
        f.setCashFlow(flow);
        f.getLines().get(0).setAccountId(debit);
        f.getLines().get(0).setDebit(new BigDecimal(amount));
        f.getLines().get(1).setAccountId(credit);
        f.getLines().get(1).setCredit(new BigDecimal(amount));
        long id = journals.save(null, f);
        journals.transition(id, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(id, 1, "post", "Independent reporting fixture review");
    }

    @BeforeAll
    void fixtures() throws Exception {
        makerId = user(maker, 1);
        readerId = user(reader, 2);
        user(staff, 2);
        reviewerId = user(reviewer, 1);
        db.update(
                "insert into user_claims(user_id,claim) values"
                        + " (?,'accounting.read'),(?,'accounting.report')",
                readerId,
                readerId);
        actor(maker);
        var book = new BookForm();
        book.setOpeningDate(opening);
        book.setOpeningMode("ZERO");
        book.setPolicyReference("Synthetic reporting cutover");
        accounts.configure(book);
        actor("admin@fcms.local");
        accounts.approveBooks(0, "Independent reporting book review");
        for (int year = opening.getYear(); year <= today.getYear(); year++) {
            var p = new PeriodForm();
            p.setName("Reporting " + year);
            p.setStartsOn(LocalDate.of(year, 1, 1));
            p.setEndsOn(LocalDate.of(year, 12, 31));
            accounts.createPeriod(p);
        }
        actor(maker);
        cash = account("111001", "Bank", "ASSET", true);
        contra = account("111002", "Contra reserve", "ASSET", false);
        equipment = account("121001", "Equipment", "ASSET", false);
        income = account("511001", "Revenue", "INCOME", false);
        expense = account("411001", "Expense", "EXPENSE", false);
        equity = account("311001", "Owner equity", "EQUITY", false);
        liability = account("211001", "Payable", "LIABILITY", false);
        cashGroup = db.queryForObject("select id from gl_accounts where code='110000'", Long.class);
        equipmentGroup =
                db.queryForObject("select id from gl_accounts where code='120000'", Long.class);
        liabilityGroup =
                db.queryForObject("select id from gl_accounts where code='210000'", Long.class);
        post("JOURNAL", cash, equity, "500.00", "FINANCING", today.minusYears(1));
        post("RECEIPT", cash, income, "100.00", "OPERATING", today.minusYears(1));
        post("PAYMENT", expense, cash, "20.00", "OPERATING", today.minusYears(1));
        post("JOURNAL", cash, equity, "50.00", "FINANCING", today);
        post("RECEIPT", cash, income, "200.00", "OPERATING", today);
        post("PAYMENT", expense, cash, "30.00", "OPERATING", today);
        post("JOURNAL", equipment, cash, "40.00", "INVESTING", today);
        post("JOURNAL", cash, liability, "70.00", "FINANCING", today);
        post("JOURNAL", expense, contra, "50.00", "NONE", today);
        financialBefore = financialDigest();
        Files.createDirectories(PROOF);
        Files.writeString(PROOF.resolve("fixture-schema.txt"), SCHEMA);
        Files.writeString(PROOF.resolve("fixture-today.txt"), today.toString());
        Files.writeString(PROOF.resolve("financial-before.txt"), financialBefore);
    }

    String financialDigest() {
        return db.queryForObject(
                "select md5((select coalesce(string_agg(row_to_json(j)::text,'' order by j.id),'')"
                    + " from gl_journals j)||(select coalesce(string_agg(row_to_json(l)::text,''"
                    + " order by l.id),'') from gl_lines l))",
                String.class);
    }

    RegisterQuery query() {
        var q = new RegisterQuery();
        q.setSize(10);
        return q;
    }

    ReportingForm form(long id, String section, Integer note, String text) {
        actor(maker);
        var f = settings.setup(id, null).form();
        f.setSection(section);
        f.setNoteNumber(note);
        f.setNoteText(text);
        f.setReason("Reporting evidence " + UUID.randomUUID());
        return f;
    }

    ReportingDecisionForm decision(long version, String action) {
        var f = new ReportingDecisionForm();
        f.setVersion(version);
        f.setAction(action);
        f.setReason("Independent reporting decision");
        return f;
    }

    long approve(long id, String section, Integer note, String text) {
        actor(maker);
        long v = settings.propose(id, form(id, section, note, text));
        actor("admin@fcms.local");
        settings.decide(id, v, decision(0, "approve"));
        return v;
    }

    AccountReporting.Mapping mapping(long account) {
        return statements.comparison("notes", start, today).accounts().stream()
                .filter(a -> a.current().id() == account)
                .findFirst()
                .orElseThrow()
                .mapping();
    }

    void amount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage();
    }

    @Test
    @Order(1)
    void migrationNeverInfersReportingSectionsOrNotes() {
        var r = statements.comparison("balance-sheet", start, today);
        amount("820.00", r.current().assets());
        amount("70.00", r.current().liabilities());
        amount("750.00", r.current().totalEquity());
        assertTrue(r.current().balanceSheetMatches());
        assertTrue(r.current().cashFlowMatches());
        assertTrue(r.current().equityMatches());
        assertEquals(4, r.unclassifiedCount());
        assertTrue(
                r.accounts().stream()
                        .filter(
                                a ->
                                        Set.of("ASSET", "LIABILITY")
                                                .contains(a.current().classification()))
                        .allMatch(a -> a.mapping().section().equals("UNCLASSIFIED")));
        assertEquals(
                0L, db.queryForObject("select count(*) from gl_account_reporting", Long.class));
        assertTrue(
                presentation
                        .statement(
                                "notes",
                                statements.comparison("notes", start, today),
                                Locale.ENGLISH)
                        .rows()
                        .isEmpty());
        var paper = presentation.statement("balance-sheet", r, Locale.ENGLISH);
        assertTrue(
                paper.rows().stream()
                        .anyMatch(x -> x.heading() && x.label().equals("Awaiting classification")));
        assertTrue(paper.rows().stream().anyMatch(x -> x.cells().contains("820.00")));
    }

    @Test
    @Order(2)
    void proposalsAreIdempotentFrozenAndIndependentlyReviewed() {
        var f = form(cashGroup, "CURRENT_ASSETS", 1, "Cash and bank & <script>literal</script>.");
        long id = settings.propose(cashGroup, f);
        assertEquals(id, settings.propose(cashGroup, f));
        assertEquals("UNCLASSIFIED", mapping(cash).section());
        f.setReason("Different retry");
        assertThrows(IllegalArgumentException.class, () -> settings.propose(cashGroup, f));
        f.setReason(settings.details(cashGroup, id).revision().reason());
        assertThrows(
                IllegalArgumentException.class,
                () -> settings.decide(cashGroup, id, decision(0, "approve")));
        assertEquals(Set.of("cancel"), settings.details(cashGroup, id).actions());
        actor("admin@fcms.local");
        assertTrue(settings.details(cashGroup, id).actions().contains("approve"));
        settings.decide(cashGroup, id, decision(0, "approve"));
        assertEquals("CURRENT_ASSETS", mapping(cash).section());
        assertEquals(1, mapping(contra).noteNumber());
        assertEquals("APPROVED", settings.details(cashGroup, id).revision().state());
        assertEquals(
                2L,
                db.queryForObject(
                        "select count(*) from audit_events where resource='gl_account_reporting'"
                                + " and resource_id=?",
                        Long.class,
                        id));
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> settings.decide(cashGroup, id, decision(0, "approve")));
    }

    @Test
    @Order(3)
    void approvedFamiliesDriveOriginalSectionsAndNumberedNotes() {
        approve(equipmentGroup, "NON_CURRENT_ASSETS", 2, LONG_NOTE);
        approve(liabilityGroup, "CURRENT_LIABILITIES", 5, "Recorded payable.");
        approve(accounts.account(income).parentId(), "", 3, "Recorded fee income.");
        approve(accounts.account(expense).parentId(), "", 4, "Operating and noncash costs.");
        approve(accounts.account(equity).parentId(), "", 6, "Owner equity.");
        var pos = statements.comparison("balance-sheet", start, today);
        assertEquals(0, pos.unclassifiedCount());
        var paper = presentation.statement("balance-sheet", pos, Locale.ENGLISH);
        assertEquals(
                List.of("Current assets", "Non-current assets", "Current liabilities", "Equity"),
                paper.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        var cashLine =
                paper.rows().stream()
                        .filter(
                                r ->
                                        r.drillLine() != null
                                                && r.drillLine()
                                                        .equals(
                                                                "group-"
                                                                        + accounts.account(cash)
                                                                                .parentId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("111000", "Bank 3", "1", "780.00", "580.00"), cashLine.cells());
        var notes =
                presentation.statement(
                        "notes", statements.comparison("notes", start, today), Locale.ENGLISH);
        assertEquals(7, notes.rows().stream().filter(r -> r.accountId() != null).count());
        assertEquals(
                List.of("111002", "Contra reserve 4", "", "(50.00)", "0.00"),
                notes.rows().stream()
                        .filter(r -> Objects.equals(r.accountId(), contra))
                        .findFirst()
                        .orElseThrow()
                        .cells());
        assertEquals(
                List.of("511001", "Revenue 4", "", "200.00", "100.00"),
                notes.rows().stream()
                        .filter(r -> Objects.equals(r.accountId(), income))
                        .findFirst()
                        .orElseThrow()
                        .cells());
        assertEquals(
                List.of("Note 1", "Note 2", "Note 3", "Note 4", "Note 5", "Note 6"),
                notes.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        assertEquals(
                1,
                notes.rows().stream()
                        .filter(
                                r ->
                                        r.cells()
                                                .contains(
                                                        "Cash and bank &"
                                                                + " <script>literal</script>."))
                        .count());
        assertTrue(
                notes.provenance().stream().anyMatch(v -> v.startsWith("Reporting references: #")));
    }

    @Test
    @Order(4)
    void noteInheritanceCanBeSuppressedOverriddenAndRestored() {
        approve(cash, "", 0, "");
        assertEquals(0, mapping(cash).noteNumber());
        assertEquals(1, mapping(contra).noteNumber());
        var pos =
                presentation.statement(
                        "balance-sheet",
                        statements.comparison("balance-sheet", start, today),
                        Locale.ENGLISH);
        assertEquals(
                "",
                pos.rows().stream()
                        .filter(
                                r ->
                                        r.drillLine() != null
                                                && r.drillLine()
                                                        .equals(
                                                                "group-"
                                                                        + accounts.account(cash)
                                                                                .parentId()))
                        .findFirst()
                        .orElseThrow()
                        .cells()
                        .get(2));
        approve(cash, "", null, "");
        assertEquals(1, mapping(cash).noteNumber());
        long family = accounts.account(cash).parentId();
        approve(family, "", 7, "Family note.");
        assertEquals(7, mapping(cash).noteNumber());
        assertEquals("Family note.", mapping(cash).noteText());
        approve(cash, "", 6, "");
        assertEquals(6, mapping(cash).noteNumber());
        assertEquals("", mapping(cash).noteText());
        approve(cash, "", null, "");
        approve(family, "", null, "");
        assertEquals(1, mapping(cash).noteNumber());
        assertTrue(mapping(cash).revisionIds().size() >= 3);
    }

    @Test
    @Order(5)
    void definitionsValidateRolesNumbersTextAndAccountScope() {
        var wrong = form(cash, "CURRENT_ASSETS", null, "");
        assertThrows(IllegalArgumentException.class, () -> settings.propose(cash, wrong));
        var mismatch = form(equipmentGroup, "CURRENT_LIABILITIES", 2, "Text");
        assertThrows(
                IllegalArgumentException.class, () -> settings.propose(equipmentGroup, mismatch));
        var noNumber = form(equipment, "", null, "Missing number");
        assertThrows(IllegalArgumentException.class, () -> settings.propose(equipment, noNumber));
        var tooLarge = form(equipment, "", 1000, "");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> settings.propose(equipment, tooLarge));
        var control = form(equipment, "", 2, "Bad\ttext");
        assertThrows(IllegalArgumentException.class, () -> settings.propose(equipment, control));
        long root = db.queryForObject("select id from gl_accounts where code='100000'", Long.class);
        approve(root, "", 8, "Line one\nLine two\r\nLine three");
        assertEquals(1, mapping(cash).noteNumber());
        var v = settings.workspace(cashGroup, query()).effective();
        assertThrows(AccessDeniedException.class, () -> settings.details(equipmentGroup, v.id()));
        assertThrows(
                AccessDeniedException.class,
                () -> settings.decide(equipmentGroup, v.id(), decision(0, "reject")));
    }

    @Test
    @Order(6)
    void readOnlyAndRevokedActorsCannotChangeDefinitions() {
        long privateAccount =
                db.queryForObject("select id from gl_accounts where code='200000'", Long.class);
        long privateId =
                settings.propose(
                        privateAccount,
                        form(privateAccount, "", 9, "Unpublished reporting evidence"));
        actor(reader);
        var privateWorkspace = settings.workspace(privateAccount, query());
        assertNull(privateWorkspace.pending());
        assertTrue(privateWorkspace.history().rows().isEmpty());
        assertFalse(privateWorkspace.pendingExists());
        assertThrows(
                AccessDeniedException.class, () -> settings.details(privateAccount, privateId));
        db.update("insert into user_claims(user_id,claim) values (?,'accounting.setup')", readerId);
        assertTrue(settings.workspace(privateAccount, query()).pendingExists());
        assertFalse(settings.workspace(privateAccount, query()).canPropose());
        assertThrows(IllegalArgumentException.class, () -> settings.setup(privateAccount, null));
        assertThrows(
                AccessDeniedException.class,
                () -> settings.decide(privateAccount, privateId, decision(0, "cancel")));
        actor(maker);
        settings.decide(privateAccount, privateId, decision(0, "cancel"));
        assertTrue(
                settings.workspace(privateAccount, query()).history().rows().stream()
                        .anyMatch(v -> v.id() == privateId));
        actor(reader);
        assertTrue(settings.workspace(privateAccount, query()).history().rows().isEmpty());
        var own = settings.setup(privateAccount, null).form();
        own.setNoteNumber(10);
        own.setReason("Reader's own proposal");
        long ownId = settings.propose(privateAccount, own);
        assertEquals(ownId, settings.workspace(privateAccount, query()).pending().id());
        settings.decide(privateAccount, ownId, decision(0, "cancel"));
        db.update("delete from user_claims where user_id=? and claim='accounting.setup'", readerId);

        var f = form(equipment, "", 2, "Valid note");
        actor(reader);
        assertNotNull(settings.workspace(cashGroup, query()).effective());
        assertFalse(statements.comparison("notes", start, today).accounts().isEmpty());
        assertThrows(AccessDeniedException.class, () -> settings.setup(equipment, null));
        assertThrows(AccessDeniedException.class, () -> settings.propose(equipment, f));
        var v = settings.workspace(cashGroup, query()).effective();
        assertThrows(
                AccessDeniedException.class,
                () -> settings.decide(cashGroup, v.id(), decision(v.version(), "reject")));
        db.update(
                "delete from user_claims where user_id=? and claim='accounting.report'", readerId);
        assertThrows(
                AccessDeniedException.class, () -> statements.comparison("notes", start, today));
        db.update(
                "insert into user_claims(user_id,claim) values (?,'accounting.report')", readerId);
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> settings.workspace(cashGroup, query()));
        assertThrows(AccessDeniedException.class, () -> statements.notesExport(start, today));
    }

    @Test
    @Order(7)
    void staleAccountsAndDefinitionsCannotBeApproved() {
        var original = accounts.account(equipment);
        long id = settings.propose(equipment, form(equipment, "", 2, "Pending stale change"));
        var f = new AccountForm();
        f.setParentId(original.parentId());
        f.setCode(original.code());
        f.setName("Equipment changed");
        f.setDescription(original.description());
        f.setNormalBalance(original.normalBalance());
        f.setCashAccount(original.cashAccount());
        f.setVersion(original.version());
        accounts.saveAccount(equipment, f);
        actor("admin@fcms.local");
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> settings.decide(equipment, id, decision(0, "approve")));
        assertEquals("PENDING", settings.details(equipment, id).revision().state());
        actor(maker);
        settings.decide(equipment, id, decision(0, "cancel"));
        f.setName(original.name());
        f.setVersion(accounts.account(equipment).version());
        accounts.saveAccount(equipment, f);
        var wrong = form(equipmentGroup, "NON_CURRENT_ASSETS", 2, LONG_NOTE);
        wrong.setBaseId(null);
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> settings.propose(equipmentGroup, wrong));
    }

    @Test
    @Order(8)
    void replacementAndDatabaseGuardsRetainApprovedEvidence() {
        var old = settings.workspace(cashGroup, query()).effective();
        long next = approve(cashGroup, "NON_CURRENT_ASSETS", 1, old.proposed().noteText());
        var retired = settings.details(cashGroup, old.id()).revision();
        assertEquals("RETIRED", retired.state());
        assertEquals(next, retired.replacedBy());
        assertEquals(old.reviewReason(), retired.reviewReason());
        assertEquals(
                1L,
                db.queryForObject(
                        "select count(*) from gl_account_reporting where account_id=? and"
                                + " state='APPROVED'",
                        Long.class,
                        cashGroup));
        assertThrows(
                DataAccessException.class,
                () -> db.update("update gl_account_reporting set note_no=99 where id=?", old.id()));
        assertThrows(
                DataAccessException.class,
                () -> db.update("delete from gl_account_reporting where id=?", old.id()));
        approve(cashGroup, "CURRENT_ASSETS", 1, old.proposed().noteText());
        var root = db.queryForObject("select id from gl_accounts where code='100000'", Long.class);
        var base = settings.workspace(root, query()).effective();
        long pending = settings.propose(root, form(root, "", 8, "Replacement pending"));
        var tx = new TransactionTemplate(transactions);
        var failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                tx.execute(
                                        status -> {
                                            db.update(
                                                    "update gl_account_reporting set"
                                                        + " state='RETIRED',retired_by=1,retired_at=current_timestamp,replaced_by=?,version=version+1"
                                                        + " where id=?",
                                                    pending,
                                                    base.id());
                                            return null;
                                        }));
        assertTrue(
                rootMessage(failure)
                        .contains("Reporting retirement requires its approved replacement"));
        assertEquals("APPROVED", settings.details(root, base.id()).revision().state());
        settings.decide(root, pending, decision(0, "cancel"));
        long expenseRoot =
                db.queryForObject("select id from gl_accounts where code='400000'", Long.class);
        long self = settings.propose(expenseRoot, form(expenseRoot, "", null, ""));
        assertThrows(
                DataAccessException.class,
                () ->
                        db.update(
                                "update gl_account_reporting set"
                                    + " state='APPROVED',checker_id=maker_id,review_reason='Forged"
                                    + " self"
                                    + " review',reviewed_at=current_timestamp,version=version+1"
                                    + " where id=?",
                                self));
        assertEquals("PENDING", settings.details(expenseRoot, self).revision().state());
        settings.decide(expenseRoot, self, decision(0, "cancel"));
    }

    @Test
    @Order(9)
    void concurrentRetriesAndDecisionsProduceOneEffectiveRevision() throws Exception {
        long root = db.queryForObject("select id from gl_accounts where code='400000'", Long.class);
        var f = form(root, "", null, "");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> request =
                    () -> {
                        try {
                            actor(maker);
                            return settings.propose(root, f);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
            var a = pool.submit(request);
            var b = pool.submit(request);
            long id = a.get(30, TimeUnit.SECONDS);
            assertEquals(id, b.get(30, TimeUnit.SECONDS));
            var results = new ArrayList<Future<String>>();
            for (String who : List.of("admin@fcms.local", reviewer))
                results.add(
                        pool.submit(
                                () -> {
                                    try {
                                        actor(who);
                                        settings.decide(root, id, decision(0, "approve"));
                                        return "approved";
                                    } catch (OptimisticLockingFailureException e) {
                                        return "stale";
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                }));
            var outcomes =
                    List.of(
                            results.get(0).get(30, TimeUnit.SECONDS),
                            results.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("approved"::equals).count());
            assertEquals(1, outcomes.stream().filter("stale"::equals).count());
            actor(maker);
            assertEquals(
                    1L,
                    db.queryForObject(
                            "select count(*) from audit_events where"
                                    + " action='accounting.reporting.approve' and resource_id=?",
                            Long.class,
                            id));
            assertEquals("APPROVED", settings.details(root, id).revision().state());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Order(9)
    void auditFailureRollsBackTheApprovalAndRetirementTogether() {
        var previous = settings.workspace(cashGroup, query()).effective();
        long pending =
                settings.propose(
                        cashGroup, form(cashGroup, "CURRENT_ASSETS", 1, "Audit rollback proposal"));
        long auditBefore = db.queryForObject("select count(*) from audit_events", Long.class);
        db.execute(
                """
                create function reporting_test_audit_failure() returns trigger language plpgsql as $$
                begin
                 if new.resource='gl_account_reporting' and new.action='accounting.reporting.approve'
                 then raise exception 'Synthetic reporting audit failure'; end if;
                 return new;
                end; $$
                """);
        db.execute(
                "create trigger reporting_test_audit_failure before insert on audit_events "
                        + "for each row execute function reporting_test_audit_failure()");
        try {
            actor("admin@fcms.local");
            var failure =
                    assertThrows(
                            DataAccessException.class,
                            () -> settings.decide(cashGroup, pending, decision(0, "approve")));
            assertTrue(rootMessage(failure).contains("Synthetic reporting audit failure"));
            assertEquals(previous.id(), settings.workspace(cashGroup, query()).effective().id());
            assertEquals("APPROVED", settings.details(cashGroup, previous.id()).revision().state());
            assertNull(settings.details(cashGroup, previous.id()).revision().replacedBy());
            assertEquals("PENDING", settings.details(cashGroup, pending).revision().state());
            assertEquals(
                    auditBefore,
                    db.queryForObject("select count(*) from audit_events", Long.class));
        } finally {
            db.execute("drop trigger reporting_test_audit_failure on audit_events");
            db.execute("drop function reporting_test_audit_failure()");
        }
        actor(maker);
        settings.decide(cashGroup, pending, decision(0, "cancel"));
    }

    @Test
    @Order(10)
    void historyUsesBoundedServerFilteringSortingAndPagination() {
        long root = db.queryForObject("select id from gl_accounts where code='500000'", Long.class);
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 26; i++) {
            var f = form(root, "", 0, "");
            f.setReason("History " + i);
            long id = settings.propose(root, f);
            settings.decide(root, id, decision(0, "cancel"));
            ids.add(id);
        }
        var q = query();
        q.setStatus("CANCELLED");
        q.setSort("id");
        q.setDirection("asc");
        var first = settings.workspace(root, q);
        assertEquals(10, first.history().rows().size());
        assertTrue(first.history().hasNext());
        assertEquals(ids.getFirst(), first.history().rows().getFirst().id());
        q.setPage(2);
        var last = settings.workspace(root, q);
        assertEquals(6, last.history().rows().size());
        assertFalse(last.history().hasNext());
        var special = form(root, "", 0, "");
        special.setReason("History literal %_!");
        long id = settings.propose(root, special);
        settings.decide(root, id, decision(0, "cancel"));
        q.setPage(0);
        q.setSearch("%_!");
        assertEquals(
                List.of(id),
                settings.workspace(root, q).history().rows().stream().map(v -> v.id()).toList());
        q.setSearch("' OR true --");
        assertTrue(settings.workspace(root, q).history().rows().isEmpty());
    }

    @Test
    @Order(11)
    void snapshotsAndGroupDrillsKeepTheirExactDateMembership() throws Exception {
        long family = accounts.account(cash).parentId();
        var q = query();
        q.setFrom(start);
        q.setTo(today);
        var group = drill.accounts("balance-sheet", "group-" + family, q);
        amount("780.00", group.total());
        assertEquals(
                Set.of(cash, contra),
                new HashSet<>(group.records().rows().stream().map(r -> r.id()).toList()));
        var section = drill.accounts("balance-sheet", "section-CURRENT_ASSETS", q);
        amount("780.00", section.total());
        assertThrows(
                IllegalArgumentException.class,
                () -> drill.entries("balance-sheet", "group-" + family, equipment, q));
        assertThrows(
                IllegalArgumentException.class,
                () -> drill.accounts("income-statement", "group-" + family, q));
        assertThrows(
                IllegalArgumentException.class,
                () -> drill.accounts("balance-sheet", "group-1 OR true", q));
        q.setTo(today.minusYears(1));
        q.setFrom(start.minusYears(1));
        amount("580.00", drill.accounts("balance-sheet", "group-" + family, q).total());
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var pool = Executors.newSingleThreadExecutor();
        var previous = settings.workspace(cashGroup, query()).effective();
        try {
            tx.execute(
                    status -> {
                        var before = statements.comparison("notes", start, today);
                        try {
                            pool.submit(
                                            () -> {
                                                try {
                                                    return approve(
                                                            cashGroup,
                                                            "CURRENT_ASSETS",
                                                            1,
                                                            "Concurrent approved note");
                                                } finally {
                                                    SecurityContextHolder.clearContext();
                                                }
                                            })
                                    .get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        var after = statements.comparison("notes", start, today);
                        assertEquals(before.reportingVersionIds(), after.reportingVersionIds());
                        assertEquals(
                                before.accounts().getFirst().mapping().noteText(),
                                after.accounts().getFirst().mapping().noteText());
                        return null;
                    });
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertNotEquals(previous.id(), settings.workspace(cashGroup, query()).effective().id());
        approve(cashGroup, "CURRENT_ASSETS", 1, previous.proposed().noteText());
        var unavailable = statements.comparison("notes", opening, opening.plusDays(1));
        assertFalse(unavailable.previousAvailable());
        assertTrue(
                presentation.statement("notes", unavailable, Locale.ENGLISH).rows().stream()
                        .filter(r -> r.accountId() != null)
                        .allMatch(r -> r.cells().get(4).equals("Unavailable")));
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
    @Order(12)
    void nativeJspNotesAndProposalReviewEnforcePermissionsCsrfAndRecovery() throws Exception {
        var web = login("admin@fcms.local");
        String base = "/accounting/accounts/" + equipment + "/reporting";
        var create = get(web, base + "/new?lang=en");
        assertEquals(200, create.statusCode());
        assertTrue(create.body().contains("Propose reporting setup"));
        var f = hidden(create.body());
        f.put("noteText", "Missing number <script>literal</script>");
        f.put("reason", "HTTP reporting fixture");
        var bad = post(web, base, f);
        assertEquals(200, bad.statusCode());
        assertTrue(bad.body().contains("Enter a note number for this text."));
        assertTrue(bad.body().contains("&lt;script&gt;literal&lt;/script&gt;"));
        f.put("noteNumber", "2");
        f.put("noteText", LONG_NOTE);
        var csrf = new LinkedHashMap<>(f);
        csrf.remove("_csrf");
        assertEquals(403, post(web, base, csrf).statusCode());
        var saved = post(web, base, f);
        assertEquals(302, saved.statusCode());
        assertEquals(
                saved.headers().firstValue("Location"),
                post(web, base, f).headers().firstValue("Location"));
        String location = saved.headers().firstValue("Location").orElseThrow();
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1).split("\\?")[0]);
        var detail = get(web, location);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("data-message-type=\"success\""));
        assertTrue(detail.body().contains("PENDING") || detail.body().contains("Pending"));
        var self = hidden(detail.body());
        self.put("version", "0");
        self.put("action", "approve");
        self.put("reason", "Independent review required");
        var denied = post(web, base + "/" + id + "/decision", self);
        assertEquals(200, denied.statusCode());
        assertTrue(denied.body().contains("An independent reviewer is required."));
        assertTrue(denied.body().contains("Independent review required"));
        actor(maker);
        assertEquals("PENDING", settings.details(equipment, id).revision().state());
        var reviewerWeb = login(reviewer);
        var pending = get(reviewerWeb, base + "/" + id);
        assertEquals(200, pending.statusCode());
        var review = hidden(pending.body());
        review.put("version", "0");
        review.put("action", "approve");
        review.put("reason", "HTTP independent review");
        assertEquals(302, post(reviewerWeb, base + "/" + id + "/decision", review).statusCode());
        assertEquals("APPROVED", settings.details(equipment, id).revision().state());
        var forbidden = login(staff);
        for (String route :
                List.of(
                        "/accounting/statements/notes",
                        "/accounting/statements/notes/print",
                        "/accounting/statements/notes/print.pdf",
                        base + "/new",
                        base + "/" + id))
            assertEquals(403, get(forbidden, route).statusCode(), route);
        var read = login(reader);
        assertEquals(403, get(read, base + "/new").statusCode());
        assertEquals(200, get(read, base + "/" + id).statusCode());
        for (String lang : List.of("en", "sw"))
            for (String suffix : List.of("", "/print", "/print.pdf")) {
                var response =
                        get(
                                web,
                                "/accounting/statements/notes"
                                        + suffix
                                        + "?from="
                                        + start
                                        + "&to="
                                        + today
                                        + "&lang="
                                        + lang);
                assertEquals(200, response.statusCode(), lang + suffix);
                if (suffix.equals("/print.pdf")) {
                    assertTrue(
                            response.headers()
                                    .firstValue("Content-Type")
                                    .orElse("")
                                    .startsWith("application/pdf"));
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
                                            lang.equals("en")
                                                    ? "Notes to the financial statements"
                                                    : "Maelezo ya taarifa za fedha"));
                    assertTrue(response.body().contains("&lt;script&gt;literal&lt;/script&gt;"));
                    if (suffix.isEmpty())
                        assertTrue(
                                response.body()
                                        .contains("/ledger?from=" + start + "&amp;to=" + today));
                    else assertFalse(response.body().contains("class=\"fs-details-button\""));
                }
            }
        var wrongAccount = get(web, "/accounting/accounts/" + cash + "/reporting/" + id + "?saved");
        assertEquals(403, wrongAccount.statusCode());
        assertFalse(wrongAccount.body().contains("HTTP independent review"));
        assertFalse(wrongAccount.body().contains("data-message-type=\"success\""));
        long historyAccount =
                db.queryForObject("select id from gl_accounts where code='500000'", Long.class);
        for (String direction : List.of("asc", "desc")) {
            for (int page = 0; page < 2; page++) {
                var history =
                        get(
                                web,
                                "/accounting/accounts/"
                                        + historyAccount
                                        + "?size=10&sort=id&direction="
                                        + direction
                                        + "&page="
                                        + page);
                assertEquals(200, history.statusCode());
                for (String key : List.of("id", "state", "maker", "created", "reviewed")) {
                    assertTrue(history.body().contains("data-table-sort-key=\"" + key + "\""));
                    assertTrue(history.body().contains("data-table-sort-asc=\"" + key + "\""));
                    assertTrue(history.body().contains("data-table-sort-desc=\"" + key + "\""));
                }
                var links =
                        Pattern.compile(
                                        "href=\"/accounting/accounts/"
                                                + historyAccount
                                                + "/reporting/([0-9]+)\"")
                                .matcher(history.body());
                var observed = new ArrayList<Long>();
                while (links.find()) observed.add(Long.parseLong(links.group(1)));
                var expected =
                        db.queryForList(
                                "select id from gl_account_reporting where account_id=? order by id"
                                        + " "
                                        + direction
                                        + " limit 10 offset ?",
                                Long.class,
                                historyAccount,
                                page * 10);
                assertEquals(10, expected.size());
                assertEquals(expected, observed, direction + " page " + page);
            }
        }
    }

    @AfterAll
    void unchangedMoney() throws Exception {
        if (financialBefore != null) {
            assertEquals(financialBefore, financialDigest());
            Files.writeString(PROOF.resolve("financial-after.txt"), financialDigest());
        }
        SecurityContextHolder.clearContext();
    }
}
