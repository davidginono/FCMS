package com.foresight.fcms.client.saccos.dto;

import java.math.BigDecimal;

public record SaccosTotals(
        Long totalRows,
        BigDecimal annualFee,
        BigDecimal amount,
        BigDecimal withholdingTax,
        BigDecimal commission,
        BigDecimal programmerCost,
        BigDecimal systemInstallFee,
        BigDecimal trainingFee,
        BigDecimal androidFee,
        BigDecimal androidCost,
        BigDecimal systemReinstallFee,
        BigDecimal bulkSmsFee,
        BigDecimal hostingFee,
        BigDecimal essFee,
        BigDecimal cloudBackupFee,
        BigDecimal cloudBackupCost) {
    public BigDecimal revenueTotal() {
        return amount.add(systemInstallFee)
                .add(trainingFee)
                .add(androidFee)
                .add(systemReinstallFee)
                .add(hostingFee)
                .add(bulkSmsFee)
                .add(cloudBackupFee);
    }

    public BigDecimal grossProfit() {
        return revenueTotal()
                .subtract(programmerCost)
                .subtract(commission)
                .subtract(cloudBackupCost)
                .subtract(androidCost);
    }
}
