package com.foresight.fcms.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.category.dto.*;
import com.foresight.fcms.client.category.model.*;
import com.foresight.fcms.client.category.service.*;
import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.client.saccos.service.*;
import com.foresight.fcms.report.dto.OperationalDocument;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

class ClientExportEdgeTests {
    static class ProofRenderer extends ReportPdfRenderer {
        OperationalDocument source;

        @Override
        public byte[] table(OperationalDocument value) {
            source = value;
            return super.table(value);
        }
    }

    final ProofRenderer renderer = new ProofRenderer();
    final ClientReportPdfService stationery;
    final SaccosPdfService saccos;
    final ClientCategoryPdfService categories;
    final BigDecimal zero = new BigDecimal("0.00");
    final SaccosTotals saccosZero =
            new SaccosTotals(
                    0L, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                    zero, zero, zero);
    final ClientCategoryTotals categoryZero =
            new ClientCategoryTotals(
                    0, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero);

    ClientExportEdgeTests() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        stationery = new ClientReportPdfService(messages, renderer);
        saccos = new SaccosPdfService(stationery);
        categories = new ClientCategoryPdfService(stationery);
    }

    @AfterEach
    void locale() {
        LocaleContextHolder.resetLocaleContext();
    }

    String text(byte[] bytes) throws Exception {
        try (var reader = new PdfReader(bytes)) {
            var result = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++)
                result.append(new PdfTextExtractor(reader).getTextFromPage(page));
            return result.toString();
        }
    }

    @Test
    void emptyRegisterFixturesRetainEachOriginalCaptionAndAbsenceOfSyntheticTotals()
            throws Exception {
        var directory = Path.of(".verification/client-empty-exports");
        Files.createDirectories(directory);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (String kind : List.of("SACCOS", "AMCOS", "UNIONS", "MICROFINANCE", "OTHERS")) {
                byte[] bytes =
                        kind.equals("SACCOS")
                                ? saccos.listPdf("SACCOS Register", List.of(), saccosZero)
                                : categories.listPdf(
                                        ClientCategory.valueOf(kind),
                                        List.of(),
                                        categoryZero,
                                        "2026");
                var source = renderer.source;
                String name = source.kind() + "-empty-" + locale.getLanguage();
                Files.writeString(
                        directory.resolve("source-" + name + ".json"),
                        new tools.jackson.databind.json.JsonMapper().writeValueAsString(source));
                Files.write(directory.resolve("spring-original-" + name + ".pdf"), bytes);
                assertTrue(source.totals().isEmpty());
                assertFalse(text(bytes).contains("0.00"));
                if (List.of("SACCOS", "UNIONS", "OTHERS").contains(kind))
                    assertTrue(text(bytes).contains(source.emptyMessage()));
                else assertTrue(source.emptyMessage().isEmpty());
            }
        }
    }

    void saveEmptyRevenue(String name, byte[] bytes) throws Exception {
        var directory = Path.of(".verification/client-empty-revenues");
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("source-" + name + ".json"),
                new tools.jackson.databind.json.JsonMapper().writeValueAsString(renderer.source));
        Files.write(directory.resolve("spring-original-" + name + ".pdf"), bytes);
        assertTrue(renderer.source.rows().isEmpty());
        assertTrue(text(bytes).contains("0.00"));
        assertFalse(text(bytes).contains("null"));
    }

    @Test
    void emptyRevenueAndPaymentFixturesKeepExactZeroTotalsWithoutInventedClients()
            throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            String suffix = "-empty-" + locale.getLanguage();
            for (boolean paid : List.of(true, false))
                saveEmptyRevenue(
                        "CLIENT_PAYMENT_" + (paid ? "PAID" : "UNPAID") + suffix,
                        saccos.paymentSummaryPdf(
                                paid ? "Paid SACCOS - 2026" : "Unpaid SACCOS",
                                List.of(),
                                saccosZero,
                                paid));
            for (boolean full : List.of(true, false)) {
                var report =
                        new SaccosService.SaccosReportResult(
                                List.of(),
                                saccosZero,
                                1L,
                                1L,
                                full
                                        ? com.foresight.fcms.client.saccos.model.SaccosReportType
                                                .FULL
                                        : com.foresight.fcms.client.saccos.model.SaccosReportType
                                                .TRAINING_FEE,
                                "2026",
                                "PAID");
                saveEmptyRevenue(
                        (full ? "CLIENT_REVENUE" : "CLIENT_SINGLE-SACCOS") + suffix,
                        saccos.revenuePdf(report));
                for (var category : ClientCategory.values()) {
                    var other =
                            new ClientCategoryService.ClientCategoryReportResult(
                                    category,
                                    List.of(),
                                    categoryZero,
                                    1L,
                                    1L,
                                    full
                                            ? ClientCategoryReportType.FULL
                                            : ClientCategoryReportType.TRAINING_FEE,
                                    "2026",
                                    "PAID");
                    saveEmptyRevenue(
                            (full
                                            ? "CLIENT_" + category.name() + "_REVENUE"
                                            : "CLIENT_SINGLE-" + category.name())
                                    + suffix,
                            categories.revenuePdf(other));
                }
            }
        }
    }

    @Test
    void wrappedMultilingualRowsAndTotalsRemainCompleteAcrossContinuationPages() throws Exception {
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            LocaleContextHolder.setLocale(locale);
            for (String kind :
                    List.of(
                            "CLIENT_SACCOS",
                            "CLIENT_AMCOS_REGISTER",
                            "CLIENT_UNIONS_REGISTER",
                            "CLIENT_MICROFINANCE_REGISTER",
                            "CLIENT_OTHERS_REGISTER",
                            "CLIENT_REVENUE",
                            "CLIENT_UNIONS_REVENUE",
                            "CLIENT_AMCOS_REVENUE",
                            "CLIENT_MICROFINANCE_REVENUE",
                            "CLIENT_OTHERS_REVENUE",
                            "CLIENT_SINGLE",
                            "CLIENT_PAYMENT")) {
                var rows = new ArrayList<List<String>>();
                for (int i = 0; i < 130; i++)
                    rows.add(
                            List.of(
                                    String.valueOf(i + 1),
                                    "Mteja wa Ushirika Ω & <literal> ".repeat(6) + "Mwisho" + i,
                                    "123.45"));
                byte[] bytes =
                        stationery.render(
                                kind,
                                "Synthetic Report - 2026",
                                List.of("No", "Name", "Amount Paid"),
                                rows,
                                List.of("Total", "", "16,048.50"),
                                List.of(10f, kind.equals("CLIENT_PAYMENT") ? 90f : 42f, 40f));
                String content = text(bytes).replaceAll("\\s+", "");
                for (int i = 0; i < 130; i++)
                    assertTrue(content.contains("Mwisho" + i), kind + ":" + i);
                assertTrue(content.contains("Ω&<literal>"), kind);
                assertTrue(content.contains("16,048.50"), kind);
                try (var reader = new PdfReader(bytes)) {
                    assertTrue(reader.getNumberOfPages() > 1, kind);
                    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                        var pageText =
                                new PdfTextExtractor(reader)
                                        .getTextFromPage(page)
                                        .replaceAll("\\s+", "");
                        assertTrue(
                                pageText.contains(
                                        stationery.label("Amount Paid").replaceAll("\\s+", "")),
                                kind + ":" + page);
                    }
                }
                var directory = Path.of(".verification/client-long-exports");
                Files.createDirectories(directory);
                Files.write(directory.resolve(kind + "-" + locale.getLanguage() + ".pdf"), bytes);
            }
        }
    }
}
