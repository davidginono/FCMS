package com.foresight.fcms.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.category.dto.*;
import com.foresight.fcms.client.category.model.*;
import com.foresight.fcms.client.category.service.*;
import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.client.saccos.service.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class ClientExportPdfTests {
    final ClientReportPdfService stationery;
    final BigDecimal v = new BigDecimal("123.45"), z = new BigDecimal("0.00");

    ClientExportPdfTests() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        stationery = new ClientReportPdfService(messages, new ReportPdfRenderer());
    }

    @AfterEach
    void locale() {
        LocaleContextHolder.resetLocaleContext();
    }

    String text(byte[] bytes) throws Exception {
        try (var r = new PdfReader(bytes)) {
            var text = new StringBuilder();
            for (int p = 1; p <= r.getNumberOfPages(); p++)
                text.append(new PdfTextExtractor(r).getTextFromPage(p));
            return text.toString().replaceAll("\\s+", "");
        }
    }

    ClientCategoryRowDto categoryRow() {
        return new ClientCategoryRowDto(
                1L,
                "Synthetic Ω Client",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1),
                "Synthetic Region",
                "",
                v,
                v,
                z,
                z,
                z,
                v,
                v,
                v,
                v,
                v,
                v,
                1L,
                "2026",
                1L,
                "Paid");
    }

    ClientCategoryTotals categoryTotals() {
        return new ClientCategoryTotals(1, v, v, z, z, z, v, v, v, v, v, v);
    }

    @Test
    void categoryReportsPreserveExactDecimalsDatesAndUnicodeInBothLanguages() throws Exception {
        var pdf = new ClientCategoryPdfService(stationery);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (var category : ClientCategory.values()) {
                String document =
                        text(pdf.listPdf(category, List.of(categoryRow()), categoryTotals()));
                for (String value :
                        List.of("123.45", "01/11/2026", "01/11/2027", "SyntheticΩClient"))
                    assertTrue(document.contains(value), document);
                if (locale.getLanguage().equals("sw")) {
                    if (category == ClientCategory.AMCOS || category == ClientCategory.UNIONS)
                        assertTrue(document.contains("RIPOTI"), document);
                    if (category == ClientCategory.OTHERS)
                        assertTrue(document.contains("WATEJAWENGINEKWAMWAKA:2026"), document);
                    assertFalse(document.contains("legacyReport."), document);
                    assertFalse(document.contains("AnnualFee"), document);
                }
                var report =
                        new ClientCategoryService.ClientCategoryReportResult(
                                category,
                                List.of(categoryRow()),
                                categoryTotals(),
                                1L,
                                1L,
                                ClientCategoryReportType.FULL,
                                "2026",
                                "Paid");
                assertTrue(
                        text(pdf.revenuePdf(report))
                                .contains(categoryRow().grossProfit().toPlainString()));
            }
        }
    }

    @Test
    void saccosPaymentSummaryRetainsLegacyAnnualFeeSemanticsWithoutInventingDebt()
            throws Exception {
        var pdf = new SaccosPdfService(stationery);
        var row =
                new SaccosRowDto(
                        1L,
                        "Synthetic Ω Client",
                        LocalDate.of(2026, 11, 1),
                        null,
                        "Synthetic Region",
                        false,
                        new BigDecimal("200.25"),
                        v,
                        null,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        z,
                        "2026",
                        "Paid",
                        "ACTIVE",
                        "NO");
        var totals =
                new SaccosTotals(
                        1L, new BigDecimal("200.25"), v, z, z, z, z, z, z, z, z, z, z, z, z, z);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            String paid = text(pdf.paymentSummaryPdf("Paid SACCOS", List.of(row), totals, true));
            assertTrue(paid.contains("123.45"));
            String unpaid =
                    text(pdf.paymentSummaryPdf("Unpaid SACCOS", List.of(row), totals, false));
            assertTrue(unpaid.contains("200.25"));
            assertFalse(unpaid.contains("76.80"));
            String register = text(pdf.listPdf("SACCOS Register", List.of(row), totals));
            assertTrue(register.contains("SyntheticΩClient"));
            assertTrue(register.contains("01/11/2026"));
        }
    }

    @Test
    void renderLimitAlsoProtectsDirectClientExporterCalls() {
        var pdf = new ClientCategoryPdfService(stationery);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        pdf.listPdf(
                                ClientCategory.AMCOS,
                                Collections.nCopies(1001, categoryRow()),
                                categoryTotals()));
    }

    @Test
    void originalClientRegisterFixturesCoverEachDistinctLayoutInBothLanguages() throws Exception {
        class ReferenceRenderer extends ReportPdfRenderer {
            com.foresight.fcms.report.dto.OperationalDocument source;

            @Override
            public byte[] table(com.foresight.fcms.report.dto.OperationalDocument document) {
                source = document;
                return super.table(document);
            }
        }
        var renderer = new ReferenceRenderer();
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        var prepared = new ClientReportPdfService(messages, renderer);
        var categories = new ClientCategoryPdfService(prepared);
        var saccos = new SaccosPdfService(prepared);
        var row =
                new ClientCategoryRowDto(
                        1L,
                        "Client",
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2027, 11, 1),
                        "Region",
                        "",
                        v,
                        v,
                        z,
                        z,
                        z,
                        v,
                        v,
                        v,
                        v,
                        v,
                        v,
                        1L,
                        "2026",
                        1L,
                        "Paid");
        var sacco =
                new SaccosRowDto(
                        1L,
                        "Client",
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2027, 11, 1),
                        "Region",
                        false,
                        v,
                        v,
                        null,
                        z,
                        z,
                        z,
                        v,
                        v,
                        v,
                        v,
                        z,
                        v,
                        z,
                        v,
                        z,
                        z,
                        "2026",
                        "Paid",
                        "ACTIVE",
                        "NO");
        var twice = v.multiply(BigDecimal.TWO);
        var totals =
                new SaccosTotals(
                        2L, twice, twice, z, z, z, twice, twice, twice, twice, z, twice, z, twice,
                        z, z);
        var directory = java.nio.file.Path.of(".verification/client-exports");
        java.nio.file.Files.createDirectories(directory);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (String kind : List.of("SACCOS", "AMCOS", "UNIONS", "MICROFINANCE", "OTHERS")) {
                byte[] bytes =
                        kind.equals("SACCOS")
                                ? saccos.listPdf("SACCOS Register", List.of(sacco, sacco), totals)
                                : categories.listPdf(
                                        ClientCategory.valueOf(kind),
                                        List.of(row, row),
                                        categoryTotals(),
                                        "2026");
                String name = renderer.source.kind() + "-" + locale.getLanguage();
                java.nio.file.Files.writeString(
                        directory.resolve("source-" + name + ".json"),
                        new tools.jackson.databind.json.JsonMapper()
                                .writeValueAsString(renderer.source));
                java.nio.file.Files.write(
                        directory.resolve("spring-original-" + name + ".pdf"), bytes);
                assertTrue(text(bytes).contains("123.45"));
                if (kind.equals("AMCOS") || kind.equals("UNIONS"))
                    assertFalse(renderer.source.footer().totalPages());
                else assertEquals(0, renderer.source.footer().size());
                if (!kind.equals("SACCOS")) assertTrue(renderer.source.totals().isEmpty());
            }
        }
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String empty =
                text(
                        categories.listPdf(
                                ClientCategory.OTHERS, List.of(), categoryTotals(), "2025"));
        assertTrue(empty.contains("OTHERCLIENTSFORYEAR:2025"));
    }
}
