package com.foresight.fcms.client.saccos.dto;

import com.foresight.fcms.client.saccos.model.Saccos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaccosRowDto(
        Long id,
        String saccosName,
        LocalDate entryDate,
        LocalDate expireDate,
        String region,
        boolean hasContractFile,
        BigDecimal annualFee,
        BigDecimal amount,
        LocalDate datePaid,
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
        BigDecimal cloudBackupCost,
        String financialYear,
        String paymentStatus,
        String operationalStatus,
        String invoiceStatus) {
    public static SaccosRowDto from(Saccos saccos) {
        String paymentStatus = saccos.getStatus() == null ? "" : saccos.getStatus().getName();
        String financialYear =
                saccos.getFinancialYear() == null ? "" : saccos.getFinancialYear().getFyear();
        return new SaccosRowDto(
                saccos.getId(),
                saccos.getSaccosName(),
                saccos.getEntryDate(),
                saccos.getExpireDate(),
                saccos.getRegion(),
                saccos.hasContractFile(),
                saccos.getAnnualFee(),
                saccos.getAmount(),
                saccos.getDatePaid(),
                saccos.getWithholdingTax(),
                saccos.getCommission(),
                saccos.getProgrammerCost(),
                saccos.getSystemInstallFee(),
                saccos.getTrainingFee(),
                saccos.getAndroidFee(),
                saccos.getAndroidCost(),
                saccos.getSystemReinstallFee(),
                saccos.getBulkSmsFee(),
                saccos.getHostingFee(),
                saccos.getEssFee(),
                saccos.getCloudBackupFee(),
                saccos.getCloudBackupCost(),
                financialYear,
                paymentStatus,
                saccos.getOperationalStatus(),
                saccos.getInvoiceStatus());
    }

    public BigDecimal amountRemaining() {
        return annualFee.subtract(amount);
    }

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
