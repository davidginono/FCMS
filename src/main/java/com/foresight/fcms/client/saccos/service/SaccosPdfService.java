package com.foresight.fcms.client.saccos.service;

import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.report.service.ClientReportPdfService;
import com.foresight.fcms.shared.view.ApplicationTimestamps;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SaccosPdfService {
    private final ClientReportPdfService pdf;

    public SaccosPdfService(ClientReportPdfService pdf) {
        this.pdf = pdf;
    }

    public byte[] listPdf(String title, List<SaccosRowDto> source, SaccosTotals totals) {
        var rows = new ArrayList<List<String>>();
        for (var r : source)
            rows.add(
                    List.of(
                            Integer.toString(rows.size() + 1),
                            r.saccosName(),
                            ApplicationTimestamps.date(r.entryDate()),
                            ApplicationTimestamps.date(r.expireDate()),
                            r.region(),
                            money(r.annualFee()),
                            money(r.amount()),
                            ApplicationTimestamps.date(r.datePaid()),
                            money(r.trainingFee()),
                            money(r.systemInstallFee()),
                            money(r.systemReinstallFee()),
                            money(r.androidFee()),
                            money(r.bulkSmsFee()),
                            r.financialYear(),
                            pdf.label(r.paymentStatus())));
        return pdf.render(
                "CLIENT_SACCOS",
                title,
                List.of(
                        "No",
                        "SACCOS Name",
                        "Entry Date",
                        "Expire Date",
                        "Region",
                        "Annual Fee",
                        "Amount Paid",
                        "Date Paid",
                        "Training Fee",
                        "Install Fee",
                        "Reinstall Fee",
                        "Android Fee",
                        "Bulk SMS",
                        "Year",
                        "Payment"),
                rows,
                List.of(
                        "Total",
                        "",
                        "",
                        "",
                        "",
                        money(totals.annualFee()),
                        money(totals.amount()),
                        "",
                        money(totals.trainingFee()),
                        money(totals.systemInstallFee()),
                        money(totals.systemReinstallFee()),
                        money(totals.androidFee()),
                        money(totals.bulkSmsFee()),
                        "",
                        ""),
                List.of(8f, 50f, 16f, 17f, 28f, 18f, 19f, 18f, 18f, 18f, 18f, 12f, 12f, 12f, 12f));
    }

    public byte[] paymentSummaryPdf(
            String title, List<SaccosRowDto> source, SaccosTotals totals, boolean paid) {
        var rows = new ArrayList<List<String>>();
        for (var r : source)
            rows.add(
                    List.of(
                            Integer.toString(rows.size() + 1),
                            r.saccosName(),
                            money(paid ? r.amount() : r.annualFee()),
                            r.financialYear()));
        return pdf.render(
                "CLIENT_PAYMENT",
                title,
                List.of("No", "SACCOS Name", paid ? "Amount Paid" : "Annual Fee", "Year"),
                rows,
                List.of("Total", "", money(paid ? totals.amount() : totals.annualFee()), ""),
                List.of(10f, 90f, 40f, 40f));
    }

    public byte[] revenuePdf(SaccosService.SaccosReportResult source) {
        var rows = new ArrayList<List<String>>();
        var type = source.reportType();
        String title =
                "SACCOS Report - "
                        + source.selectedFinancialYearLabel()
                        + " - "
                        + pdf.label(source.selectedPaymentStatusLabel());
        if (!type.isFull()) {
            for (var r : source.rows())
                rows.add(
                        List.of(
                                Integer.toString(rows.size() + 1),
                                r.saccosName(),
                                money(type.rowValue(r))));
            return pdf.render(
                    "CLIENT_SINGLE",
                    pdf.label(type.getLabel())
                            + " - "
                            + source.selectedFinancialYearLabel()
                            + " - "
                            + pdf.label(source.selectedPaymentStatusLabel()),
                    List.of("No", "SACCOS Name", type.getLabel()),
                    rows,
                    List.of("Grand Total", "", money(type.totalValue(source.totals()))),
                    List.of(15f, 190f, 60f));
        }
        for (var r : source.rows())
            rows.add(
                    List.of(
                            Integer.toString(rows.size() + 1),
                            r.saccosName(),
                            money(r.annualFee()),
                            money(r.amount()),
                            money(r.withholdingTax()),
                            money(r.commission()),
                            money(r.programmerCost()),
                            money(r.systemInstallFee()),
                            money(r.trainingFee()),
                            money(r.androidFee()),
                            money(r.androidCost()),
                            money(r.systemReinstallFee()),
                            money(r.bulkSmsFee()),
                            money(r.hostingFee()),
                            money(r.cloudBackupFee()),
                            money(r.cloudBackupCost()),
                            money(r.revenueTotal()),
                            money(r.grossProfit())));
        var t = source.totals();
        return pdf.render(
                "CLIENT_REVENUE",
                title,
                List.of(
                        "No",
                        "SACCOS Name",
                        "Annual Invoiced",
                        "Annual Paid",
                        "W-TAX",
                        "Commission",
                        "Programmer Cost",
                        "Install Fee",
                        "Training",
                        "Android Fee",
                        "Android Cost",
                        "Reinstall",
                        "Bulk SMS",
                        "Hosting",
                        "Cloud Backup",
                        "Cloud Cost",
                        "Revenue",
                        "Gross Profit"),
                rows,
                List.of(
                        "Grand Total",
                        "",
                        money(t.annualFee()),
                        money(t.amount()),
                        money(t.withholdingTax()),
                        money(t.commission()),
                        money(t.programmerCost()),
                        money(t.systemInstallFee()),
                        money(t.trainingFee()),
                        money(t.androidFee()),
                        money(t.androidCost()),
                        money(t.systemReinstallFee()),
                        money(t.bulkSmsFee()),
                        money(t.hostingFee()),
                        money(t.cloudBackupFee()),
                        money(t.cloudBackupCost()),
                        money(t.revenueTotal()),
                        money(t.grossProfit())),
                List.of(
                        8f, 45f, 14f, 14f, 12f, 14f, 16f, 15f, 14f, 14f, 14f, 16f, 14f, 14f, 16f,
                        16f, 15f, 15f));
    }

    private String money(BigDecimal value) {
        return value.toPlainString();
    }
}
