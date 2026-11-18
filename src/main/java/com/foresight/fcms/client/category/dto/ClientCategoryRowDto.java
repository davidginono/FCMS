package com.foresight.fcms.client.category.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientCategoryRowDto(
        Long id,
        String clientName,
        LocalDate entryDate,
        LocalDate expireDate,
        String region,
        String contractFile,
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
        BigDecimal hostingFee,
        Long financialYearId,
        String financialYear,
        Long paymentStatusId,
        String paymentStatus,
        String operationalStatus) {
    /** Compatibility for report fixtures without a lifecycle state. */
    public ClientCategoryRowDto(Long id, String clientName, LocalDate entryDate,
            LocalDate expireDate, String region, String contractFile, BigDecimal annualFee,
            BigDecimal amount, BigDecimal withholdingTax, BigDecimal commission,
            BigDecimal programmerCost, BigDecimal systemInstallFee, BigDecimal trainingFee,
            BigDecimal androidFee, BigDecimal systemReinstallFee, BigDecimal bulkSmsFee,
            BigDecimal hostingFee, Long financialYearId, String financialYear,
            Long paymentStatusId, String paymentStatus) {
        this(id, clientName, entryDate, expireDate, region, contractFile, annualFee, amount,
                withholdingTax, commission, programmerCost, systemInstallFee, trainingFee,
                androidFee, systemReinstallFee, bulkSmsFee, hostingFee, financialYearId,
                financialYear, paymentStatusId, paymentStatus, null);
    }
    public boolean hasContractFile() {
        return contractFile != null && !contractFile.isBlank();
    }

    public BigDecimal amountRemaining() { return annualFee.subtract(amount); }

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
