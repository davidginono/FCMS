package com.foresight.fcms.client.category.model;

import com.foresight.fcms.client.category.dto.ClientCategoryRowDto;
import com.foresight.fcms.client.category.dto.ClientCategoryTotals;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public enum ClientCategoryReportType {
    FULL("full", "Full Revenue Report"),
    HOSTING_FEE("hosting_fee", "Hosting Fee"),
    TRAINING_FEE("training_fee", "Training Fee"),
    SYSTEM_INSTALL_FEE("system_install_fee", "System Install Fee"),
    ANDROID_FEE("android_fee", "Android Fee"),
    REINSTALL_FEE("reinstall_fee", "System Reinstall Fee"),
    BULK_SMS("bulk_sms", "Bulk SMS");

    private final String key;
    private final String label;

    ClientCategoryReportType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFull() {
        return this == FULL;
    }

    public boolean supports(ClientCategory category) {
        return switch (this) {
            case FULL, TRAINING_FEE, SYSTEM_INSTALL_FEE, ANDROID_FEE, REINSTALL_FEE -> true;
            case HOSTING_FEE -> category.isHostingSupported();
            case BULK_SMS -> category.isBulkSmsSupported();
        };
    }

    public BigDecimal rowValue(ClientCategoryRowDto row) {
        return switch (this) {
            case HOSTING_FEE -> row.hostingFee();
            case TRAINING_FEE -> row.trainingFee();
            case SYSTEM_INSTALL_FEE -> row.systemInstallFee();
            case ANDROID_FEE -> row.androidFee();
            case REINSTALL_FEE -> row.systemReinstallFee();
            case BULK_SMS -> row.bulkSmsFee();
            case FULL -> row.revenueTotal();
        };
    }

    public BigDecimal totalValue(ClientCategoryTotals totals) {
        return switch (this) {
            case HOSTING_FEE -> totals.hostingFee();
            case TRAINING_FEE -> totals.trainingFee();
            case SYSTEM_INSTALL_FEE -> totals.systemInstallFee();
            case ANDROID_FEE -> totals.androidFee();
            case REINSTALL_FEE -> totals.systemReinstallFee();
            case BULK_SMS -> totals.bulkSmsFee();
            case FULL -> totals.revenueTotal();
        };
    }

    public static ClientCategoryReportType from(String value, ClientCategory category) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(value.trim()))
                .filter(type -> type.supports(category))
                .findFirst()
                .orElse(FULL);
    }

    public static List<ClientCategoryReportType> selectableTypes(ClientCategory category) {
        return Arrays.stream(values()).filter(type -> type.supports(category)).toList();
    }
}
