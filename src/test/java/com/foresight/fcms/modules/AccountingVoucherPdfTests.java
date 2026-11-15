package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.foresight.fcms.accounting.dto.AccountingViews.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.accounting.service.JournalDocumentService.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class AccountingVoucherPdfTests {
    private static final Path OUTPUT = Path.of(".verification/voucher-facsimile");

    static AccountingDocumentPdfService service() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return new AccountingDocumentPdfService(messages);
    }

    static Journal journal(String kind, boolean longText) {
        var amount = new BigDecimal("100.35");
        String description =
                longText
                        ? "Continuation accounting description\n".repeat(130) + "FINAL PARTICULARS"
                        : "Service receipt";
        var lines =
                kind.equals("RECEIPT")
                        ? List.of(
                                new Line(
                                        1,
                                        "110001",
                                        "Bank",
                                        amount,
                                        BigDecimal.ZERO,
                                        "Cash movement"),
                                new Line(
                                        2,
                                        "410001",
                                        "Service income",
                                        BigDecimal.ZERO,
                                        new BigDecimal("110.35"),
                                        description),
                                new Line(
                                        3,
                                        "510001",
                                        "Adjustment",
                                        BigDecimal.TEN,
                                        BigDecimal.ZERO,
                                        "Retained adjustment"))
                        : List.of(
                                new Line(
                                        3,
                                        "510001",
                                        "Service expense",
                                        amount,
                                        BigDecimal.ZERO,
                                        description),
                                new Line(
                                        1,
                                        "110001",
                                        "Bank",
                                        BigDecimal.ZERO,
                                        amount,
                                        "Cash movement"));
        return new Journal(
                42,
                kind,
                "FACSIMILE-42",
                "Document narration",
                "Evidence-42",
                LocalDate.of(2026, 11, 3),
                1,
                "OPERATING",
                "POSTED",
                7,
                "Test Maker",
                8L,
                "Test Checker",
                1,
                41L,
                43L,
                "Independent review",
                kind.equals("RECEIPT") ? new BigDecimal("110.35") : amount,
                false,
                lines);
    }

    static VoucherDocument document(String kind, boolean longText, Locale locale) {
        var db = mock(JdbcTemplate.class);
        var journals = mock(JournalService.class);
        when(journals.find(42)).thenReturn(journal(kind, longText));
        when(db.queryForMap(anyString(), eq(42L)))
                .thenReturn(
                        Map.of(
                                "posted_at",
                                java.sql.Timestamp.from(Instant.parse("2026-11-03T09:10:11Z")),
                                "cash_delta",
                                new BigDecimal(kind.equals("RECEIPT") ? "100.35" : "-100.35")));
        when(db.queryForList(anyString(), eq(Long.class), eq(42L))).thenReturn(List.of(1L));
        return new JournalDocumentService(db, journals).document(42, locale);
    }

    @Test
    void receiptsReconcileSignedCounterEntriesToCashMovement() {
        var doc = document("RECEIPT", false, Locale.ENGLISH);
        assertEquals(new BigDecimal("100.35"), doc.amount());
        assertEquals(
                doc.amount(),
                doc.receiptItems().stream()
                        .map(ReceiptItem::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(
                List.of(new BigDecimal("110.35"), new BigDecimal("-10")),
                doc.receiptItems().stream().map(ReceiptItem::amount).toList());
        assertEquals(List.of("110001 - Bank"), doc.moneyAccounts());
        assertTrue(doc.words().contains("thirty-five cents"));
    }

    @Test
    void stationeryPreservesA4GeometryMoneyAndProvenanceInBothLanguages() throws Exception {
        Files.createDirectories(OUTPUT);
        var service = service();
        for (String kind : List.of("RECEIPT", "PAYMENT")) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
                var source = document(kind, false, locale);
                byte[] bytes = service.voucher(source, locale);
                var reader = new PdfReader(bytes);
                assertEquals(1, reader.getNumberOfPages());
                // OpenPDF serializes page bounds to hundredths of a point.
                assertEquals(595.27563, reader.getPageSize(1).getWidth(), .01);
                assertEquals(841.8898, reader.getPageSize(1).getHeight(), .01);
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                for (String value :
                        List.of(
                                "FCMS",
                                "FACSIMILE-42",
                                "Evidence-42",
                                "100.35",
                                "Test Maker",
                                "Test Checker",
                                "Independent review",
                                "#41",
                                "#43",
                                "1 / 1")) assertTrue(text.contains(value), value);
                assertFalse(text.contains("null"));
                assertFalse(text.contains("(TZS) (TZS)"));
                assertTrue(text.contains("12:10:11 EAT"));
                if (kind.equals("RECEIPT")) {
                    assertTrue(text.contains("110.35"));
                    assertTrue(text.contains("-10.00"));
                }
                String graphics =
                        new String(
                                reader.getPageContent(1),
                                java.nio.charset.StandardCharsets.ISO_8859_1);
                assertTrue(graphics.contains("551 35 re"), "Original full-width stationery header");
                reader.close();
                String name = kind.toLowerCase(Locale.ROOT) + "-" + locale.getLanguage();
                Files.write(OUTPUT.resolve("spring-" + name + ".pdf"), bytes);
                var method =
                        AccountingDocumentPdfService.class.getDeclaredMethod(
                                "stationery", VoucherDocument.class, Locale.class);
                method.setAccessible(true);
                Files.writeString(
                        OUTPUT.resolve("source-" + name + ".json"),
                        new JsonMapper()
                                .writeValueAsString(method.invoke(service, source, locale)));
            }
        }
    }

    @Test
    void continuationRetainsFinalTextAndCorrectPageCounts() throws Exception {
        Files.createDirectories(OUTPUT);
        var service = service();
        for (String kind : List.of("RECEIPT", "PAYMENT")) {
            var source = document(kind, true, Locale.ENGLISH);
            byte[] bytes = service.voucher(source, Locale.ENGLISH);
            var reader = new PdfReader(bytes);
            assertTrue(reader.getNumberOfPages() > 2);
            var extract = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int n = 1; n <= reader.getNumberOfPages(); n++) {
                String page = extract.getTextFromPage(n);
                assertTrue(page.contains("FCMS"));
                assertTrue(page.contains(n + " / " + reader.getNumberOfPages()));
                text.append(page);
            }
            assertTrue(text.toString().contains("FINAL PARTICULARS"));
            assertTrue(text.toString().contains("100.35"));
            reader.close();
            String name = kind.toLowerCase(Locale.ROOT) + "-long";
            Files.write(OUTPUT.resolve("spring-" + name + ".pdf"), bytes);
            var method =
                    AccountingDocumentPdfService.class.getDeclaredMethod(
                            "stationery", VoucherDocument.class, Locale.class);
            method.setAccessible(true);
            Files.writeString(
                    OUTPUT.resolve("source-" + name + ".json"),
                    new JsonMapper()
                            .writeValueAsString(method.invoke(service, source, Locale.ENGLISH)));
        }
    }
}
