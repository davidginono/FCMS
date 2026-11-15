package com.foresight.fcms.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.category.dto.*;
import com.foresight.fcms.client.category.model.*;
import com.foresight.fcms.client.category.service.*;
import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.client.saccos.model.SaccosReportType;
import com.foresight.fcms.client.saccos.service.*;
import com.foresight.fcms.report.dto.OperationalDocument;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class ClientRevenueStationeryTests {
    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    static BigDecimal twice(String value) {
        return n(value).multiply(BigDecimal.TWO);
    }

    static final Path ROOT = Path.of(".verification/client-revenue-exports");

    static class ProofRenderer extends ReportPdfRenderer {
        OperationalDocument source;

        @Override
        public byte[] table(OperationalDocument source) {
            this.source = source;
            return super.table(source);
        }
    }

    final ProofRenderer renderer = new ProofRenderer();
    final ClientReportPdfService stationery;
    final SaccosPdfService saccos;
    final ClientCategoryPdfService categories;

    ClientRevenueStationeryTests() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        stationery =
                new ClientReportPdfService(
                        messages,
                        renderer,
                        Clock.fixed(Instant.parse("2026-11-01T07:15:09Z"), ZoneOffset.UTC));
        saccos = new SaccosPdfService(stationery);
        categories = new ClientCategoryPdfService(stationery);
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    SaccosRowDto sacco() {
        return new SaccosRowDto(
                1L,
                "Client",
                LocalDate.of(2026, 11, 1),
                null,
                "Region",
                false,
                n("200.25"),
                n("123.45"),
                null,
                n("1.01"),
                n("2.02"),
                n("3.03"),
                n("4.04"),
                n("5.05"),
                n("6.06"),
                n("0.60"),
                n("7.07"),
                n("8.08"),
                n("9.09"),
                n("10.10"),
                n("11.11"),
                n("1.11"),
                "2026",
                "PAID",
                "ACTIVE",
                "NO");
    }

    SaccosTotals totals() {
        return new SaccosTotals(
                2L,
                twice("200.25"),
                twice("123.45"),
                twice("1.01"),
                twice("2.02"),
                twice("3.03"),
                twice("4.04"),
                twice("5.05"),
                twice("6.06"),
                twice("0.60"),
                twice("7.07"),
                twice("8.08"),
                twice("9.09"),
                twice("10.10"),
                twice("11.11"),
                twice("1.11"));
    }

    String save(byte[] bytes, String name) throws Exception {
        Files.createDirectories(ROOT);
        Files.write(ROOT.resolve("spring-original-" + name + ".pdf"), bytes);
        Files.writeString(
                ROOT.resolve("source-" + name + ".json"),
                new tools.jackson.databind.json.JsonMapper().writeValueAsString(renderer.source));
        try (var reader = new PdfReader(bytes)) {
            var text = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++)
                text.append(new PdfTextExtractor(reader).getTextFromPage(p));
            return text.toString().replaceAll("\\s+", "");
        }
    }

    @Test
    void paidAndUnpaidUseOriginalPrintedFooterAndYearHeadingInBothLanguages() throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (boolean paid : List.of(true, false)) {
                String text =
                        save(
                                saccos.paymentSummaryPdf(
                                        paid ? "Paid SACCOS - 2026" : "Unpaid SACCOS",
                                        List.of(sacco(), sacco()),
                                        totals(),
                                        paid),
                                "CLIENT_PAYMENT_"
                                        + (paid ? "PAID" : "UNPAID")
                                        + "-"
                                        + locale.getLanguage());
                assertTrue(text.contains(paid ? "246.90" : "400.50"));
                assertTrue(text.contains("01Nov202610:15:09"));
                assertTrue(
                        text.contains(
                                locale.getLanguage().equals("sw") ? "Imechapishwa" : "Printed"));
                assertEquals(9, renderer.source.footer().size());
                assertTrue(renderer.source.footer().totalPages());
                if (paid) assertTrue(renderer.source.title().contains("2026"));
                assertFalse(text.contains("153.60"));
            }
        }
    }

    @Test
    void fullAndSingleColumnRevenueFixturesRetainActualRevenueAndDistinctSourceStyles()
            throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (boolean full : List.of(true, false)) {
                var report =
                        new SaccosService.SaccosReportResult(
                                List.of(sacco(), sacco()),
                                totals(),
                                1L,
                                1L,
                                full ? SaccosReportType.FULL : SaccosReportType.TRAINING_FEE,
                                "2026",
                                "PAID");
                String text =
                        save(
                                saccos.revenuePdf(report),
                                full
                                        ? "CLIENT_REVENUE-" + locale.getLanguage()
                                        : "CLIENT_SINGLE-SACCOS-" + locale.getLanguage());
                assertTrue(text.contains(full ? totals().grossProfit().toPlainString() : "10.10"));
                assertEquals(0, renderer.source.footer().size());
            }
            for (var category : ClientCategory.values()) {
                boolean wide =
                        category == ClientCategory.AMCOS || category == ClientCategory.UNIONS;
                BigDecimal bulk = wide ? n("8.08") : BigDecimal.ZERO,
                        hosting = wide ? n("9.09") : BigDecimal.ZERO,
                        commission = wide ? n("2.02") : BigDecimal.ZERO;
                var row =
                        new ClientCategoryRowDto(
                                1L,
                                "Client",
                                null,
                                null,
                                "Region",
                                "",
                                n("200.25"),
                                n("123.45"),
                                n("1.01"),
                                commission,
                                n("3.03"),
                                n("4.04"),
                                n("5.05"),
                                n("6.06"),
                                n("7.07"),
                                bulk,
                                hosting,
                                1L,
                                "2026",
                                1L,
                                "PAID");
                var totals =
                        new ClientCategoryTotals(
                                2,
                                twice("200.25"),
                                twice("123.45"),
                                twice("1.01"),
                                commission.multiply(BigDecimal.TWO),
                                twice("3.03"),
                                twice("4.04"),
                                twice("5.05"),
                                twice("6.06"),
                                twice("7.07"),
                                bulk.multiply(BigDecimal.TWO),
                                hosting.multiply(BigDecimal.TWO));
                for (boolean full : List.of(true, false)) {
                    var report =
                            new ClientCategoryService.ClientCategoryReportResult(
                                    category,
                                    List.of(row, row),
                                    totals,
                                    1L,
                                    1L,
                                    full
                                            ? ClientCategoryReportType.FULL
                                            : ClientCategoryReportType.TRAINING_FEE,
                                    "2026",
                                    "PAID");
                    String name =
                            full
                                    ? "CLIENT_"
                                            + category.name()
                                            + "_REVENUE-"
                                            + locale.getLanguage()
                                    : "CLIENT_SINGLE-"
                                            + category.name()
                                            + "-"
                                            + locale.getLanguage();
                    String text = save(categories.revenuePdf(report), name);
                    assertTrue(
                            text.contains(full ? totals.grossProfit().toPlainString() : "10.10"));
                    assertEquals(0, renderer.source.footer().size());
                    if (full)
                        assertFalse(
                                text.contains(
                                        totals.grossProfit()
                                                .add(totals.annualFee().subtract(totals.amount()))
                                                .toPlainString()));
                }
            }
        }
    }
}
