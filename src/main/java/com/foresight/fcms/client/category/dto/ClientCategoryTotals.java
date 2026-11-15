package com.foresight.fcms.client.category.dto;

import java.math.BigDecimal;

public record ClientCategoryTotals(
        long totalRows,
        BigDecimal annualFee,
        BigDecimal amount,
        BigDecimal withholdingTax,
        BigDecimal commission,
        BigDecimal programmerCost,
        BigDecimal systemInstallFee,
        BigDecimal trainingFee,
        BigDecimal androidFee,
        BigDecimal systemReinstallFee,
        BigDecimal bulkSmsFee,
        BigDecimal hostingFee) {
    public BigDecimal revenueTotal() {
        return amount.add(systemInstallFee)
                .add(trainingFee)
                .add(androidFee)
                .add(systemReinstallFee)
                .add(bulkSmsFee)
                .add(hostingFee);
    }

    public BigDecimal grossProfit() {
        return revenueTotal().subtract(programmerCost).subtract(commission);
    }
}
