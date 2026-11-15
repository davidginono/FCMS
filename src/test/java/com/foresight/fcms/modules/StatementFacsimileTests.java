package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.accounting.service.StatementService.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class StatementFacsimileTests {
    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    static Statements report(boolean prior) {
        return report(prior, LocalDate.of(prior ? 2025 : 2026, 11, 3));
    }

    private static Statements report(boolean prior, LocalDate to) {
        var from = LocalDate.of(prior ? 2025 : 2026, 1, 1);
        var classes =
                prior
                        ? List.of(
                                new Classification(
                                        "ASSET", n("1200"), n("800"), n("500"), n("1500")),
                                new Classification(
                                        "LIABILITY", n("-300"), n("0"), n("100"), n("-400")),
                                new Classification(
                                        "EQUITY", n("-900"), n("0"), n("100"), n("-1000")),
                                new Classification("INCOME", n("0"), n("0"), n("700"), n("-700")),
                                new Classification("EXPENSE", n("0"), n("600"), n("0"), n("600")))
                        : List.of(
                                new Classification(
                                        "ASSET", n("1550"), n("1200"), n("750"), n("2000")),
                                new Classification(
                                        "LIABILITY", n("-450"), n("0"), n("150"), n("-600")),
                                new Classification(
                                        "EQUITY", n("-1000"), n("0"), n("50"), n("-1050")),
                                new Classification(
                                        "INCOME", n("-500"), n("0"), n("1000"), n("-1500")),
                                new Classification(
                                        "EXPENSE", n("400"), n("750"), n("0"), n("1150")));
        return new Statements(
                from,
                to,
                "Verified cutover policy",
                classes,
                n(prior ? "700" : "1000"),
                n(prior ? "600" : "750"),
                n(prior ? "100" : "250"),
                n(prior ? "1500" : "2000"),
                n(prior ? "400" : "600"),
                n(prior ? "1000" : "1050"),
                n(prior ? "100" : "350"),
                n(prior ? "900" : "1100"),
                n(prior ? "100" : "50"),
                n(prior ? "1000" : "1200"),
                n(prior ? "200" : "300"),
                n(prior ? "-100" : "-50"),
                n(prior ? "100" : "50"),
                n(prior ? "1200" : "1500"),
                2,
                new SourceCoverageService.Coverage(to, 4, 1, 1, 2, n("5.25"), true));
    }

    static TrialRow account(
            long id,
            String code,
            String name,
            String classification,
            String opening,
            String debit,
            String credit,
            String closingDebit,
            String closingCredit) {
        return new TrialRow(
                id,
                code,
                name,
                classification,
                n(opening),
                n(debit),
                n(credit),
                n(closingDebit),
                n(closingCredit));
    }

    static Comparison comparison(boolean available, boolean longText) {
        var current = report(false);
        var prior = report(true);
        String incomeName =
                longText
                        ? "Long accounting description\n".repeat(120) + "FINAL ACCOUNT"
                        : "Service income";
        var pairs =
                List.of(
                        new ComparedAccount(
                                account(
                                        1, "111001", "Bank", "ASSET", "1200", "1050", "750", "1500",
                                        "0"),
                                available
                                        ? account(
                                                1, "111001", "Bank", "ASSET", "1000", "700", "500",
                                                "1200", "0")
                                        : null),
                        new ComparedAccount(
                                account(
                                        2,
                                        "121001",
                                        "Equipment",
                                        "ASSET",
                                        "350",
                                        "150",
                                        "0",
                                        "500",
                                        "0"),
                                available
                                        ? account(
                                                2,
                                                "121001",
                                                "Equipment",
                                                "ASSET",
                                                "200",
                                                "100",
                                                "0",
                                                "300",
                                                "0")
                                        : null),
                        new ComparedAccount(
                                account(
                                        3,
                                        "211001",
                                        "Payables",
                                        "LIABILITY",
                                        "-450",
                                        "0",
                                        "150",
                                        "0",
                                        "600"),
                                available
                                        ? account(
                                                3,
                                                "211001",
                                                "Payables",
                                                "LIABILITY",
                                                "-300",
                                                "0",
                                                "100",
                                                "0",
                                                "400")
                                        : null),
                        new ComparedAccount(
                                account(
                                        4,
                                        "311001",
                                        "Owner equity",
                                        "EQUITY",
                                        "-1000",
                                        "0",
                                        "50",
                                        "0",
                                        "1050"),
                                available
                                        ? account(
                                                4,
                                                "311001",
                                                "Owner equity",
                                                "EQUITY",
                                                "-900",
                                                "0",
                                                "100",
                                                "0",
                                                "1000")
                                        : null),
                        new ComparedAccount(
                                account(
                                        5,
                                        "511001",
                                        incomeName,
                                        "INCOME",
                                        "-500",
                                        "0",
                                        "1000",
                                        "0",
                                        "1500"),
                                available
                                        ? account(
                                                5,
                                                "511001",
                                                "Service income",
                                                "INCOME",
                                                "0",
                                                "0",
                                                "700",
                                                "0",
                                                "700")
                                        : null),
                        new ComparedAccount(
                                account(
                                        6,
                                        "411001",
                                        "Service costs",
                                        "EXPENSE",
                                        "400",
                                        "750",
                                        "0",
                                        "1150",
                                        "0"),
                                available
                                        ? account(
                                                6,
                                                "411001",
                                                "Service costs",
                                                "EXPENSE",
                                                "0",
                                                "600",
                                                "0",
                                                "600",
                                                "0")
                                        : null));
        return new Comparison(
                current,
                available ? prior : null,
                prior.from(),
                prior.to(),
                pairs,
                Instant.parse("2026-11-03T09:10:11Z"),
                List.of(
                        new CashCounterpart(
                                "OPERATING",
                                5,
                                "Service income",
                                n("1000"),
                                available ? n("700") : null,
                                false),
                        new CashCounterpart(
                                "OPERATING",
                                6,
                                "Service costs",
                                n("-750"),
                                available ? n("-600") : null,
                                false),
                        new CashCounterpart(
                                "OPERATING",
                                1,
                                "Receivables",
                                n("50"),
                                available ? n("100") : null,
                                false),
                        new CashCounterpart(
                                "INVESTING",
                                2,
                                "Equipment",
                                n("-50"),
                                available ? n("-100") : null,
                                false),
                        new CashCounterpart(
                                "FINANCING",
                                4,
                                "Owner equity",
                                n("50"),
                                available ? n("100") : null,
                                false)),
                EquityMatrixCalculator.calculate(
                        pairs,
                        List.of(
                                new com.foresight.fcms.accounting.dto.EquityReporting.Movement(
                                        4,
                                        com.foresight.fcms.accounting.dto.EquityReporting.Category
                                                .UNCLASSIFIED,
                                        false,
                                        n("0"),
                                        n("50")),
                                new com.foresight.fcms.accounting.dto.EquityReporting.Movement(
                                        4,
                                        com.foresight.fcms.accounting.dto.EquityReporting.Category
                                                .UNCLASSIFIED,
                                        true,
                                        n("0"),
                                        n("100"))),
                        com.foresight.fcms.accounting.dto.EquityReporting.Setup.empty(),
                        available,
                        List.of()));
    }

    @Test
    void trialPaperUsesMatchingTotalsRatherThanTheVisiblePage() {
        var query = new com.foresight.fcms.accounting.dto.TrialQuery();
        assertEquals("code", query.getSort());
        assertEquals("asc", query.getDirection());
        query.setSort("debit desc; delete from gl_lines");
        assertEquals("code", query.getSort());
        query.setSort(null);
        assertEquals("code", query.getSort());
        var page =
                new com.foresight.fcms.shared.dto.RegisterPage<>(
                        List.of(
                                account(
                                        1,
                                        "111001",
                                        "Page account",
                                        "ASSET",
                                        "2",
                                        "3",
                                        "1",
                                        "4",
                                        "0")),
                        1,
                        10,
                        true);
        var source =
                new Trial(
                        report(false),
                        page,
                        new TrialTotals(
                                125, n("10"), n("123.45"), n("5.67"), n("150"), n("22.22")));
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var paper = new StatementPresentation(messages()).trialPage(source, "", locale);
            assertEquals("trial", paper.style());
            assertEquals(2, paper.rows().size());
            assertEquals(
                    List.of("111001", "Page account", "2.00", "3.00", "1.00", "2.00", "4.00"),
                    paper.rows().getFirst().cells());
            assertEquals(
                    List.of("10.00", "123.45", "5.67", "117.78", "127.78"),
                    paper.rows().getLast().cells().subList(2, 7));
            assertTrue(paper.rows().getLast().total());
            assertEquals(1L, paper.rows().getFirst().accountId());
            assertTrue(paper.provenance().stream().anyMatch(v -> v.endsWith("150.00")));
            assertTrue(paper.provenance().stream().anyMatch(v -> v.endsWith("22.22")));
        }
    }

    @Test
    void originalTopFieldsKeepTheirPreviewAndPrintOrderInBothLanguages() {
        var presentation = new StatementPresentation(messages());
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            boolean sw = locale.getLanguage().equals("sw");
            String scope = sw ? "Kampuni" : "Company";
            String previous =
                    (sw ? "Kipindi kilichopita" : "Previous period") + ": 01/01/2025 — 03/11/2025";
            String generated = (sw ? "Imetengenezwa" : "Generated") + ": 03/11/2026 12:10 EAT";
            for (String type :
                    List.of(
                            "income-statement",
                            "balance-sheet",
                            "cash-flow",
                            "changes-in-equity",
                            "notes")) {
                String period =
                        type.equals("balance-sheet")
                                ? (sw ? "Hadi tarehe" : "As at") + ": 03/11/2026 | TZS"
                                : (sw ? "Kwa kipindi cha" : "For the period")
                                        + ": 01/01/2026 — 03/11/2026 | TZS";
                for (boolean available : List.of(true, false)) {
                    var document =
                            presentation.statement(type, comparison(available, false), locale);
                    assertEquals(
                            type.equals("changes-in-equity")
                                    ? List.of(
                                            (sw ? "Kwa kipindi" : "For Period")
                                                    + "       "
                                                    + LocalDate.of(2026, 11, 3)
                                                            .format(
                                                                    java.time.format
                                                                            .DateTimeFormatter
                                                                            .ofPattern(
                                                                                    "dd-MMM-uuuu",
                                                                                    locale))
                                                            .toUpperCase(locale),
                                            "FCMS | " + scope + " | TZS")
                                    : List.of("FCMS | " + scope, period, previous, generated),
                            document.context());
                    if (type.equals("changes-in-equity")) {
                        assertEquals(
                                sw
                                        ? "TAARIFA YA MABADILIKO YA MTAJI"
                                        : "STATEMENT of CHANGES IN EQUITY",
                                document.title());
                        assertTrue(document.provenance().contains(previous));
                        assertTrue(document.provenance().contains(generated));
                    }
                    if (!type.equals("changes-in-equity"))
                        assertEquals(
                                List.of(
                                        scope,
                                        period.replace(": ", " ").replace(" | TZS", " · TZS"),
                                        generated.replace(": ", " "),
                                        previous),
                                document.header().previewLines());
                }
            }
        }
    }

    @Test
    void equityReferenceTitleUsesSeparatePeriodForTheCompleteYear() {
        var presentation = new StatementPresentation(messages());
        var current = report(false, LocalDate.of(2026, 12, 31));
        var prior = report(true, LocalDate.of(2025, 12, 31));
        var source =
                new Comparison(
                        current,
                        prior,
                        prior.from(),
                        prior.to(),
                        List.of(),
                        Instant.parse("2026-12-31T09:10:11Z"));
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var document = presentation.statement("changes-in-equity", source, locale);
            assertEquals(
                    locale.getLanguage().equals("sw")
                            ? "TAARIFA YA MABADILIKO YA MTAJI"
                            : "STATEMENT of CHANGES IN EQUITY",
                    document.title());
            assertEquals(
                    List.of(
                            (locale.getLanguage().equals("sw") ? "Kwa kipindi" : "For Period")
                                    + "       "
                                    + current.to()
                                            .format(
                                                    java.time.format.DateTimeFormatter.ofPattern(
                                                            "dd-MMM-uuuu", locale))
                                            .toUpperCase(locale),
                            locale.getLanguage().equals("sw")
                                    ? "FCMS | Kampuni | TZS"
                                    : "FCMS | Company | TZS"),
                    document.context());
            assertTrue(
                    document.provenance().stream()
                            .anyMatch(value -> value.contains("01/01/2026 — 31/12/2026")));
        }
    }

    @Test
    void comparativeRowsRetainAccountDrillsAndActualSignedAmounts() {
        var p = new StatementPresentation(messages());
        var doc = p.statement("income-statement", comparison(true, false), Locale.ENGLISH);
        assertEquals(5, doc.columns().size());
        assertEquals(
                List.of("Account", "Description", "Notes", "Current period", "Previous period"),
                doc.columns().stream().map(c -> c.label()).toList());
        assertEquals(
                List.of("Income", "Expenses"),
                doc.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        assertEquals("FCMS", doc.header().identity());
        assertEquals(
                "Financial position",
                messages().getMessage("gl.statement.balance-sheet", null, Locale.ENGLISH));
        assertEquals("Income Statement", doc.title());
        assertTrue(doc.header().previewLines().get(1).contains(" · TZS"));
        assertTrue(doc.header().previewLines().get(2).startsWith("Generated "));
        assertTrue(doc.context().get(2).startsWith("Previous period:"));
        var account =
                doc.rows().stream()
                        .filter(r -> Objects.equals(r.accountId(), 5L))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                List.of("511001", "Service income", "", "1,000.00", "700.00"), account.cells());
        assertNull(account.drillLine());
        var profit =
                doc.rows().stream()
                        .filter(r -> "report.profit".equals(r.drillLine()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                List.of("", "Surplus / deficit for the period", "", "250.00", "100.00"),
                profit.cells());
        assertTrue(profit.total());
        var cash = p.statement("cash-flow", comparison(true, false), Locale.ENGLISH);
        assertEquals(
                List.of(
                        "Cash flows from operating activities",
                        "Cash flows from investing activities",
                        "Cash flows from financing activities"),
                cash.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        var investing =
                cash.rows().stream()
                        .filter(r -> "cash-INVESTING-2".equals(r.drillLine()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("(50.00)", investing.cells().get(3));
        assertEquals("(100.00)", investing.cells().get(4));
        assertTrue(doc.provenance().stream().anyMatch(v -> v.contains("5.25")));
        assertTrue(doc.provenance().stream().anyMatch(v -> v.contains("Verified cutover policy")));
    }

    @Test
    void unavailableHistoryStaysDistinctFromZeroAndEquityComponentsReconcile() {
        var p = new StatementPresentation(messages());
        var source = comparison(false, false);
        var doc = p.statement("balance-sheet", source, Locale.ENGLISH);
        assertTrue(doc.header().period().startsWith("As at: 03/11/2026"));
        assertTrue(
                doc.rows().stream()
                        .filter(r -> !r.heading())
                        .allMatch(r -> r.cells().getLast().equals("Unavailable")));
        assertTrue(doc.rows().stream().filter(r -> !r.heading()).allMatch(r -> !r.numericCell(4)));
        assertEquals("Previous period: 01/01/2025 — 03/11/2025", doc.header().previous());
        var equity = p.statement("changes-in-equity", source, Locale.ENGLISH);
        assertEquals(
                List.of(false, false, false, true),
                equity.rows().stream().map(r -> r.total()).toList());
        assertEquals(
                List.of("Balance as at 1 January 2026", "1,000.00", "100.00", "1,100.00"),
                equity.rows().getFirst().cells());
        assertEquals(
                List.of("Balance as at 3 November 2026", "1,050.00", "350.00", "1,400.00"),
                equity.rows().getLast().cells());
        assertTrue(source.current().equityMatches());
        assertTrue(source.current().balanceSheetMatches());
        assertTrue(source.current().cashFlowMatches());
    }

    @Test
    void originalFontsPaperHeadersAndSeparateProvenanceRenderInBothLocales() throws Exception {
        var p = new StatementPresentation(messages());
        var pdf = new AccountingDocumentPdfService(messages());
        var dir = Path.of(".verification/statements-facsimile");
        Files.createDirectories(dir);
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            for (String type :
                    List.of(
                            "income-statement",
                            "balance-sheet",
                            "cash-flow",
                            "changes-in-equity",
                            "notes",
                            "trial")) {
                var source = comparison(true, false);
                var doc =
                        type.equals("trial")
                                ? p.trial(
                                        new TrialExport(
                                                source.current(),
                                                source.accounts().stream()
                                                        .map(ComparedAccount::current)
                                                        .toList(),
                                                ""),
                                        locale)
                                : p.statement(type, source, locale);
                var bytes = pdf.statement(doc, locale);
                var reader = new PdfReader(bytes);
                int pages = type.equals("changes-in-equity") ? 3 : 2;
                assertEquals(pages, reader.getNumberOfPages(), type + " " + locale);
                assertEquals(
                        doc.landscape() ? 841.8898 : 595.27563,
                        reader.getPageSize(1).getWidth(),
                        .01);
                var extractor = new PdfTextExtractor(reader);
                String first = extractor.getTextFromPage(1),
                        last = extractor.getTextFromPage(pages);
                assertTrue(first.contains("FCMS"));
                assertTrue(first.contains(doc.notice()));
                assertTrue(first.contains("1 / " + pages));
                assertTrue(last.contains(pages + " / " + pages));
                assertTrue(extractor.getTextFromPage(2).contains("Verified cutover policy"));
                assertFalse(first.contains("Verified cutover policy"));
                if (type.equals("changes-in-equity")) {
                    assertFalse(first.contains("12:10 EAT"));
                    StringBuilder auditText = new StringBuilder();
                    for (int page = 2; page <= pages; page++)
                        auditText.append(extractor.getTextFromPage(page));
                    assertTrue(auditText.toString().contains("12:10 EAT"));
                } else if (!type.equals("trial")) assertTrue(first.contains("12:10 EAT"));
                else assertTrue(first.contains("EAT"));
                String graphics =
                        new String(
                                reader.getPageContent(1),
                                java.nio.charset.StandardCharsets.ISO_8859_1);
                String name = type + "-" + locale.getLanguage();
                var titlePosition =
                        java.util.regex.Pattern.compile("1 0 0 1 (-?[0-9.]+) (-?[0-9.]+) Tm")
                                .matcher(graphics);
                assertTrue(titlePosition.find(), name);
                if (name.equals("income-statement-en"))
                    assertEquals(225.29982, Double.parseDouble(titlePosition.group(1)), .01, name);
                assertEquals(
                        type.equals("changes-in-equity")
                                ? 573.27563
                                : doc.landscape() ? 559.27563 : 805.88977,
                        Double.parseDouble(titlePosition.group(2)),
                        .01,
                        name);
                if (type.equals("changes-in-equity")) {
                    assertTrue(graphics.contains("1 0 0 rg"));
                    assertTrue(Double.parseDouble(titlePosition.group(1)) > 20);
                    assertTrue(titlePosition.find(), name);
                    assertEquals(20, Double.parseDouble(titlePosition.group(1)), .01);
                    assertEquals(554.27563, Double.parseDouble(titlePosition.group(2)), .01);
                }
                reader.close();
                Files.write(dir.resolve("spring-" + name + ".pdf"), bytes);
                Files.writeString(
                        dir.resolve("source-" + name + ".json"),
                        new JsonMapper().writeValueAsString(doc));
            }
        }
    }

    @Test
    void filteredTrialTotalsIdentifyExactlyThePresentedAccounts() {
        var source = comparison(true, false);
        var doc =
                new StatementPresentation(messages())
                        .trial(
                                new TrialExport(
                                        source.current(),
                                        List.of(source.accounts().getFirst().current()),
                                        "111001"),
                                Locale.ENGLISH);
        assertTrue(doc.context().stream().anyMatch(value -> value.contains("111001")));
        assertTrue(doc.header().period().startsWith("As at: 03/11/2026"));
        assertEquals(
                List.of(
                        "Account",
                        "Description",
                        "BF balance",
                        "Debits",
                        "Credits",
                        "Net change",
                        "Ending balance"),
                doc.columns().stream().map(c -> c.label()).toList());
        assertEquals(
                List.of(
                        "Filtered total",
                        "",
                        "1,200.00",
                        "1,050.00",
                        "750.00",
                        "300.00",
                        "1,500.00"),
                doc.rows().getLast().cells());
    }

    @Test
    void longAccountTextContinuesWithoutLosingBalancesOrFinalText() throws Exception {
        var p = new StatementPresentation(messages());
        var doc = p.statement("income-statement", comparison(true, true), Locale.ENGLISH);
        var bytes = new AccountingDocumentPdfService(messages()).statement(doc, Locale.ENGLISH);
        var reader = new PdfReader(bytes);
        assertTrue(reader.getNumberOfPages() > 3);
        var extract = new PdfTextExtractor(reader);
        var text = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            String page = extract.getTextFromPage(i);
            assertTrue(page.contains(i + " / " + reader.getNumberOfPages()));
            text.append(page);
        }
        assertTrue(text.toString().contains("FINAL ACCOUNT"));
        assertTrue(text.toString().contains("1,000.00"));
        assertTrue(text.toString().contains("Verified cutover policy"));
        reader.close();
        var dir = Path.of(".verification/statements-facsimile");
        Files.createDirectories(dir);
        Files.write(dir.resolve("spring-long-income.pdf"), bytes);
        Files.writeString(
                dir.resolve("source-long-income.json"), new JsonMapper().writeValueAsString(doc));
    }

    @Test
    void ledgerFaceRetainsOriginalColumnsAndFullPeriodTotals() throws Exception {
        var q = new com.foresight.fcms.accounting.dto.LedgerQuery();
        assertEquals("date", q.getSort());
        assertEquals("asc", q.getDirection());
        q.setSort("debit; drop table gl_lines");
        assertEquals("date", q.getSort());
        var account =
                new com.foresight.fcms.accounting.dto.AccountingViews.Account(
                        42,
                        "111001",
                        "Ledger bank",
                        "",
                        "ASSET",
                        "DEBIT",
                        4,
                        1L,
                        "Bank",
                        true,
                        true,
                        0);
        var activity =
                new Activity(
                        9,
                        123,
                        "J&<42>",
                        LocalDate.of(2026, 11, 3),
                        "JOURNAL",
                        "Ledger split debit",
                        n("2.25"),
                        n("0.00"),
                        n("23.50"));
        var correction =
                new Activity(
                        10,
                        124,
                        "REV-42",
                        LocalDate.of(2026, 11, 3),
                        "REVERSAL",
                        "Ledger correction",
                        n("0.00"),
                        n("1.00"),
                        n("22.50"));
        var source =
                new AccountLedger(
                        account,
                        new com.foresight.fcms.shared.dto.RegisterPage<>(
                                List.of(activity, correction), 1, 10, false),
                        n("11.25"),
                        n("16.25"),
                        n("4.50"),
                        n("23.00"),
                        report(false).sourceCoverage(),
                        report(false).from(),
                        report(false).to(),
                        "Verified ledger policy",
                        Instant.parse("2026-11-03T09:10:11Z"),
                        12,
                        "LEDGER");
        var dir = Path.of(".verification/account-ledger-paper-20261112/reference");
        Files.createDirectories(dir);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var doc = new StatementPresentation(messages()).ledger(source, locale);
            assertEquals("trial", doc.style());
            assertTrue(doc.landscape());
            assertEquals(-1, doc.detailsBefore());
            assertEquals(
                    locale.getLanguage().equals("en")
                            ? List.of(
                                    "Transaction date",
                                    "Account number",
                                    "Description",
                                    "Debits",
                                    "Credits",
                                    "Running balance")
                            : List.of(
                                    "Tarehe ya muamala",
                                    "Namba ya akaunti",
                                    "Maelezo",
                                    "Debiti",
                                    "Krediti",
                                    "Salio endelevu"),
                    doc.columns().stream().map(c -> c.label()).toList());
            assertEquals(
                    List.of(
                            "",
                            "",
                            locale.getLanguage().equals("en") ? "BF balance" : "Salio la mwanzo",
                            "",
                            "",
                            "11.25"),
                    doc.rows().getFirst().cells());
            assertEquals(
                    List.of(
                            "03/11/2026",
                            locale.getLanguage().equals("en") ? "Unavailable" : "Haipatikani",
                            "Ledger split debit",
                            "2.25",
                            "0.00",
                            "23.50"),
                    doc.rows().get(1).cells());
            assertEquals(
                    messages().getMessage("gl.kind.REVERSAL", null, locale)
                            + " — REV-42 — Ledger correction",
                    doc.rows().get(2).cells().get(2));
            assertEquals(124L, doc.rows().get(2).journalId());
            assertEquals(123L, doc.rows().get(1).journalId());
            assertNull(doc.rows().get(1).accountId());
            assertEquals("J&<42>", doc.rows().get(1).label());
            assertEquals(
                    List.of(
                            "",
                            "",
                            locale.getLanguage().equals("en")
                                    ? "Full period total"
                                    : "Jumla ya kipindi chote",
                            "16.25",
                            "4.50",
                            "23.00"),
                    doc.rows().getLast().cells());
            assertTrue(doc.title().endsWith("— 111001 Ledger bank"));
            assertTrue(doc.header().period().contains("01/01/2026 — 03/11/2026"));
            assertTrue(doc.context().stream().anyMatch(v -> v.contains("12:10 EAT")));
            assertTrue(doc.header().extra().getFirst().contains("LEDGER"));
            var bytes = new AccountingDocumentPdfService(messages()).statement(doc, locale);
            var reader = new PdfReader(bytes);
            assertEquals(2, reader.getNumberOfPages());
            var extract = new PdfTextExtractor(reader);
            assertTrue(extract.getTextFromPage(1).contains("23.50"));
            assertTrue(extract.getTextFromPage(1).contains("23.00"));
            assertTrue(extract.getTextFromPage(2).contains("Verified ledger policy"));
            reader.close();
            String name = "ledger-" + locale.getLanguage();
            Files.write(dir.resolve("spring-" + name + ".pdf"), bytes);
            Files.writeString(
                    dir.resolve("source-" + name + ".json"),
                    new JsonMapper().writeValueAsString(doc));
        }
    }
}
