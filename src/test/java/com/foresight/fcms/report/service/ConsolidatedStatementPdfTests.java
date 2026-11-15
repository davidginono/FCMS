package com.foresight.fcms.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.service.AccountingDocumentPdfService;
import com.foresight.fcms.client.service.ClientRevenueService.Revenue;
import com.foresight.fcms.finance.report.service.FinancialReportService.Statement;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

class ConsolidatedStatementPdfTests {
    private ResourceBundleMessageSource messages() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return messages;
    }

    @Test
    void microfinanceIncomeFormatPreservesSignedTotalsAndClientMarginsInBothLanguages()
            throws Exception {
        var messages = messages();
        var service =
                new ConsolidatedStatementPdfService(
                        messages, new AccountingDocumentPdfService(messages));
        var statement =
                new Statement(
                        2026,
                        List.of(
                                revenue("saccos", "100.35", "10.25"),
                                revenue("unions", "80.10", "1.10"),
                                revenue("amcos", "50.35", "5.15"),
                                revenue("microfinance_clients", "1.50", "5.90"),
                                revenue("other_clients", "2.00", "1.25")),
                        new BigDecimal("-19.00"),
                        new BigDecimal("215.13"),
                        new BigDecimal("-23.48"));
        assertEquals(new BigDecimal("215.30"), statement.totalIncome());
        assertEquals(new BigDecimal("238.78"), statement.totalExpenses());
        assertEquals(
                statement.netProfit(), statement.totalIncome().subtract(statement.totalExpenses()));
        var folder = Path.of(".verification/statements-facsimile");
        Files.createDirectories(folder);
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var generated = Instant.parse("2026-11-04T09:10:11Z");
            var document = service.document(statement, locale, generated);
            assertFalse(document.landscape());
            assertEquals("statement", document.style());
            assertEquals(5, document.columns().size());
            assertEquals(
                    List.of(
                            messages.getMessage("gl.income", null, locale),
                            messages.getMessage("gl.expenses", null, locale)),
                    document.rows().stream()
                            .filter(row -> row.heading())
                            .map(row -> row.label())
                            .toList());
            assertEquals("FCMS", document.header().identity());
            assertEquals(
                    (locale.getLanguage().equals("sw") ? "Kipindi kilichopita" : "Previous period")
                            + ": 01/01/2025 — 31/12/2025",
                    document.header().previous());
            assertTrue(
                    document.rows().stream()
                            .filter(row -> !row.heading())
                            .allMatch(
                                    row ->
                                            row.cells()
                                                    .getLast()
                                                    .equals(
                                                            locale.getLanguage().equals("sw")
                                                                    ? "Haipatikani"
                                                                    : "Unavailable")));
            assertEquals(
                    messages.getMessage("gl.presentation.account", null, locale),
                    document.columns().getFirst().label());
            var bytes = service.render(statement, locale, generated);
            try (var reader = new PdfReader(bytes)) {
                assertEquals(2, reader.getNumberOfPages());
                assertEquals(595.27563f, reader.getPageSize(1).getWidth(), .00001);
                assertEquals(841.8898f, reader.getPageSize(1).getHeight(), .00001);
                var mediaBox = reader.getPageN(1).getAsArray(PdfName.MEDIABOX);
                assertEquals("595.27563", mediaBox.getAsNumber(2).toString());
                assertEquals("841.8898", mediaBox.getAsNumber(3).toString());
                var text = new PdfTextExtractor(reader);
                String first = text.getTextFromPage(1), detail = text.getTextFromPage(2);
                for (String amount : List.of("215.30", "238.78", "(23.48)"))
                    assertTrue(first.contains(amount), amount);
                assertTrue(first.contains("01/01/2026"));
                assertTrue(first.contains("31/12/2026"));
                assertTrue(first.contains("1 / 2"));
                assertTrue(detail.contains("2 / 2"));
                for (var client : statement.clients())
                    assertTrue(
                            detail.contains(
                                    messages.getMessage(
                                            "client.category." + client.category(), null, locale)));
                for (String amount :
                        List.of(
                                "100.35", "10.25", "90.10", "80.10", "1.10", "79.00", "50.35",
                                "5.15", "45.20", "1.50", "5.90", "(4.40)", "2.00", "1.25", "0.75",
                                "(19.00)", "215.13", "(23.48)"))
                    assertTrue(detail.contains(amount), amount);
                String title =
                        locale.getLanguage().equals("sw")
                                ? "TAARIFA YA MAPATO"
                                : "INCOME STATEMENT";
                assertTrue(first.contains(title), first);
                assertFalse(
                        new String(
                                        reader.getPageContent(1),
                                        java.nio.charset.StandardCharsets.ISO_8859_1)
                                .contains(" rg"));
            }
            String name = "consolidated-" + locale.getLanguage();
            Files.write(folder.resolve("spring-" + name + ".pdf"), bytes);
            Files.writeString(
                    folder.resolve("source-" + name + ".json"),
                    new JsonMapper().writeValueAsString(document));
        }
    }

    private Revenue revenue(String category, String gross, String cost) {
        return new Revenue(category, 1, new BigDecimal(gross), new BigDecimal(cost));
    }

    @Test
    void emptyYearsAndLargeAggregateAmountsRetainTheirExactValues() throws Exception {
        var messages = messages();
        var service =
                new ConsolidatedStatementPdfService(
                        messages, new AccountingDocumentPdfService(messages));
        var folder = Path.of(".verification/consolidated-statement-facsimile");
        Files.createDirectories(folder);
        for (String value : List.of("0.00", "1000000000000000000000.01")) {
            var amount = new BigDecimal(value);
            var statement = new Statement(2000, List.of(), amount, BigDecimal.ZERO, amount);
            var bytes = service.render(statement, Locale.ENGLISH, Instant.EPOCH);
            try (var reader = new PdfReader(bytes)) {
                assertEquals(2, reader.getNumberOfPages());
                var extractor = new PdfTextExtractor(reader);
                String expected =
                        value.equals("0.00") ? "0.00" : "1,000,000,000,000,000,000,000.01";
                assertTrue(extractor.getTextFromPage(1).contains(expected));
                assertTrue(extractor.getTextFromPage(2).contains(expected));
                assertTrue(extractor.getTextFromPage(1).contains("01/01/2000"));
            }
            Files.write(
                    folder.resolve(amount.signum() == 0 ? "edge-empty.pdf" : "edge-large.pdf"),
                    bytes);
            var references = Path.of(".verification/statements-facsimile");
            Files.createDirectories(references);
            String name = amount.signum() == 0 ? "consolidated-empty-en" : "consolidated-large-en";
            Files.write(references.resolve("spring-" + name + ".pdf"), bytes);
            Files.writeString(
                    references.resolve("source-" + name + ".json"),
                    new JsonMapper()
                            .writeValueAsString(
                                    service.document(statement, Locale.ENGLISH, Instant.EPOCH)));
        }
    }
}
