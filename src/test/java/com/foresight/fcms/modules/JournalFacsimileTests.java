package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.AccountingViews.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.accounting.service.JournalDocumentService.VoucherDocument;
import com.foresight.fcms.accounting.view.AmountInWords;
import com.foresight.fcms.accounting.view.JournalStationery;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class JournalFacsimileTests {
    private static final Path OUTPUT = Path.of(".verification/journal-facsimile");

    static AccountingDocumentPdfService service() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return new AccountingDocumentPdfService(messages);
    }

    static VoucherDocument document(String kind, boolean draft, boolean longText, Locale locale) {
        var lines = new ArrayList<Line>();
        int count = longText ? 50 : 3;
        for (int i = 0; i < count; i++) {
            String description =
                    longText
                            ? ("Long retained description " + i + " ").repeat(20)
                            : "Independent line " + i;
            description =
                    description.substring(0, Math.min(470, description.length()))
                            + " FINAL-LINE-"
                            + i;
            BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
            if (longText) {
                if (i < 25) debit = new BigDecimal("1.01");
                else credit = new BigDecimal("1.01");
            } else if (i == 0) debit = new BigDecimal("100.35");
            else if (i == 1) debit = new BigDecimal("49.65");
            else credit = new BigDecimal("150.00");
            lines.add(
                    new Line(
                            10 + i,
                            "111" + String.format("%03d", i + 1),
                            longText
                                    ? ("Retained account " + i + " ")
                                            .repeat(8)
                                            .substring(
                                                    0,
                                                    Math.min(
                                                            155,
                                                            ("Retained account " + i + " ")
                                                                    .repeat(8)
                                                                    .length()))
                                    : "Retained account " + i,
                            debit,
                            credit,
                            description));
        }
        var total = longText ? new BigDecimal("25.25") : new BigDecimal("150.00");
        var journal =
                new Journal(
                        42,
                        kind,
                        "FACSIMILE-JOURNAL-42",
                        "Exact journal narration",
                        "Reviewed evidence 42",
                        LocalDate.of(2026, 11, 4),
                        1,
                        "NONE",
                        draft ? "DRAFT" : "POSTED",
                        7,
                        "Test Maker",
                        draft ? null : 8L,
                        draft ? null : "Test Checker",
                        2,
                        kind.equals("REVERSAL") ? 41L : null,
                        kind.equals("JOURNAL") ? 43L : null,
                        draft ? "" : "Independent review comment",
                        total,
                        false,
                        lines);
        return new VoucherDocument(
                journal,
                total,
                AmountInWords.amountInWords(total, locale),
                draft ? null : Instant.parse("2026-11-04T09:10:11Z"),
                List.of(),
                List.of());
    }

    static JournalStationery.Source display(
            AccountingDocumentPdfService service, VoucherDocument source, Locale locale)
            throws Exception {
        var method =
                AccountingDocumentPdfService.class.getDeclaredMethod(
                        "journalStationery", VoucherDocument.class, Locale.class);
        method.setAccessible(true);
        return (JournalStationery.Source) method.invoke(service, source, locale);
    }

    @Test
    void independentLinesRemainIndependentInsteadOfInventedPairs() throws Exception {
        var service = service();
        var source = document("JOURNAL", false, false, Locale.ENGLISH);
        var display = display(service, source, Locale.ENGLISH);
        assertEquals(3, display.rows().size());
        assertEquals(
                List.of("100.35", "49.65", ""),
                display.rows().stream().map(JournalStationery.Row::debit).toList());
        assertEquals(
                List.of("", "", "150.00"),
                display.rows().stream().map(JournalStationery.Row::credit).toList());
        for (int i = 0; i < 3; i++) {
            assertEquals(Integer.toString(i + 1), display.rows().get(i).number());
            assertEquals(
                    source.journal().lines().get(i).description(),
                    display.rows().get(i).particulars());
            assertTrue(
                    display.rows()
                            .get(i)
                            .ledger()
                            .contains(source.journal().lines().get(i).accountCode()));
        }
        assertEquals(
                List.of("150.00", "150.00", "150.00"),
                display.totals().stream().map(t -> t.value()).toList());
    }

    @Test
    void journalOpeningAndReversalUseOriginalGeometryAndCompleteProvenance() throws Exception {
        Files.createDirectories(OUTPUT);
        var service = service();
        for (String kind : List.of("JOURNAL", "OPENING", "REVERSAL")) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
                var source = document(kind, false, false, locale);
                var bytes = service.voucher(source, locale);
                var reader = new PdfReader(bytes);
                var extract = new PdfTextExtractor(reader);
                var all = new StringBuilder();
                assertEquals(595.27563, reader.getPageSize(1).getWidth(), .01);
                assertEquals(841.8898, reader.getPageSize(1).getHeight(), .01);
                for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                    String page = extract.getTextFromPage(i);
                    all.append(page);
                    assertTrue(page.contains(i + " / " + reader.getNumberOfPages()));
                    assertTrue(page.contains("FCMS"));
                }
                String text = all.toString();
                for (String value :
                        List.of(
                                "100.35",
                                "49.65",
                                "150.00",
                                "FACSIMILE-JOURNAL-42",
                                "Reviewed evidence 42",
                                "Test Maker",
                                "Test Checker",
                                "12:10:11 EAT",
                                "Independent review comment",
                                "FINAL-LINE-2"))
                    assertTrue(text.contains(value), kind + " " + locale + " " + value);
                if (kind.equals("JOURNAL")) assertTrue(text.contains("#43"));
                if (kind.equals("REVERSAL")) assertTrue(text.contains("#41"));
                assertFalse(text.contains("null"));
                assertFalse(text.contains("(TZS) (TZS)"));
                String graphics =
                        new String(
                                reader.getPageContent(1),
                                java.nio.charset.StandardCharsets.ISO_8859_1);
                assertTrue(
                        graphics.contains("523 38 re"), "Original journal table heading rectangle");
                reader.close();
                String name = kind.toLowerCase(Locale.ROOT) + "-" + locale.getLanguage();
                Files.write(OUTPUT.resolve("spring-" + name + ".pdf"), bytes);
                Files.writeString(
                        OUTPUT.resolve("source-" + name + ".json"),
                        new JsonMapper().writeValueAsString(display(service, source, locale)));
            }
        }
    }

    @Test
    void missingReviewAndPostingDataNeverBecomeInventedApprovals() throws Exception {
        var service = service();
        var source = document("JOURNAL", true, false, Locale.ENGLISH);
        var display = display(service, source, Locale.ENGLISH);
        assertEquals("", display.posting().get(1).value());
        assertEquals("", display.posting().get(2).value());
        var reader = new PdfReader(service.voucher(source, Locale.ENGLISH));
        StringBuilder text = new StringBuilder();
        var extract = new PdfTextExtractor(reader);
        for (int n = 1; n <= reader.getNumberOfPages(); n++)
            text.append(extract.getTextFromPage(n));
        assertTrue(text.toString().contains("Draft"));
        assertFalse(text.toString().contains("Test Checker"));
        assertFalse(text.toString().contains("EAT"));
        assertFalse(text.toString().contains("null"));
        reader.close();
    }

    @Test
    void fiftyLongLinesContinueWithoutLosingLastAccountOrMoney() throws Exception {
        Files.createDirectories(OUTPUT);
        var service = service();
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var source = document("JOURNAL", false, true, locale);
            var bytes = service.voucher(source, locale);
            var reader = new PdfReader(bytes);
            assertTrue(reader.getNumberOfPages() > 5);
            var text = new StringBuilder();
            var extract = new PdfTextExtractor(reader);
            for (int n = 1; n <= reader.getNumberOfPages(); n++) {
                String page = extract.getTextFromPage(n);
                text.append(page);
                assertTrue(page.contains(n + " / " + reader.getNumberOfPages()));
                assertTrue(page.contains("FCMS"));
            }
            for (int i = 0; i < 50; i++)
                assertTrue(text.toString().contains("FINAL-LINE-" + i), "Line " + i);
            assertTrue(text.toString().contains("111050"));
            assertTrue(text.toString().contains("25.25"));
            assertTrue(text.toString().contains("Independent review comment"));
            reader.close();
            String name = "long-" + locale.getLanguage();
            Files.write(OUTPUT.resolve("spring-" + name + ".pdf"), bytes);
            Files.writeString(
                    OUTPUT.resolve("source-" + name + ".json"),
                    new JsonMapper().writeValueAsString(display(service, source, locale)));
        }
    }
}
