package com.foresight.fcms.modules;

import static com.foresight.fcms.shared.time.ApplicationCalendar.today;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;

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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
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
class StatementComparisonTests {
    private static final String SCHEMA =
            "statement_compare_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired AccountingService accounting;
    @Autowired JournalService journals;
    @Autowired StatementService reports;
    @Autowired StatementPresentation presentation;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    final String maker = "statement-maker@test.invalid", staff = "statement-staff@test.invalid";
    final LocalDate today = today(),
            start = today.withDayOfYear(1),
            opening = start.minusYears(2);
    long makerId, cash, income, expense, equity, ledgerAsset;
    final List<Long> ledgerJournals = new ArrayList<>();

    void actor(String name) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, "", List.of()));
    }

    @BeforeAll
    void setup() {
        makerId = user(maker, 1);
        user(staff, 2);
        actor(maker);
        var book = new BookForm();
        book.setOpeningDate(opening);
        book.setOpeningMode("ZERO");
        book.setPolicyReference("Comparative fixture opening");
        accounting.configure(book);
        actor("admin@fcms.local");
        accounting.approveBooks(0, "Independent opening review");
        for (int year = opening.getYear(); year <= today.getYear(); year++) {
            var period = new PeriodForm();
            period.setName("Comparative " + year);
            period.setStartsOn(LocalDate.of(year, 1, 1));
            period.setEndsOn(LocalDate.of(year, 12, 31));
            accounting.createPeriod(period);
        }
        actor(maker);
        cash = account("111001", "Bank", "ASSET", true);
        income = account("511001", "Income", "INCOME", false);
        expense = account("411001", "Expense", "EXPENSE", false);
        equity = account("311001", "Owner equity", "EQUITY", false);
        post("JOURNAL", cash, equity, "500.00", "FINANCING", today.minusYears(1));
        post("RECEIPT", cash, income, "100.35", "OPERATING", today.minusYears(1));
        post("PAYMENT", expense, cash, "20.10", "OPERATING", today.minusYears(1));
        post("JOURNAL", cash, equity, "50.00", "FINANCING", today);
        post("RECEIPT", cash, income, "200.65", "OPERATING", today);
        post("PAYMENT", expense, cash, "30.25", "OPERATING", today);
    }

    @BeforeEach
    void signIn() {
        actor(maker);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long user(String email, long role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id)"
                        + " values ('Statement','Fixture',?,?,1,?) returning id",
                Long.class,
                email,
                passwords.encode("Verification123!"),
                role);
    }

    long account(String code, String name, String classification, boolean cashAccount) {
        long parent =
                db.queryForObject(
                        "select id from gl_accounts where code=?",
                        Long.class,
                        code.substring(0, 1) + "00000");
        for (int depth = 2; depth <= 4; depth++) {
            String next = depth == 4 ? code : code.substring(0, depth) + "0".repeat(6 - depth);
            var ids = db.queryForList("select id from gl_accounts where code=?", Long.class, next);
            if (!ids.isEmpty()) {
                parent = ids.getFirst();
                continue;
            }
            var f = new AccountForm();
            f.setParentId(parent);
            f.setCode(next);
            f.setName(name + " " + depth);
            f.setNormalBalance(
                    Set.of("ASSET", "EXPENSE").contains(classification) ? "DEBIT" : "CREDIT");
            f.setCashAccount(depth == 4 && cashAccount);
            parent = accounting.saveAccount(null, f);
        }
        return parent;
    }

    long post(String kind, long debit, long credit, String amount, String flow, LocalDate date) {
        return post(
                kind,
                debit,
                credit,
                amount,
                flow,
                date,
                UUID.randomUUID().toString(),
                "Comparative fixture");
    }

    long post(
            String kind,
            long debit,
            long credit,
            String amount,
            String flow,
            LocalDate date,
            String reference,
            String description) {
        actor(maker);
        var f = new JournalForm();
        f.setKind(kind);
        f.setDate(date);
        f.setReference(reference);
        f.setDescription(description);
        f.setEvidenceReference("Reviewed comparative evidence");
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

    @Test
    @Order(1)
    void currentAndPriorFiguresUseTheirActualPostingPeriods() {
        var report = reports.comparison("income-statement", start, today);
        assertTrue(report.previousAvailable());
        assertEquals(start.minusYears(1), report.previousFrom());
        assertEquals(today.minusYears(1), report.previousTo());
        assertEquals(0, new BigDecimal("200.65").compareTo(report.current().income()));
        assertEquals(0, new BigDecimal("100.35").compareTo(report.previous().income()));
        assertEquals(0, new BigDecimal("170.40").compareTo(report.current().profit()));
        assertEquals(0, new BigDecimal("80.25").compareTo(report.previous().profit()));
        assertEquals(2, report.accounts().size());
        var row =
                report.accounts().stream()
                        .filter(a -> a.current().id() == income)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("200.65").compareTo(row.current().credit()));
        assertEquals(0, new BigDecimal("100.35").compareTo(row.previous().credit()));
        var balance = reports.comparison("balance-sheet", start, today);
        assertEquals(0, new BigDecimal("800.65").compareTo(balance.current().assets()));
        assertEquals(0, new BigDecimal("580.25").compareTo(balance.previous().assets()));
        assertTrue(balance.current().balanceSheetMatches());
        assertTrue(balance.current().equityMatches());
        assertTrue(balance.current().cashFlowMatches());
    }

    @Test
    @Order(2)
    void datesBeforeTheApprovedOpeningNeverBecomeComparativeZeros() {
        var source = reports.comparison("balance-sheet", opening, opening.plusDays(1));
        assertFalse(source.previousAvailable());
        assertNull(source.previous());
        assertTrue(source.accounts().stream().allMatch(a -> a.previous() == null));
        var doc = presentation.statement("balance-sheet", source, Locale.ENGLISH);
        assertTrue(
                doc.rows().stream()
                        .filter(r -> !r.heading())
                        .allMatch(r -> r.cells().getLast().equals("Unavailable")));
    }

    @Test
    @Order(3)
    void deniedActorsCannotReadEitherPeriod() {
        actor(staff);
        assertThrows(
                AccessDeniedException.class,
                () -> reports.comparison("balance-sheet", start, today));
    }

    @Test
    @Order(4)
    void datesAndReportTypesRemainAllowlisted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("balance-sheet", opening.minusDays(1), today));
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("balance-sheet", today, start.minusDays(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("balance-sheet", start, today.plusDays(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("untrusted SQL", start, today));
    }

    @Test
    @Order(5)
    void aConcurrentPostingCannotMixTwoReadSnapshots() {
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var executor = Executors.newSingleThreadExecutor();
        BigDecimal before;
        try {
            before =
                    tx.execute(
                            status -> {
                                var first = reports.comparison("income-statement", start, today);
                                try {
                                    executor.submit(
                                                    () -> {
                                                        try {
                                                            post(
                                                                    "RECEIPT",
                                                                    cash,
                                                                    income,
                                                                    "0.05",
                                                                    "OPERATING",
                                                                    today);
                                                        } finally {
                                                            SecurityContextHolder.clearContext();
                                                        }
                                                    })
                                            .get(30, TimeUnit.SECONDS);
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                                var second = reports.comparison("income-statement", start, today);
                                assertEquals(
                                        0,
                                        first.current()
                                                .income()
                                                .compareTo(second.current().income()));
                                assertEquals(first.accounts(), second.accounts());
                                assertEquals(first.previous(), second.previous());
                                return first.current().income();
                            });
        } finally {
            executor.shutdownNow();
        }
        assertNotNull(before);
        assertEquals(
                0,
                before.add(new BigDecimal("0.05"))
                        .compareTo(
                                reports.comparison("income-statement", start, today)
                                        .current()
                                        .income()));
    }

    @Test
    @Order(6)
    void detailBoundsCoverCashAndEquityWithoutBlockingUnrelatedPositionReports() {
        actor(maker);
        account("512001", "Additional income", "INCOME", false);
        long parent =
                db.queryForObject("select id from gl_accounts where code='511000'", Long.class);
        // Bulk fixture metadata only: no journal, balance or approval is changed.
        db.update(
                "insert into"
                    + " gl_accounts(code,name,classification,normal_balance,depth,parent_id,created_by)"
                    + " select n::text,'Bound fixture '||n,'INCOME','CREDIT',4,?,? from"
                    + " generate_series(511002,511999) n",
                parent,
                makerId);
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("income-statement", start, today));
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("cash-flow", start, today));
        assertThrows(
                IllegalArgumentException.class,
                () -> reports.comparison("changes-in-equity", start, today));
        assertFalse(reports.comparison("balance-sheet", start, today).accounts().isEmpty());
    }

    @Test
    @Order(7)
    void trialMatchingTotalsAndFullExportStayIndependentOfPagingAndSorting() {
        long parent =
                db.queryForObject("select id from gl_accounts where code='111000'", Long.class);
        // Metadata-only bulk fixture; financial movements still use reviewed journal commands.
        db.update(
                "insert into"
                    + " gl_accounts(code,name,classification,normal_balance,depth,parent_id,created_by)"
                    + " select n::text,case when n=111700 then 'Paged trial %_! '||n else 'Paged"
                    + " trial '||n end,'ASSET','DEBIT',4,?,? from generate_series(111700,111824) n",
                parent, makerId);
        for (String[] item :
                List.of(
                        new String[] {"111700", "1.25"},
                        new String[] {"111750", "2.50"},
                        new String[] {"111824", "3.75"})) {
            long account =
                    db.queryForObject(
                            "select id from gl_accounts where code=?", Long.class, item[0]);
            post("JOURNAL", account, income, item[1], "NONE", today);
        }
        var q = new TrialQuery();
        q.setFrom(start);
        q.setTo(today);
        q.setSearch("Paged trial");
        q.setSize(10);
        var first = reports.trialWorkspace(q);
        assertEquals(125, first.totals().count());
        assertEquals(10, first.records().rows().size());
        assertTrue(first.records().hasNext());
        assertEquals("111700", first.records().rows().getFirst().code());
        assertEquals(0, new BigDecimal("7.50").compareTo(first.totals().debit()));
        q.setPage(1);
        var next = reports.trialWorkspace(q);
        assertTrue(next.records().rows().stream().allMatch(r -> r.debit().signum() == 0));
        assertEquals(first.totals(), next.totals());
        q.setPage(5000);
        var last = reports.trialWorkspace(q);
        assertEquals(12, last.records().page());
        assertEquals(5, last.records().rows().size());
        assertFalse(last.records().hasNext());
        assertEquals(first.totals(), last.totals());
        q.setSort("netChange");
        q.setDirection("desc");
        q.setPage(0);
        var sorted = reports.trialWorkspace(q);
        assertEquals(
                List.of("111824", "111750", "111700"),
                sorted.records().rows().stream()
                        .limit(3)
                        .map(StatementService.TrialRow::code)
                        .toList());
        assertEquals(first.totals(), sorted.totals());
        q.setPage(12);
        var exported = reports.trialExport(q);
        assertEquals(125, exported.rows().size());
        assertEquals(12, q.getPage());
        assertEquals(10, q.getSize());
        assertEquals(
                0,
                new BigDecimal("7.50")
                        .compareTo(
                                exported.rows().stream()
                                        .map(StatementService.TrialRow::debit)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    @Order(8)
    void trialSearchRemainsLiteralAndUnauthorizedActorsCannotReadTotals() {
        var q = new TrialQuery();
        q.setFrom(start);
        q.setTo(today);
        q.setSearch("%_!");
        var literal = reports.trialWorkspace(q);
        assertEquals(1, literal.totals().count());
        assertEquals("111700", literal.records().rows().getFirst().code());
        assertEquals(0, new BigDecimal("1.25").compareTo(literal.totals().debit()));
        q.setSearch("' OR true --");
        q.setPage(9);
        var empty = reports.trialWorkspace(q);
        assertEquals(0, empty.totals().count());
        assertEquals(0, empty.totals().debit().signum());
        assertTrue(empty.records().rows().isEmpty());
        assertEquals(0, empty.records().page());
        q.setFrom(opening.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> reports.trialWorkspace(q));
        q.setFrom(start);
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> reports.trialWorkspace(q));
        assertThrows(AccessDeniedException.class, () -> reports.trialExport(q));
    }

    @Test
    @Order(9)
    void ledgerBalancesIncludeHiddenMovementsBeforeSearchSortAndPagination() {
        actor(maker);
        ledgerAsset = account("181001", "Ledger fixture", "ASSET", false);
        post("JOURNAL", ledgerAsset, equity, "11.25", "NONE", today.minusYears(1));
        for (int i = 0; i < 15; i++)
            ledgerJournals.add(
                    post(
                            "JOURNAL",
                            i == 5 ? income : ledgerAsset,
                            i == 5 ? ledgerAsset : income,
                            i == 5 ? "4.00" : "1.00",
                            "NONE",
                            today.minusDays(2),
                            i == 12 ? "LEDGER-%_!-MATCH" : "LEDGER-HIDDEN-" + i,
                            i == 12
                                    ? "Visible ledger <script>bad()</script>"
                                    : "Ledger ordering fixture"));
        actor(maker);
        var f = new JournalForm();
        f.setKind("JOURNAL");
        f.setDate(today.minusDays(2));
        f.setReference("LEDGER-SPLIT");
        f.setDescription("Same journal split");
        f.setEvidenceReference("Reviewed ledger split");
        f.setCashFlow("NONE");
        f.getLines().get(0).setAccountId(ledgerAsset);
        f.getLines().get(0).setDebit(new BigDecimal("2.25"));
        f.getLines().get(0).setDescription("Split debit");
        f.getLines().get(1).setAccountId(ledgerAsset);
        f.getLines().get(1).setCredit(new BigDecimal("0.50"));
        f.getLines().get(1).setDescription("Split credit");
        var third = new JournalLineForm();
        third.setAccountId(income);
        third.setCredit(new BigDecimal("1.75"));
        f.getLines().add(third);
        long split = journals.save(null, f);
        journals.transition(split, 0, "submit", "");
        actor("admin@fcms.local");
        journals.transition(split, 1, "post", "Independent split review");
        var q = new LedgerQuery();
        q.setFrom(start);
        q.setTo(today);
        q.setSize(10);
        var first = reports.ledger(ledgerAsset, q);
        assertEquals(17, first.matchingCount());
        assertEquals(10, first.records().rows().size());
        assertTrue(first.records().hasNext());
        assertEquals(0, new BigDecimal("11.25").compareTo(first.opening()));
        assertEquals(0, new BigDecimal("16.25").compareTo(first.debit()));
        assertEquals(0, new BigDecimal("4.50").compareTo(first.credit()));
        assertEquals(0, new BigDecimal("23.00").compareTo(first.closing()));
        assertEquals(
                0,
                new BigDecimal("12.25")
                        .compareTo(first.records().rows().getFirst().runningBalance()));
        q.setPage(1);
        var last = reports.ledger(ledgerAsset, q);
        assertEquals(7, last.records().rows().size());
        assertFalse(last.records().hasNext());
        var tail = last.records().rows().subList(5, 7);
        assertEquals(
                List.of(split, split),
                tail.stream().map(StatementService.Activity::journalId).toList());
        assertEquals(
                List.of(new BigDecimal("23.50"), new BigDecimal("23.00")),
                tail.stream().map(StatementService.Activity::runningBalance).toList());
        var full = reports.ledgerExport(ledgerAsset, q);
        assertEquals(17, full.records().rows().size());
        assertEquals(1, q.getPage());
        assertEquals(10, q.getSize());
        assertEquals(last.closing(), full.closing());
        q.setSearch("%_!");
        q.setPage(4000);
        var visible = reports.ledger(ledgerAsset, q);
        assertEquals(1, visible.matchingCount());
        assertEquals(0, q.getPage());
        assertEquals("LEDGER-%_!-MATCH", visible.records().rows().getFirst().reference());
        assertEquals(
                "Visible ledger <script>bad()</script>",
                visible.records().rows().getFirst().description());
        assertEquals(
                0,
                new BigDecimal("19.25")
                        .compareTo(visible.records().rows().getFirst().runningBalance()));
        assertEquals(first.debit(), visible.debit());
        assertEquals(first.credit(), visible.credit());
        assertEquals(first.closing(), visible.closing());
        q.setSearch("Visible ledger");
        assertEquals(1, reports.ledger(ledgerAsset, q).matchingCount());
        q.setSearch("");
        q.setSort("balance");
        q.setDirection("desc");
        assertEquals(
                0,
                new BigDecimal("23.50")
                        .compareTo(
                                reports.ledger(ledgerAsset, q)
                                        .records()
                                        .rows()
                                        .getFirst()
                                        .runningBalance()));
        q.setSearch("' OR true --");
        var none = reports.ledger(ledgerAsset, q);
        assertEquals(0, none.matchingCount());
        assertTrue(none.records().rows().isEmpty());
        assertEquals(first.closing(), none.closing());
    }

    @Test
    @Order(10)
    void ledgerKeepsReviewedReversalsAndRejectsForgedJournalContexts() {
        long original = ledgerJournals.getFirst();
        actor("admin@fcms.local");
        long reversal =
                journals.reverse(
                        original,
                        UUID.randomUUID(),
                        today.minusDays(1),
                        "Independent ledger correction");
        journals.transition(reversal, 0, "submit", "");
        actor(maker);
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        journals.transition(
                                reversal, 1, "post", "Original maker cannot review reversal"));
        assertEquals("SUBMITTED", journals.find(reversal).state());
        String independentReviewer = "ledger-reviewer@test.invalid";
        user(independentReviewer, 1);
        actor(independentReviewer);
        journals.transition(reversal, 1, "post", "Separate reversal review");
        var q = new LedgerQuery();
        q.setFrom(start);
        q.setTo(today);
        q.setSize(10);
        var report = reports.ledgerExport(ledgerAsset, q);
        assertEquals(18, report.matchingCount());
        assertEquals(18, report.records().rows().size());
        assertTrue(report.records().rows().stream().anyMatch(r -> r.journalId() == original));
        assertTrue(
                report.records().rows().stream()
                        .anyMatch(r -> r.journalId() == reversal && r.kind().equals("REVERSAL")));
        assertEquals(0, new BigDecimal("22.00").compareTo(report.closing()));
        var correctionFace = presentation.ledger(report, Locale.ENGLISH);
        assertTrue(
                correctionFace.rows().stream()
                        .filter(r -> r.journalId() != null && r.journalId() == reversal)
                        .findFirst()
                        .orElseThrow()
                        .cells()
                        .get(2)
                        .startsWith("Reversal — REV-" + original));
        assertEquals(report.closing(), report.records().rows().getLast().runningBalance());
        assertEquals(
                new StatementService.LedgerContext(ledgerAsset, start, today),
                reports.ledgerContext(ledgerAsset, original, start, today));
        assertThrows(
                AccessDeniedException.class,
                () -> reports.ledgerContext(cash, original, start, today));
        assertThrows(
                AccessDeniedException.class,
                () -> reports.ledgerContext(ledgerAsset, original, today.minusDays(1), today));
        q.setFrom(opening.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> reports.ledger(ledgerAsset, q));
        q.setFrom(start);
        actor(staff);
        assertThrows(AccessDeniedException.class, () -> reports.ledger(ledgerAsset, q));
        assertThrows(AccessDeniedException.class, () -> reports.ledgerExport(ledgerAsset, q));
        assertThrows(
                AccessDeniedException.class,
                () -> reports.ledgerContext(ledgerAsset, original, start, today));
    }
}
