package com.foresight.fcms.client.saccos.service;

import com.foresight.fcms.client.saccos.dto.SaccosRowDto;
import com.foresight.fcms.client.saccos.dto.SaccosTotals;
import com.foresight.fcms.client.saccos.model.SaccosReportType;
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
import java.util.List;

@Service
public class SaccosPdfService {

    private static final DateTimeFormatter PRINTED_AT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public byte[] listPdf(String title, List<SaccosRowDto> rows, SaccosTotals totals) {
        return writePdf(
                PageSize.A3.rotate(),
                document -> {
                    addTitle(document, title);
                    PdfPTable table =
                            table(
                                    15,
                                    new float[] {
                                        8, 42, 18, 18, 26, 18, 18, 18, 18, 18, 18, 16, 16, 16, 18
                                    });
                    addHeader(
                            table,
                            "No",
                            "SACCOS Name",
                            "Entry Date",
                            "Expire Date",
                            "Region",
                            "Annual Fee",
                            "Amount Paid",
                            "W-TAX",
                            "Commission",
                            "Programmer Cost",
                            "Install Fee",
                            "Training",
                            "Android",
                            "Bulk SMS",
                            "Payment");
                    int index = 1;
                    for (SaccosRowDto row : rows) {
                        addCells(
                                table,
                                String.valueOf(index++),
                                text(row.saccosName()),
                                text(row.entryDate()),
                                text(row.expireDate()),
                                text(row.region()),
                                money(row.annualFee()),
                                money(row.amount()),
                                money(row.withholdingTax()),
                                money(row.commission()),
                                money(row.programmerCost()),
                                money(row.systemInstallFee()),
                                money(row.trainingFee()),
                                money(row.androidFee()),
                                money(row.bulkSmsFee()),
                                text(row.paymentStatus()));
                    }
                    addCells(
                            table,
                            "Total",
                            "",
                            "",
                            "",
                            "",
                            money(totals.annualFee()),
                            money(totals.amount()),
                            money(totals.withholdingTax()),
                            money(totals.commission()),
                            money(totals.programmerCost()),
                            money(totals.systemInstallFee()),
                            money(totals.trainingFee()),
                            money(totals.androidFee()),
                            money(totals.bulkSmsFee()),
                            "");
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    public byte[] paymentSummaryPdf(
            String title, List<SaccosRowDto> rows, SaccosTotals totals, boolean paid) {
        return writePdf(
                PageSize.A4,
                document -> {
                    addTitle(document, title);
                    PdfPTable table = table(4, new float[] {10, 72, 28, 22});
                    addHeader(
                            table,
                            "No",
                            "SACCOS Name",
                            paid ? "Amount Paid" : "Annual Fee",
                            "Year");
                    int index = 1;
                    for (SaccosRowDto row : rows) {
                        addCells(
                                table,
                                String.valueOf(index++),
                                text(row.saccosName()),
                                money(paid ? row.amount() : row.annualFee()),
                                text(row.financialYear()));
                    }
                    addCells(
                            table,
                            "Total",
                            "",
                            money(paid ? totals.amount() : totals.annualFee()),
                            "");
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    public byte[] revenuePdf(SaccosService.SaccosReportResult report) {
        if (!report.reportType().isFull()) {
            return singleColumnReportPdf(report);
        }
        return writePdf(
                PageSize.A3.rotate(),
                document -> {
                    addTitle(
                            document,
                            "SACCOS Report - "
                                    + report.selectedFinancialYearLabel()
                                    + " - "
                                    + report.selectedPaymentStatusLabel());
                    PdfPTable table =
                            table(
                                    18,
                                    new float[] {
                                        8, 40, 14, 14, 12, 14, 16, 15, 14, 14, 14, 16, 14, 14, 16,
                                        16, 16, 16
                                    });
                    addHeader(
                            table,
                            "No",
                            "SACCOS Name",
                            "Annual Invoiced",
                            "Annual Paid",
                            "W-H TAX",
                            "Commission",
                            "Programmer Cost",
                            "Install Fee",
                            "Training",
                            "Android Fee",
                            "Android Cost",
                            "Reinstall",
                            "Bulk SMS",
                            "Hosting",
                            "Cloud Fee",
                            "Cloud Cost",
                            "Revenue",
                            "Gross Profit");
                    int index = 1;
                    for (SaccosRowDto row : report.rows()) {
                        addCells(
                                table,
                                String.valueOf(index++),
                                text(row.saccosName()),
                                money(row.annualFee()),
                                money(row.amount()),
                                money(row.withholdingTax()),
                                money(row.commission()),
                                money(row.programmerCost()),
                                money(row.systemInstallFee()),
                                money(row.trainingFee()),
                                money(row.androidFee()),
                                money(row.androidCost()),
                                money(row.systemReinstallFee()),
                                money(row.bulkSmsFee()),
                                money(row.hostingFee()),
                                money(row.cloudBackupFee()),
                                money(row.cloudBackupCost()),
                                money(row.revenueTotal()),
                                money(row.grossProfit()));
                    }
                    SaccosTotals totals = report.totals();
                    addCells(
                            table,
                            "Grand Total",
                            "",
                            money(totals.annualFee()),
                            money(totals.amount()),
                            money(totals.withholdingTax()),
                            money(totals.commission()),
                            money(totals.programmerCost()),
                            money(totals.systemInstallFee()),
                            money(totals.trainingFee()),
                            money(totals.androidFee()),
                            money(totals.androidCost()),
                            money(totals.systemReinstallFee()),
                            money(totals.bulkSmsFee()),
                            money(totals.hostingFee()),
                            money(totals.cloudBackupFee()),
                            money(totals.cloudBackupCost()),
                            money(totals.revenueTotal()),
                            money(totals.grossProfit()));
                    document.add(table);
                    addPrintedAt(document);
                });
    }

    private byte[] singleColumnReportPdf(SaccosService.SaccosReportResult report) {
        SaccosReportType type = report.reportType();
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
                    PdfPTable table = table(3, new float[] {10, 72, 28});
                    addHeader(table, "No", "SACCOS Name", type.getLabel());
                    int index = 1;
                    for (SaccosRowDto row : report.rows()) {
                        addCells(
                                table,
                                String.valueOf(index++),
                                text(row.saccosName()),
                                money(type.rowValue(row)));
                    }
                    addCells(table, "Grand Total", "", money(type.totalValue(report.totals())));
                    document.add(table);
                    addPrintedAt(document);
                });
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
            throw new IllegalStateException("Could not generate SACCO PDF", exception);
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

    private PdfPTable table(int columns, float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        return table;
    }

    private void addHeader(PdfPTable table, String... headers) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(new Color(230, 236, 245));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(3);
            table.addCell(cell);
        }
    }

    private void addCells(PdfPTable table, String... values) {
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
