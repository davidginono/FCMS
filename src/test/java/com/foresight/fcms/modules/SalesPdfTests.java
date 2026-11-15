package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.sales.dto.*;
import com.foresight.fcms.sales.service.SalesPdfService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class SalesPdfTests {
    static SalesForm reference() {
        var f = new SalesForm();
        f.setDocumentNumber("RIBBON-TEST");
        f.setDocumentDate(LocalDate.of(2026, 11, 3));
        f.setValidUntil(LocalDate.of(2026, 12, 3));
        f.setClientName("Demonstration Client");
        f.setBillingAddress("P.O. Box 123");
        f.setBillingCity("Arusha");
        f.setItems(
                new ArrayList<>(
                        List.of(
                                new SalesItem(
                                        "Foresight SACCOS Software Annual Fee 2026",
                                        new BigDecimal("660000.00")),
                                new SalesItem(
                                        "Cloud Backup Hosting for 2026 (12*10,000/=)",
                                        new BigDecimal("120000.00")))));
        return f;
    }

    static SalesDocument document(String kind, SalesForm f, BigDecimal total) {
        return new SalesDocument(1, kind, f, total, 1, 1, Instant.now(), Instant.now());
    }

    @Test
    void invoiceAndProformaRetainOriginalArtworkGeometryAndDocumentMeaning() throws Exception {
        var service = new SalesPdfService();
        var dir = Path.of(".verification/sales-facsimile");
        Files.createDirectories(dir);
        for (String kind : List.of("INVOICE", "PROSPECT")) {
            byte[] bytes = service.render(document(kind, reference(), new BigDecimal("780000.00")));
            var reader = new PdfReader(bytes);
            assertEquals(1, reader.getNumberOfPages());
            assertEquals(210 * 72 / 25.4, reader.getPageSize(1).getWidth(), .02);
            assertEquals(249 * 72 / 25.4, reader.getPageSize(1).getHeight(), .02);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            for (String part :
                    List.of(
                            "RIBBON-TEST",
                            "780,000",
                            "CRDB Bank A/c No.",
                            "Office Use Only",
                            "Demonstration Client",
                            "03-Nov-2026",
                            "TOTAL")) assertTrue(text.contains(part), part);
            assertEquals(
                    kind.equals("PROSPECT"),
                    text.contains("Proforma only - not a tax invoice or receipt."));
            String graphics =
                    new String(
                            reader.getPageContent(1), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(graphics.contains(" Do"));
            assertTrue(graphics.contains("1 0 0"));
            reader.close();
            Files.write(
                    dir.resolve(
                            kind.equals("PROSPECT") ? "spring-proforma.pdf" : "spring-invoice.pdf"),
                    bytes);
        }
    }

    @Test
    void longDescriptionsAndTermsPaginateWithoutLosingMoneyOrFinalText() throws Exception {
        var f = reference();
        f.setItems(
                new ArrayList<>(
                        List.of(
                                new SalesItem(
                                        "Continuation service\n".repeat(120) + "FINAL DESCRIPTION",
                                        new BigDecimal("999999999999.99")))));
        var service = new SalesPdfService();
        byte[] bytes = service.render(document("INVOICE", f, new BigDecimal("999999999999.99")));
        var reader = new PdfReader(bytes);
        assertTrue(reader.getNumberOfPages() > 2);
        var text = new StringBuilder();
        var extract = new PdfTextExtractor(reader);
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            text.append(extract.getTextFromPage(i));
            assertTrue(extract.getTextFromPage(i).contains("RIBBON-TEST"));
        }
        assertTrue(text.toString().contains("FINAL DESCRIPTION"));
        assertTrue(text.toString().contains("999,999,999,999.99"));
        Files.write(Path.of(".verification/sales-facsimile/spring-long-invoice.pdf"), bytes);
        reader.close();
        f.setItems(new ArrayList<>(List.of(new SalesItem("Service", BigDecimal.ONE))));
        f.setTerms("Payment terms\n".repeat(90) + "FINAL TERMS");
        reader = new PdfReader(service.render(document("PROSPECT", f, BigDecimal.ONE)));
        assertTrue(reader.getNumberOfPages() > 1);
        assertTrue(
                new PdfTextExtractor(reader)
                        .getTextFromPage(reader.getNumberOfPages())
                        .contains("FINAL TERMS"));
        reader.close();
    }

    @Test
    void decimalAmountsAndJustifiedTermsRetainReferenceFormatting() throws Exception {
        var f = reference();
        f.setDocumentNumber("CENTS-TEST");
        f.setCurrency("USD");
        f.setItems(
                new ArrayList<>(
                        List.of(
                                new SalesItem("Annual fee", new BigDecimal("1234.40")),
                                new SalesItem("Cloud service", new BigDecimal("0.10")))));
        f.setTerms(
                "Annual service covers the agreed installation and support period. Payment is due by the stated date. "
                        .repeat(12));
        byte[] bytes =
                new SalesPdfService().render(document("INVOICE", f, new BigDecimal("1234.50")));
        var reader = new PdfReader(bytes);
        assertEquals(2, reader.getNumberOfPages());
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertTrue(text.contains("1,234.40"));
        assertTrue(text.contains("0.10"));
        assertTrue(text.contains("1,234.50"));
        assertTrue(new PdfTextExtractor(reader).getTextFromPage(2).contains("Terms and notes"));
        String stream =
                new String(reader.getPageContent(2), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(
                java.util.regex.Pattern.compile("[1-9][0-9.]* Tw").matcher(stream).find(),
                "Wrapped terms use the original justified paragraph layout");
        Files.write(Path.of(".verification/sales-facsimile/spring-decimal-terms.pdf"), bytes);
        reader.close();
    }
}
