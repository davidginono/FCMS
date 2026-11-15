package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.finance.ledger.dto.*;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.report.service.LedgerReportPdfService;
import com.foresight.fcms.report.view.LegacyLedgerStationery;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class LedgerFacsimileTests {
    private static final Path OUTPUT = Path.of(".verification/ledger-facsimile");
    private final LegacyLedgerStationery pdf = new LegacyLedgerStationery();

    LedgerReportPdfService service() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return new LedgerReportPdfService(null, messages, pdf);
    }

    LedgerView row(
            long id,
            String amount,
            long units,
            long after,
            Long reversalOf,
            Long reversedBy,
            String description) {
        return new LedgerView(
                id,
                "BULK",
                LocalDate.of(2026, 11, 4),
                description,
                "Office",
                "SOURCE-" + id,
                "CASH",
                new BigDecimal(amount),
                units,
                new BigDecimal("900.00"),
                after,
                7,
                "Test Maker",
                reversalOf,
                reversedBy,
                "Test Client");
    }

    LedgerReport source() {
        return new LedgerReport(
                List.of(
                        row(3, "-10.35", -11, 89, 1L, null, "Correction of duplicated purchase"),
                        row(2, "0.00", 3, 103, null, null, "Unit-only adjustment"),
                        row(1, "100.35", 100, 100, null, 3L, "Opening purchase")),
                new BigDecimal("100.35"),
                new BigDecimal("10.35"),
                103,
                11,
                new BigDecimal("900.00"),
                92,
                Instant.parse("2026-11-04T09:10:11Z"));
    }

    @Test
    void distinctOriginalLayoutsPreserveMoneyAndRecordedUnitSnapshots() {
        var bulk =
                service().document(LedgerKind.BULK, source(), new RegisterQuery(), Locale.ENGLISH);
        assertEquals(List.of(8f, 24f, 24f, 30f, 45f, 25f, 25f, 17f, 17f, 31f, 31f), bulk.widths());
        assertEquals(
                List.of("0.00", "10.35", "0", "11", "100", "89"),
                bulk.rows().getFirst().subList(5, 11));
        assertEquals(List.of("100", "103"), bulk.rows().get(1).subList(9, 11));
        assertEquals(List.of("0", "100"), bulk.rows().getLast().subList(9, 11));
        assertEquals(
                List.of("TOTAL:", "100.35", "10.35", "103", "11", ""),
                bulk.totals().getFirst().cells());
        assertEquals("Current SMS Units: 92", bulk.preface().getFirst().text());
        assertEquals("Bulk Sms Profit: 90.00", bulk.preface().getLast().text());
        assertTrue(bulk.rows().getFirst().get(4).contains("#1"));
        assertTrue(bulk.rows().getLast().get(4).contains("#3"));
        var cloud =
                service().document(LedgerKind.CLOUD, source(), new RegisterQuery(), Locale.ENGLISH);
        assertEquals(List.of(10f, 40f, 70f, 70f), cloud.widths());
        assertEquals(List.of("Total Balance:", "90.00"), cloud.totals().getFirst().cells());
        assertEquals(2, cloud.notes().size());
        assertTrue(cloud.notes().getFirst().contains("SOURCE-3"));
        assertThrows(
                UnsupportedOperationException.class, () -> bulk.rows().getFirst().add("altered"));
    }

    @Test
    void filteredPettyDistinguishesCurrentBalanceFromSelectedNetMovement() {
        var query = new RegisterQuery();
        query.setFrom(LocalDate.of(2026, 1, 1));
        query.setSearch("Office");
        var display = service().document(LedgerKind.PETTY, source(), query, Locale.ENGLISH);
        assertEquals(
                List.of("Current account balance:", "900.00"), display.totals().getFirst().cells());
        assertEquals(
                List.of("Selected net movement:", "90.00"), display.totals().getLast().cells());
        assertTrue(display.preface().getFirst().text().contains("2026-01-01"));
        assertTrue(display.preface().getLast().text().contains("Office"));
        assertEquals("Out", display.rows().getFirst().get(2));
        assertEquals("In", display.rows().getLast().get(2));
    }

    @Test
    void allFourRegistersRenderOriginalPaperAndLocalizedCompleteValues() throws Exception {
        Files.createDirectories(OUTPUT);
        for (var kind : LedgerKind.values()) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
                var display = service().document(kind, source(), new RegisterQuery(), locale);
                byte[] bytes = pdf.render(display);
                var reader = new PdfReader(bytes);
                assertEquals(1, reader.getNumberOfPages());
                assertEquals(
                        kind == LedgerKind.CLOUD ? 595.28 : 841.89,
                        reader.getPageSize(1).getWidth(),
                        .01);
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertTrue(text.contains(display.title()));
                assertTrue(text.contains("2026-11-04 12:10:11 EAT | TZS"));
                assertTrue(text.contains("100.35"));
                assertTrue(text.contains("10.35"));
                if (kind == LedgerKind.CLOUD) assertTrue(text.contains(display.pageLabel() + " 1"));
                else assertTrue(text.contains("SOURCE-3"));
                if (kind != LedgerKind.PETTY) assertTrue(text.contains("90.00"));
                else assertTrue(text.contains("900.00"));
                reader.close();
                String name = kind.name().toLowerCase(Locale.ROOT) + "-" + locale.getLanguage();
                Files.write(OUTPUT.resolve("spring-" + name + ".pdf"), bytes);
                Files.writeString(
                        OUTPUT.resolve("source-" + name + ".json"),
                        new JsonMapper().writeValueAsString(display));
            }
        }
    }

    @Test
    void longRowsContinueWithHeadingsAndNoLostFinalTextOrUnicode() throws Exception {
        var rows = new ArrayList<LedgerView>();
        for (int i = 0; i < 24; i++)
            rows.add(
                    row(
                            i + 1,
                            "1.01",
                            1,
                            i + 1,
                            null,
                            null,
                            ("Long retained description ").repeat(32) + " café Ω END-" + i));
        var source =
                new LedgerReport(
                        rows,
                        new BigDecimal("24.24"),
                        BigDecimal.ZERO,
                        24,
                        0,
                        new BigDecimal("24.24"),
                        24,
                        Instant.parse("2026-11-04T09:10:11Z"));
        var display =
                service()
                        .document(
                                LedgerKind.BULK,
                                source,
                                new RegisterQuery(),
                                Locale.forLanguageTag("sw"));
        byte[] bytes = pdf.render(display);
        var reader = new PdfReader(bytes);
        assertTrue(reader.getNumberOfPages() > 10);
        var extract = new PdfTextExtractor(reader);
        var all = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            String text = extract.getTextFromPage(page);
            assertTrue(text.contains(display.printed()));
            all.append(text);
        }
        for (int i = 0; i < rows.size(); i++) assertTrue(all.toString().contains("END-" + i));
        assertTrue(all.toString().contains("café"));
        assertTrue(all.toString().contains("Ω"));
        assertTrue(all.toString().contains("24.24"));
        reader.close();
        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve("spring-long-bulk-sw.pdf"), bytes);
    }

    @Test
    void grandTotalStaysWithTheLastRowWhenTheBlockFitsOnANewPage() throws Exception {
        var rows = new ArrayList<LedgerView>();
        for (int i = 1; i <= 10; i++)
            rows.add(row(i, "1.01", 0, 0, null, null, "Short retained entry"));
        var source =
                new LedgerReport(
                        rows,
                        new BigDecimal("10.10"),
                        BigDecimal.ZERO,
                        0,
                        0,
                        new BigDecimal("10.10"),
                        0,
                        Instant.EPOCH);
        var display =
                service().document(LedgerKind.PETTY, source, new RegisterQuery(), Locale.ENGLISH);
        var reader = new PdfReader(pdf.render(display));
        assertEquals(2, reader.getNumberOfPages());
        String last = new PdfTextExtractor(reader).getTextFromPage(2);
        assertTrue(last.contains("SOURCE-10"));
        assertTrue(last.contains("Total Balance:"));
        assertTrue(last.contains("10.10"));
        reader.close();
    }

    @Test
    void emptyReportsRetainTotalsAndOversizedSourcesAreRejected() {
        var empty =
                new LedgerReport(
                        List.of(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        0,
                        BigDecimal.ZERO,
                        0,
                        Instant.EPOCH);
        assertDoesNotThrow(
                () ->
                        pdf.render(
                                service()
                                        .document(
                                                LedgerKind.CLOUD,
                                                empty,
                                                new RegisterQuery(),
                                                Locale.ENGLISH)));
        var tooMany =
                new LedgerReport(
                        Collections.nCopies(1001, source().rows().getFirst()),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        0,
                        BigDecimal.ZERO,
                        0,
                        Instant.EPOCH);
        var document =
                service().document(LedgerKind.CLOUD, tooMany, new RegisterQuery(), Locale.ENGLISH);
        assertEquals(
                "error.narrowReport",
                assertThrows(IllegalArgumentException.class, () -> pdf.render(document))
                        .getMessage());
    }
}
