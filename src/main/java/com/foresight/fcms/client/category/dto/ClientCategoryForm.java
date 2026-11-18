package com.foresight.fcms.client.category.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ClientCategoryForm {
    private long version;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    @NotBlank
    @Size(max = 255)
    private String clientName;

    @NotNull
    private LocalDate entryDate = com.foresight.fcms.shared.time.ApplicationCalendar.today();

    private LocalDate expireDate;

    @NotBlank
    @Size(max = 255)
    private String region;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal annualFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal amount = BigDecimal.ZERO;

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
    private BigDecimal systemReinstallFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal bulkSmsFee = BigDecimal.ZERO;

    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    @DecimalMin("0.00")
    private BigDecimal hostingFee = BigDecimal.ZERO;

    @NotNull private Long paymentStatusId;

    private Long financialYearId;

    @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE")
    @NotBlank
    private String operationalStatus = "ACTIVE";

    public String getOperationalStatus() { return operationalStatus; }

    public void setOperationalStatus(String value) { operationalStatus = value; }

    public static ClientCategoryForm from(ClientCategoryRowDto row) {
        ClientCategoryForm form = new ClientCategoryForm();
        form.setClientName(row.clientName());
        form.setEntryDate(row.entryDate());
        form.setExpireDate(row.expireDate());
        form.setRegion(row.region());
        form.setAnnualFee(row.annualFee());
        form.setAmount(row.amount());
        form.setWithholdingTax(row.withholdingTax());
        form.setCommission(row.commission());
        form.setProgrammerCost(row.programmerCost());
        form.setSystemInstallFee(row.systemInstallFee());
        form.setTrainingFee(row.trainingFee());
        form.setAndroidFee(row.androidFee());
        form.setSystemReinstallFee(row.systemReinstallFee());
        form.setBulkSmsFee(row.bulkSmsFee());
        form.setHostingFee(row.hostingFee());
        form.setPaymentStatusId(row.paymentStatusId());
        form.setFinancialYearId(row.financialYearId());
        if (row.operationalStatus() != null) form.setOperationalStatus(row.operationalStatus());
        return form;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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
}
