package com.foresight.fcms.modules;

import static com.foresight.fcms.modules.StatementFacsimileTests.*;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.AccountReporting.Mapping;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.accounting.service.StatementService.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;

class ReportingPresentationTests {
    static final Path REFERENCE = Path.of(".verification/reporting-notes-20261113/reference");
    final StatementPresentation presentation = new StatementPresentation(messages());

    Mapping mapping(long id, int note, String text) {
        String[] names = {
            "",
            "Cash and bank",
            "Equipment",
            "Payables",
            "Owner equity",
            "Fee income",
            "Operating costs"
        };
        String[] codes = {"", "111000", "121000", "211000", "311000", "511000", "411000"};
        String[] sections = {
            "",
            "CURRENT_ASSETS",
            "NON_CURRENT_ASSETS",
            "CURRENT_LIABILITIES",
            "EQUITY",
            "INCOME",
            "EXPENSE"
        };
        return new Mapping(
                100 + id,
                codes[(int) id],
                names[(int) id],
                sections[(int) id],
                note,
                text,
                id < 4 ? 200 + id : null,
                300L + note,
                List.of(200 + id, 300L + note));
    }

    Comparison source(boolean prior, boolean longNotes) {
        var base = comparison(prior, false);
        var accounts = new ArrayList<ComparedAccount>();
        for (var a : base.accounts()) {
            int note =
                    switch ((int) a.current().id()) {
                        case 1, 2 -> 1;
                        case 3 -> 2;
                        case 4 -> 3;
                        default -> 4;
                    };
            String text =
                    note == 1
                            ? (longNotes
                                    ? "Long numbered note ".repeat(100)
                                    : "Shared note & <script>literal</script>.\nSecond line.")
                            : "Disclosure " + a.current().name();
            accounts.add(
                    new ComparedAccount(
                            a.current(), a.previous(), mapping(a.current().id(), note, text)));
        }
        if (longNotes)
            for (int i = 3; i < 123; i++) {
                var now =
                        account(
                                1000 + i,
                                "111" + String.format(Locale.ROOT, "%03d", i),
                                "Supporting financial account " + i,
                                "ASSET",
                                "0",
                                "0",
                                "0",
                                "0",
                                "0");
                accounts.add(
                        new ComparedAccount(
                                now,
                                prior ? now : null,
                                mapping(1, 1, "Long numbered note ".repeat(100))));
            }
        accounts.sort(Comparator.comparing(a -> a.current().code()));
        return new Comparison(
                base.current(),
                base.previous(),
                base.previousFrom(),
                base.previousTo(),
                accounts,
                base.generatedAt());
    }

    Comparison forType(String type, Comparison c) {
        var accounts =
                c.accounts().stream()
                        .filter(
                                a ->
                                        type.equals("notes")
                                                || type.equals("income-statement")
                                                        == Set.of("INCOME", "EXPENSE")
                                                                .contains(
                                                                        a.current()
                                                                                .classification()))
                        .toList();
        return new Comparison(
                c.current(),
                c.previous(),
                c.previousFrom(),
                c.previousTo(),
                accounts,
                c.generatedAt());
    }

    StatementDocument.Row accountRow(StatementDocument d, long id) {
        return d.rows().stream()
                .filter(r -> Objects.equals(r.accountId(), id))
                .findFirst()
                .orElseThrow();
    }

    void write(String name, StatementDocument doc, Locale locale) throws Exception {
        Files.createDirectories(REFERENCE);
        Files.writeString(
                REFERENCE.resolve("source-" + name + ".json"),
                new JsonMapper().writeValueAsString(doc));
        Files.write(
                REFERENCE.resolve("spring-" + name + ".pdf"),
                new AccountingDocumentPdfService(messages()).statement(doc, locale));
    }

    @Test
    void notesUseOriginalColumnsSignedPeriodRulesAndOrderedSections() {
        var d = presentation.statement("notes", source(true, false), Locale.ENGLISH);
        assertEquals("Notes to the financial statements", d.title());
        assertEquals("statement", d.style());
        assertFalse(d.landscape());
        assertEquals(3, d.detailsBefore());
        assertEquals(
                List.of("Account", "Description", "Notes", "Current period", "Previous period"),
                d.columns().stream().map(c -> c.label()).toList());
        assertEquals(
                List.of(.12f, .40f, .08f, .20f, .20f),
                d.columns().stream().map(c -> c.weight()).toList());
        assertEquals(
                List.of("Note 1", "Note 2", "Note 3", "Note 4"),
                d.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        assertEquals(
                List.of("511001", "Service income", "", "1,000.00", "700.00"),
                accountRow(d, 5).cells());
        assertEquals(
                List.of("411001", "Service costs", "", "750.00", "600.00"),
                accountRow(d, 6).cells());
        assertEquals(List.of("", "Total", "", "1,750.00", "1,300.00"), d.rows().getLast().cells());
        assertEquals(
                1,
                d.rows().stream()
                        .filter(
                                r ->
                                        r.cells()
                                                .contains(
                                                        "Shared note & <script>literal</script>.\n"
                                                            + "Second line."))
                        .count());
        assertTrue(d.header().period().startsWith("For the period:"));
        assertTrue(d.provenance().stream().anyMatch(x -> x.startsWith("Reporting references: #")));
    }

    @Test
    void unavailableComparisonsStayUnavailableAndEmptyNotesAreNotInvented() {
        var d = presentation.statement("notes", source(false, false), Locale.ENGLISH);
        assertTrue(
                d.rows().stream()
                        .filter(r -> r.accountId() != null || r.total())
                        .allMatch(r -> r.cells().get(4).equals("Unavailable")));
        var empty = presentation.statement("notes", comparison(false, false), Locale.ENGLISH);
        assertTrue(empty.rows().isEmpty());
        assertFalse(empty.notice().isBlank());
    }

    @Test
    void groupSectionsUseApprovedIdsAndRetainExactTotalsAndDrills() {
        var c = source(true, false);
        var position =
                presentation.statement(
                        "balance-sheet", forType("balance-sheet", c), Locale.ENGLISH);
        assertEquals(
                List.of("Current assets", "Non-current assets", "Current liabilities", "Equity"),
                position.rows().stream().filter(r -> r.heading()).map(r -> r.label()).toList());
        var cash =
                position.rows().stream()
                        .filter(r -> Objects.equals(r.drillLine(), "group-101"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("111000", "Cash and bank", "1", "1,500.00", "1,200.00"), cash.cells());
        assertNull(cash.accountId());
        assertTrue(
                position.rows().stream()
                        .anyMatch(
                                r ->
                                        Objects.equals(r.drillLine(), "section-CURRENT_ASSETS")
                                                && r.cells().get(3).equals("1,500.00")));
        var income =
                presentation.statement(
                        "income-statement", forType("income-statement", c), Locale.ENGLISH);
        assertTrue(
                income.rows().stream()
                        .anyMatch(
                                r ->
                                        Objects.equals(r.drillLine(), "group-105")
                                                && r.cells().get(3).equals("1,000.00")));
        assertEquals("250.00", income.rows().getLast().cells().get(3));
    }

    @Test
    void mixedNotesDoNotPublishAnIncorrectFamilyNote() {
        var c = source(true, false);
        var pairs = new ArrayList<>(c.accounts());
        var first = pairs.stream().filter(a -> a.current().id() == 1).findFirst().orElseThrow();
        pairs.remove(first);
        pairs.add(
                new ComparedAccount(
                        account(1, "111001", "Bank", "ASSET", "1100", "1050", "750", "1400", "0"),
                        account(1, "111001", "Bank", "ASSET", "900", "700", "500", "1100", "0"),
                        first.mapping()));
        pairs.add(
                new ComparedAccount(
                        account(7, "111002", "Second bank", "ASSET", "100", "0", "0", "100", "0"),
                        account(7, "111002", "Second bank", "ASSET", "100", "0", "0", "100", "0"),
                        mapping(1, 0, "")));
        var changed =
                new Comparison(
                        c.current(),
                        c.previous(),
                        c.previousFrom(),
                        c.previousTo(),
                        pairs,
                        c.generatedAt());
        var d =
                presentation.statement(
                        "balance-sheet", forType("balance-sheet", changed), Locale.ENGLISH);
        var row =
                d.rows().stream()
                        .filter(r -> Objects.equals(r.drillLine(), "group-101"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("", row.cells().get(2));
        assertEquals("1,500.00", row.cells().get(3));
    }

    @Test
    void missingRolesStayVisibleAndDoNotBecomeCurrentAssets() {
        var c = comparison(true, false);
        var d =
                presentation.statement(
                        "balance-sheet", forType("balance-sheet", c), Locale.ENGLISH);
        assertTrue(
                d.rows().stream()
                        .anyMatch(r -> r.heading() && r.label().equals("Awaiting classification")));
        assertFalse(
                d.rows().stream().anyMatch(r -> r.heading() && r.label().equals("Current assets")));
        assertEquals(3, c.unclassifiedCount());
        assertEquals("2,000.00", d.rows().getLast().cells().get(3));
    }

    @Test
    void bothLocalesExportTheOriginalNotesAndGroupedFaces() throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            for (String type : List.of("notes", "income-statement", "balance-sheet")) {
                var doc = presentation.statement(type, forType(type, source(true, false)), locale);
                var bytes = new AccountingDocumentPdfService(messages()).statement(doc, locale);
                var pdf = new PdfReader(bytes);
                try {
                    assertTrue(pdf.getNumberOfPages() >= 2);
                    String text = new PdfTextExtractor(pdf).getTextFromPage(1);
                    assertTrue(
                            text.contains(
                                    type.equals("notes")
                                            ? (locale.getLanguage().equals("en")
                                                    ? "NOTES TO THE FINANCIAL STATEMENTS"
                                                    : "MAELEZO YA TAARIFA ZA FEDHA")
                                            : doc.title().toUpperCase(locale)));
                    assertTrue(
                            text.contains("Current period") || text.contains("Kipindi cha sasa"));
                    assertEquals(595.27563f, pdf.getPageSize(1).getWidth(), .00001f);
                } finally {
                    pdf.close();
                }
                write(
                        (type.equals("notes") ? "notes" : "grouped-" + type)
                                + "-"
                                + locale.getLanguage(),
                        doc,
                        locale);
            }
            write(
                    "notes-empty-" + locale.getLanguage(),
                    presentation.statement("notes", comparison(false, false), locale),
                    locale);
        }
    }

    @Test
    void longNotesKeepAllMembersTextAndContinuationPages() throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var doc = presentation.statement("notes", source(true, true), locale);
            assertEquals(126, doc.rows().stream().filter(r -> r.accountId() != null).count());
            var narratives =
                    doc.rows().stream()
                            .filter(r -> !r.heading() && !r.total() && r.accountId() == null)
                            .toList();
            assertTrue(narratives.stream().allMatch(r -> r.cells().get(1).length() <= 500));
            assertEquals(
                    4,
                    narratives.stream()
                            .filter(
                                    r ->
                                            r.cells().get(1).startsWith("Long")
                                                    || r.cells().get(1).contains("numbered note"))
                            .count());
            var bytes = new AccountingDocumentPdfService(messages()).statement(doc, locale);
            var pdf = new PdfReader(bytes);
            try {
                assertTrue(pdf.getNumberOfPages() >= 4);
                String all = "";
                var text = new PdfTextExtractor(pdf);
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    String value = text.getTextFromPage(page);
                    all += value;
                    assertTrue(value.contains(page + " / " + pdf.getNumberOfPages()));
                }
                assertTrue(all.contains("Supporting financial account 122"));
                assertTrue(all.contains("2,000.00"));
                assertTrue(all.contains("1,750.00"));
            } finally {
                pdf.close();
            }
            write("notes-long-" + locale.getLanguage(), doc, locale);
        }
    }
}
