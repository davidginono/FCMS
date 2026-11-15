package com.foresight.fcms.client.saccos.model;

import com.foresight.fcms.client.saccos.dto.SaccosRowDto;
import com.foresight.fcms.client.saccos.dto.SaccosTotals;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public enum SaccosReportType {
    FULL("full", "Full Revenue Report"),
    HOSTING_FEE("hosting_fee", "Hosting Fee"),
    TRAINING_FEE("training_fee", "Training Fee"),
    SYSTEM_INSTALL_FEE("system_install_fee", "System Install Fee"),
    ANDROID_FEE("android_fee", "Android Fee"),
    REINSTALL_FEE("reinstall_fee", "System Reinstall Fee"),
    BULK_SMS("bulk_sms", "Bulk SMS");

    private final String key;
    private final String label;

    SaccosReportType(String key, String label) {
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

    public BigDecimal rowValue(SaccosRowDto row) {
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

    public BigDecimal totalValue(SaccosTotals totals) {
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

    public static SaccosReportType from(String value) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(FULL);
    }

    public static List<SaccosReportType> selectableTypes() {
        return List.of(values());
    }
}
