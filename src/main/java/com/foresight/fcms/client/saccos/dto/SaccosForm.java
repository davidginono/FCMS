package com.foresight.fcms.client.saccos.dto;

import com.foresight.fcms.client.saccos.model.Saccos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SaccosForm {
    private long version;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    @NotBlank private String saccosName;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate entryDate = com.foresight.fcms.shared.time.ApplicationCalendar.today();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expireDate;

    @NotBlank private String region;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal annualFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal amount = BigDecimal.ZERO;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate datePaid;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal withholdingTax = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal commission = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal programmerCost = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal systemInstallFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal trainingFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal androidFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal androidCost = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal systemReinstallFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal bulkSmsFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal hostingFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal essFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal cloudBackupFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal cloudBackupCost = BigDecimal.ZERO;

    @NotNull private Long paymentStatusId;

    @NotNull private Long financialYearId;

    private String operationalStatus = "ACTIVE";

    private String invoiceStatus = "NO";

    public static SaccosForm from(Saccos saccos) {
        SaccosForm form = new SaccosForm();
        form.setVersion(saccos.getVersion());
        form.setSaccosName(saccos.getSaccosName());
        form.setEntryDate(saccos.getEntryDate());
        form.setExpireDate(saccos.getExpireDate());
        form.setRegion(saccos.getRegion());
        form.setAnnualFee(saccos.getAnnualFee());
        form.setAmount(saccos.getAmount());
        form.setDatePaid(saccos.getDatePaid());
        form.setWithholdingTax(saccos.getWithholdingTax());
        form.setCommission(saccos.getCommission());
        form.setProgrammerCost(saccos.getProgrammerCost());
        form.setSystemInstallFee(saccos.getSystemInstallFee());
        form.setTrainingFee(saccos.getTrainingFee());
        form.setAndroidFee(saccos.getAndroidFee());
        form.setAndroidCost(saccos.getAndroidCost());
        form.setSystemReinstallFee(saccos.getSystemReinstallFee());
        form.setBulkSmsFee(saccos.getBulkSmsFee());
        form.setHostingFee(saccos.getHostingFee());
        form.setEssFee(saccos.getEssFee());
        form.setCloudBackupFee(saccos.getCloudBackupFee());
        form.setCloudBackupCost(saccos.getCloudBackupCost());
        form.setPaymentStatusId(saccos.getStatus() == null ? null : saccos.getStatus().getId());
        form.setFinancialYearId(
                saccos.getFinancialYear() == null ? null : saccos.getFinancialYear().getId());
        form.setOperationalStatus(saccos.getOperationalStatus());
        form.setInvoiceStatus(saccos.getInvoiceStatus());
        return form;
    }

    public String getSaccosName() {
        return saccosName;
    }

    public void setSaccosName(String saccosName) {
        this.saccosName = saccosName;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public BigDecimal getAnnualFee() {
        return annualFee;
    }

    public void setAnnualFee(BigDecimal annualFee) {
        this.annualFee = annualFee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDatePaid() {
        return datePaid;
    }

    public void setDatePaid(LocalDate datePaid) {
        this.datePaid = datePaid;
    }

    public BigDecimal getWithholdingTax() {
        return withholdingTax;
    }

    public void setWithholdingTax(BigDecimal withholdingTax) {
        this.withholdingTax = withholdingTax;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public BigDecimal getProgrammerCost() {
        return programmerCost;
    }

    public void setProgrammerCost(BigDecimal programmerCost) {
        this.programmerCost = programmerCost;
    }

    public BigDecimal getSystemInstallFee() {
        return systemInstallFee;
    }

    public void setSystemInstallFee(BigDecimal systemInstallFee) {
        this.systemInstallFee = systemInstallFee;
    }

    public BigDecimal getTrainingFee() {
        return trainingFee;
    }

    public void setTrainingFee(BigDecimal trainingFee) {
        this.trainingFee = trainingFee;
    }

    public BigDecimal getAndroidFee() {
        return androidFee;
    }

    public void setAndroidFee(BigDecimal androidFee) {
        this.androidFee = androidFee;
    }

    public BigDecimal getAndroidCost() {
        return androidCost;
    }

    public void setAndroidCost(BigDecimal androidCost) {
        this.androidCost = androidCost;
    }

    public BigDecimal getSystemReinstallFee() {
        return systemReinstallFee;
    }

    public void setSystemReinstallFee(BigDecimal systemReinstallFee) {
        this.systemReinstallFee = systemReinstallFee;
    }

    public BigDecimal getBulkSmsFee() {
        return bulkSmsFee;
    }

    public void setBulkSmsFee(BigDecimal bulkSmsFee) {
        this.bulkSmsFee = bulkSmsFee;
    }

    public BigDecimal getHostingFee() {
        return hostingFee;
    }

    public void setHostingFee(BigDecimal hostingFee) {
        this.hostingFee = hostingFee;
    }

    public BigDecimal getEssFee() {
        return essFee;
    }

    public void setEssFee(BigDecimal essFee) {
        this.essFee = essFee;
    }

    public BigDecimal getCloudBackupFee() {
        return cloudBackupFee;
    }

    public void setCloudBackupFee(BigDecimal cloudBackupFee) {
        this.cloudBackupFee = cloudBackupFee;
    }

    public BigDecimal getCloudBackupCost() {
        return cloudBackupCost;
    }

    public void setCloudBackupCost(BigDecimal cloudBackupCost) {
        this.cloudBackupCost = cloudBackupCost;
    }

    public Long getPaymentStatusId() {
        return paymentStatusId;
    }

    public void setPaymentStatusId(Long paymentStatusId) {
        this.paymentStatusId = paymentStatusId;
    }

    public Long getFinancialYearId() {
        return financialYearId;
    }

    public void setFinancialYearId(Long financialYearId) {
        this.financialYearId = financialYearId;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getInvoiceStatus() {
        return invoiceStatus;
    }

    public void setInvoiceStatus(String invoiceStatus) {
        this.invoiceStatus = invoiceStatus;
    }
}
