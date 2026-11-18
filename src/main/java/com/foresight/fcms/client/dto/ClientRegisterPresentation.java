package com.foresight.fcms.client.dto;

import com.foresight.fcms.client.category.dto.ClientCategoryRowDto;
import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.saccos.dto.SaccosRowDto;

import java.util.*;

/** Prepared presentation data shared by the five client registers. */
public final class ClientRegisterPresentation {
    private ClientRegisterPresentation() {}

    public record Column(String key, String label, String kind) {}

    public record Row(long id, String name, boolean contract, Map<String, Object> values) {}

    private static Column text(String key, String label) {
        return new Column(key, label, "text");
    }

    private static Column money(String key) {
        return new Column(key, "client." + key, "money");
    }

    public static List<Column> columns(ClientCategory category) {
        var c = base();
        if (category.isWithholdingTaxSupported()) c.add(money("withholdingTax"));
        if (category.isCommissionSupported()) c.add(money("commission"));
        fees(c);
        if (category.isBulkSmsSupported()) c.add(money("bulkSmsFee"));
        if (category.isHostingSupported()) c.add(money("hostingFee"));
        if (category.isFinancialYearSupported()) c.add(text("financialYear", "report.year"));
        c.add(new Column("paymentStatus", "client.paymentStatus", "status"));
        if (category.isOperationalStatusSupported())
            c.add(new Column("operationalStatus", "client.operationalStatus", "status"));
        return List.copyOf(c);
    }

    public static List<Column> saccosColumns() {
        var c = base();
        c.add(new Column("datePaid", "client.datePaid", "date"));
        c.add(money("withholdingTax"));
        c.add(money("commission"));
        fees(c);
        c.add(c.size() - 1, money("androidCost"));
        c.add(money("bulkSmsFee"));
        c.add(money("hostingFee"));
        c.add(money("essFee"));
        c.add(money("cloudBackupFee"));
        c.add(money("cloudBackupCost"));
        c.add(text("financialYear", "report.year"));
        c.add(new Column("paymentStatus", "client.paymentStatus", "status"));
        c.add(new Column("invoiceStatus", "client.invoiceStatus", "status"));
        c.add(new Column("operationalStatus", "client.operationalStatus", "status"));
        return List.copyOf(c);
    }

    private static ArrayList<Column> base() {
        return new ArrayList<>(
                List.of(
                        text("name", "common.name"),
                        new Column("entryDate", "client.entryDate", "date"),
                        new Column("expireDate", "client.expireDate", "date"),
                        text("region", "client.region"),
                        new Column("contract", "client.contract", "contract"),
                        money("annualFee"),
                        new Column("amount", "client.amountPaid", "money")));
    }

    private static void fees(List<Column> c) {
        c.add(money("programmerCost"));
        c.add(money("systemInstallFee"));
        c.add(money("trainingFee"));
        c.add(money("androidFee"));
        c.add(money("systemReinstallFee"));
    }

    public static Row row(ClientCategoryRowDto r) {
        var v =
                common(
                        r.clientName(),
                        r.entryDate(),
                        r.expireDate(),
                        r.region(),
                        r.annualFee(),
                        r.amount(),
                        r.programmerCost(),
                        r.systemInstallFee(),
                        r.trainingFee(),
                        r.androidFee(),
                        r.systemReinstallFee(),
                        r.financialYear(),
                        r.paymentStatus(),
                        r.operationalStatus());
        v.put("withholdingTax", r.withholdingTax());
        v.put("commission", r.commission());
        v.put("bulkSmsFee", r.bulkSmsFee());
        v.put("hostingFee", r.hostingFee());
        return new Row(r.id(), r.clientName(), r.hasContractFile(), Collections.unmodifiableMap(v));
    }

    public static Row row(SaccosRowDto r) {
        var v =
                common(
                        r.saccosName(),
                        r.entryDate(),
                        r.expireDate(),
                        r.region(),
                        r.annualFee(),
                        r.amount(),
                        r.programmerCost(),
                        r.systemInstallFee(),
                        r.trainingFee(),
                        r.androidFee(),
                        r.systemReinstallFee(),
                        r.financialYear(),
                        r.paymentStatus(),
                        r.operationalStatus());
        v.put("datePaid", r.datePaid());
        v.put("withholdingTax", r.withholdingTax());
        v.put("commission", r.commission());
        v.put("androidCost", r.androidCost());
        v.put("bulkSmsFee", r.bulkSmsFee());
        v.put("hostingFee", r.hostingFee());
        v.put("essFee", r.essFee());
        v.put("cloudBackupFee", r.cloudBackupFee());
        v.put("cloudBackupCost", r.cloudBackupCost());
        v.put("invoiceStatus", r.invoiceStatus());
        return new Row(r.id(), r.saccosName(), r.hasContractFile(), Collections.unmodifiableMap(v));
    }

    private static Map<String, Object> common(Object... values) {
        String[] keys = {
            "name",
            "entryDate",
            "expireDate",
            "region",
            "annualFee",
            "amount",
            "programmerCost",
            "systemInstallFee",
            "trainingFee",
            "androidFee",
            "systemReinstallFee",
            "financialYear",
            "paymentStatus",
            "operationalStatus"
        };
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keys.length; i++) result.put(keys[i], values[i]);
        return result;
    }
}
