package com.foresight.fcms.client.category.service;

import com.foresight.fcms.client.category.dto.*;
import com.foresight.fcms.client.category.model.*;
import com.foresight.fcms.report.service.ClientReportPdfService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ClientCategoryPdfService {
    private final ClientReportPdfService pdf;

    public ClientCategoryPdfService(ClientReportPdfService pdf) {
        this.pdf = pdf;
    }

    public byte[] listPdf(
            ClientCategory category,
            List<ClientCategoryRowDto> source,
            ClientCategoryTotals totals) {
        return listPdf(
                category,
                source,
                totals,
                source.isEmpty() ? "All Years" : source.getFirst().financialYear());
    }

    public byte[] listPdf(
            ClientCategory category,
            List<ClientCategoryRowDto> source,
            ClientCategoryTotals totals,
            String yearLabel) {
        var rows = new ArrayList<List<String>>();
        if (category == ClientCategory.MICROFINANCE || category == ClientCategory.OTHERS) {
            for (var row : source)
                rows.add(
                        List.of(
                                Integer.toString(rows.size() + 1),
                                row.clientName(),
                                text(row.entryDate()),
                                text(row.expireDate()),
                                row.region(),
                                money(row.annualFee()),
                                money(row.amount()),
                                money(row.trainingFee()),
                                money(row.systemInstallFee()),
                                money(row.systemReinstallFee()),
                                money(row.androidFee()),
                                text(row.financialYear()),
                                pdf.label(row.paymentStatus())));
            return pdf.render(
                    "CLIENT_" + category.name() + "_REGISTER",
                    category == ClientCategory.OTHERS
                            ? pdf.label("client.otherRegisterYear") + " " + pdf.label(yearLabel)
                            : category.getTitle() + " Register",
                    List.of(
                            "No",
                            category.getNameLabel(),
                            "Entry Date",
                            "Expire Date",
                            "Region",
                            "Annual Fee",
                            "Amount Paid",
                            "Training Fee",
                            "Install Fee",
                            "Reinstall Fee",
                            "Android Fee",
                            "Year",
                            "Payment"),
                    rows,
                    List.of(),
                    category == ClientCategory.MICROFINANCE
                            ? List.of(
                                    8f, 52f, 18f, 19f, 28f, 18f, 21f, 19f, 21f, 21f, 20f, 14f, 14f)
                            : List.of(
                                    10f, 52f, 18f, 19f, 30f, 20f, 21f, 20f, 18f, 22f, 20f, 13f,
                                    13f));
        }
        for (var row : source) rows.add(listValues(category, rows.size() + 1, row));
        var headers = listHeaders(category);
        var widths =
                category == ClientCategory.AMCOS
                        ? List.of(
                                8f, 42f, 17f, 17f, 17f, 13f, 16f, 16f, 18f, 18f, 16f, 15f, 18f, 14f,
                                15f, 13f, 17f)
                        : List.of(
                                8f, 38f, 16f, 16f, 16f, 12f, 15f, 15f, 13f, 14f, 16f, 16f, 15f, 14f,
                                17f, 13f, 14f, 13f, 14f);
        return pdf.render(
                "CLIENT_" + category.name() + "_REGISTER",
                pdf.title(category.getTitle() + " Report").toUpperCase(java.util.Locale.ROOT),
                headers,
                rows,
                List.of(),
                widths);
    }

    public byte[] revenuePdf(ClientCategoryService.ClientCategoryReportResult source) {
        var rows = new ArrayList<List<String>>();
        var category = source.category();
        var type = source.reportType();
        String title =
                category.getTitle()
                        + " Report - "
                        + source.selectedFinancialYearLabel()
                        + " - "
                        + pdf.label(source.selectedPaymentStatusLabel());
        if (!type.isFull()) {
            for (var row : source.rows())
                rows.add(
                        List.of(
                                Integer.toString(rows.size() + 1),
                                row.clientName(),
                                money(type.rowValue(row))));
            return pdf.render(
                    "CLIENT_SINGLE",
                    pdf.label(type.getLabel())
                            + " - "
                            + source.selectedFinancialYearLabel()
                            + " - "
                            + pdf.label(source.selectedPaymentStatusLabel()),
                    List.of("No", category.getNameLabel(), type.getLabel()),
                    rows,
                    List.of("Grand Total", "", money(type.totalValue(source.totals()))),
                    List.of(15f, 190f, 60f));
        }
        for (var row : source.rows()) rows.add(revenueValues(category, rows.size() + 1, row));
        var headers = revenueHeaders(category);
        return pdf.render(
                "CLIENT_" + category.name() + "_REVENUE",
                title,
                headers,
                rows,
                revenueTotals(category, source.totals()),
                (category == ClientCategory.MICROFINANCE || category == ClientCategory.OTHERS)
                        ? List.of(10f, 62f, 20f, 30f, 30f, 20f, 20f, 20f, 20f, 20f, 20f)
                        : category == ClientCategory.AMCOS
                        ? List.of(8f, 45f, 16f, 16f, 18f, 18f, 16f, 16f, 18f, 15f, 15f, 16f, 15f)
                        : List.of(
                                8f, 45f, 16f, 16f, 14f, 15f, 18f, 18f, 16f, 16f, 18f, 15f, 15f, 16f,
                                15f));
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
                                "Contract",
                                "Annual Fee",
                                "Amount Paid"));
        if (category == ClientCategory.UNIONS) {
            headers.add("W-H TAX");
        }
        if (category == ClientCategory.UNIONS) {
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
                                pdf.label(row.hasContractFile() ? "common.yes" : "common.no"),
                                money(row.annualFee()),
                                money(row.amount())));
        if (category == ClientCategory.UNIONS) {
            values.add(money(row.withholdingTax()));
        }
        if (category == ClientCategory.UNIONS) {
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
        values.addAll(List.of(text(row.financialYear()), pdf.label(text(row.paymentStatus()))));
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

    private String money(BigDecimal value) {
        return value.toPlainString();
    }

    private String text(Object value) {
        if (value instanceof LocalDate date)
            return com.foresight.fcms.shared.view.ApplicationTimestamps.date(date);
        return value == null ? "" : value.toString();
    }
}
