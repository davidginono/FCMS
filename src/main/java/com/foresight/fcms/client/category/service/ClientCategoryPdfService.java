package com.foresight.fcms.client.category.service;

import com.foresight.fcms.client.category.dto.ClientCategoryRowDto;
import com.foresight.fcms.client.category.dto.ClientCategoryTotals;
import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.category.model.ClientCategoryReportType;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClientCategoryPdfService {

    private static final DateTimeFormatter PRINTED_AT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public byte[] listPdf(
            ClientCategory category, List<ClientCategoryRowDto> rows, ClientCategoryTotals totals) {
        return writePdf(
                PageSize.A3.rotate(),
                document -> {
                    addTitle(document, category.getTitle() + " Register");
                    List<String> headers = listHeaders(category);
                    PdfPTable table = table(headers.size());
                    addHeader(table, headers);
                    int index = 1;
                    for (ClientCategoryRowDto row : rows) {
                        addCells(table, listValues(category, index++, row));
                    }
                    addCells(table, listTotals(category, totals));
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    public byte[] revenuePdf(ClientCategoryService.ClientCategoryReportResult report) {
        if (!report.reportType().isFull()) {
            return singleColumnReportPdf(report);
        }
        return writePdf(
                PageSize.A3.rotate(),
                document -> {
                    addTitle(
                            document,
                            report.category().getTitle()
                                    + " Report - "
                                    + report.selectedFinancialYearLabel()
                                    + " - "
                                    + report.selectedPaymentStatusLabel());
                    List<String> headers = revenueHeaders(report.category());
                    PdfPTable table = table(headers.size());
                    addHeader(table, headers);
                    int index = 1;
                    for (ClientCategoryRowDto row : report.rows()) {
                        addCells(table, revenueValues(report.category(), index++, row));
                    }
                    addCells(table, revenueTotals(report.category(), report.totals()));
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    private byte[] singleColumnReportPdf(ClientCategoryService.ClientCategoryReportResult report) {
        ClientCategoryReportType type = report.reportType();
        return writePdf(
                PageSize.A4,
                document -> {
                    addTitle(
                            document,
                            type.getLabel()
                                    + " - "
                                    + report.selectedFinancialYearLabel()
                                    + " - "
                                    + report.selectedPaymentStatusLabel());
                    PdfPTable table = table(3);
                    addHeader(
                            table,
                            List.of("No", report.category().getNameLabel(), type.getLabel()));
                    int index = 1;
                    for (ClientCategoryRowDto row : report.rows()) {
                        addCells(
                                table,
                                List.of(
                                        String.valueOf(index++),
                                        text(row.clientName()),
                                        money(type.rowValue(row))));
                    }
                    addCells(
                            table,
                            List.of("Grand Total", "", money(type.totalValue(report.totals()))));
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    private List<String> listHeaders(ClientCategory category) {
        List<String> headers =
                new ArrayList<>(
                        List.of(
                                "No",
                                category.getNameLabel(),
                                "Entry Date",
                                "Expire Date",
                                "Region",
                                "Annual Fee",
                                "Amount Paid"));
        if (category.isWithholdingTaxSupported()) {
            headers.add("W-H TAX");
        }
        if (category.isCommissionSupported()) {
            headers.add("Commission");
        }
        headers.addAll(
                List.of("Programmer Cost", "Install Fee", "Training", "Android", "Reinstall"));
        if (category.isBulkSmsSupported()) {
            headers.add("Bulk SMS");
        }
        if (category.isHostingSupported()) {
            headers.add("Hosting");
        }
        headers.addAll(List.of("Year", "Payment"));
        return headers;
    }

    private List<String> listValues(ClientCategory category, int index, ClientCategoryRowDto row) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                String.valueOf(index),
                                text(row.clientName()),
                                text(row.entryDate()),
                                text(row.expireDate()),
                                text(row.region()),
                                money(row.annualFee()),
                                money(row.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(money(row.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(money(row.commission()));
        }
        values.addAll(
                List.of(
                        money(row.programmerCost()),
                        money(row.systemInstallFee()),
                        money(row.trainingFee()),
                        money(row.androidFee()),
                        money(row.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(money(row.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(money(row.hostingFee()));
        }
        values.addAll(List.of(text(row.financialYear()), text(row.paymentStatus())));
        return values;
    }

    private List<String> listTotals(ClientCategory category, ClientCategoryTotals totals) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                "Total (" + totals.totalRows() + ")",
                                "",
                                "",
                                "",
                                "",
                                money(totals.annualFee()),
                                money(totals.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(money(totals.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(money(totals.commission()));
        }
        values.addAll(
                List.of(
                        money(totals.programmerCost()),
                        money(totals.systemInstallFee()),
                        money(totals.trainingFee()),
                        money(totals.androidFee()),
                        money(totals.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(money(totals.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(money(totals.hostingFee()));
        }
        values.addAll(List.of("", ""));
        return values;
    }

    private List<String> revenueHeaders(ClientCategory category) {
        List<String> headers =
                new ArrayList<>(
                        List.of("No", category.getNameLabel(), "Annual Invoiced", "Annual Paid"));
        if (category.isWithholdingTaxSupported()) {
            headers.add("W-H TAX");
        }
        if (category.isCommissionSupported()) {
            headers.add("Commission");
        }
        headers.addAll(
                List.of("Programmer Cost", "Install Fee", "Training", "Android Fee", "Reinstall"));
        if (category.isBulkSmsSupported()) {
            headers.add("Bulk SMS");
        }
        if (category.isHostingSupported()) {
            headers.add("Hosting");
        }
        headers.addAll(List.of("Revenue", "Gross Profit"));
        return headers;
    }

    private List<String> revenueValues(
            ClientCategory category, int index, ClientCategoryRowDto row) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                String.valueOf(index),
                                text(row.clientName()),
                                money(row.annualFee()),
                                money(row.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(money(row.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(money(row.commission()));
        }
        values.addAll(
                List.of(
                        money(row.programmerCost()),
                        money(row.systemInstallFee()),
                        money(row.trainingFee()),
                        money(row.androidFee()),
                        money(row.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(money(row.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(money(row.hostingFee()));
        }
        values.addAll(List.of(money(row.revenueTotal()), money(row.grossProfit())));
        return values;
    }

    private List<String> revenueTotals(ClientCategory category, ClientCategoryTotals totals) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                "Grand Total",
                                "",
                                money(totals.annualFee()),
                                money(totals.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(money(totals.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(money(totals.commission()));
        }
        values.addAll(
                List.of(
                        money(totals.programmerCost()),
                        money(totals.systemInstallFee()),
                        money(totals.trainingFee()),
                        money(totals.androidFee()),
                        money(totals.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(money(totals.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(money(totals.hostingFee()));
        }
        values.addAll(List.of(money(totals.revenueTotal()), money(totals.grossProfit())));
        return values;
    }

    private byte[] writePdf(com.lowagie.text.Rectangle pageSize, PdfBody body) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(pageSize, 16, 16, 18, 18);
            PdfWriter.getInstance(document, output);
            document.open();
            body.write(document);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not generate client category PDF", exception);
        }
    }

    private void addTitle(Document document, String title) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Paragraph paragraph = new Paragraph(title, titleFont);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(8);
        document.add(paragraph);
    }

    private void addPrintedAt(Document document) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7);
        Paragraph printed =
                new Paragraph("Printed: " + LocalDateTime.now().format(PRINTED_AT), font);
        printed.setAlignment(Element.ALIGN_CENTER);
        printed.setSpacingBefore(8);
        document.add(printed);
    }

    private PdfPTable table(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private void addHeader(PdfPTable table, List<String> headers) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(new Color(230, 236, 245));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(3);
            table.addCell(cell);
        }
    }

    private void addCells(PdfPTable table, List<String> values) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 6);
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font));
            cell.setPadding(3);
            table.addCell(cell);
        }
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.toPlainString();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    @FunctionalInterface
    private interface PdfBody {
        void write(Document document) throws DocumentException;
    }
}
