package com.foresight.fcms.client.saccos.model;

import com.foresight.fcms.client.saccos.dto.SaccosForm;
import com.foresight.fcms.reference.ClientStatus;
import com.foresight.fcms.reference.FinancialYear;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "saccos")
public class Saccos {
    @jakarta.persistence.Version private long version;

    public long getVersion() {
        return version;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saccos_name", nullable = false)
    private String saccosName;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(nullable = false)
    private String region;

    @Column(name = "contract_file")
    private String contractFile;

    @Column(name = "annual_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "date_paid")
    private LocalDate datePaid;

    @Column(name = "withholding_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal withholdingTax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal commission = BigDecimal.ZERO;

    @Column(name = "programmer_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal programmerCost = BigDecimal.ZERO;

    @Column(name = "system_install_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal systemInstallFee = BigDecimal.ZERO;

    @Column(name = "training_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal trainingFee = BigDecimal.ZERO;

    @Column(name = "android_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal androidFee = BigDecimal.ZERO;

    @Column(name = "android_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal androidCost = BigDecimal.ZERO;

    @Column(name = "system_reinstall_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal systemReinstallFee = BigDecimal.ZERO;

    @Column(name = "bulk_sms_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal bulkSmsFee = BigDecimal.ZERO;

    @Column(name = "hosting_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal hostingFee = BigDecimal.ZERO;

    @Column(name = "ess_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal essFee = BigDecimal.ZERO;

    @Column(name = "cloud_backup_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal cloudBackupFee = BigDecimal.ZERO;

    @Column(name = "cloud_backup_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal cloudBackupCost = BigDecimal.ZERO;

    @Column(name = "operational_status", nullable = false, length = 20)
    private String operationalStatus = "ACTIVE";

    @Column(name = "invoice_status", nullable = false, length = 20)
    private String invoiceStatus = "NO";

    @Column(nullable = false)
    private boolean visible = true;

    @ManyToOne
    @JoinColumn(name = "client_status_id")
    private ClientStatus status;

    @ManyToOne
    @JoinColumn(name = "financial_year_id")
    private FinancialYear financialYear;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getSaccosName() {
        return saccosName;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public String getRegion() {
        return region;
    }

    public String getContractFile() {
        return contractFile;
    }

    public boolean hasContractFile() {
        return contractFile != null && !contractFile.isBlank();
    }

    public BigDecimal getAnnualFee() {
        return annualFee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountRemaining() {
        return annualFee.subtract(amount);
    }

    public LocalDate getDatePaid() {
        return datePaid;
    }

    public BigDecimal getWithholdingTax() {
        return withholdingTax;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getProgrammerCost() {
        return programmerCost;
    }

    public BigDecimal getSystemInstallFee() {
        return systemInstallFee;
    }

    public BigDecimal getTrainingFee() {
        return trainingFee;
    }

    public BigDecimal getAndroidFee() {
        return androidFee;
    }

    public BigDecimal getAndroidCost() {
        return androidCost;
    }

    public BigDecimal getSystemReinstallFee() {
        return systemReinstallFee;
    }

    public BigDecimal getBulkSmsFee() {
        return bulkSmsFee;
    }

    public BigDecimal getHostingFee() {
        return hostingFee;
    }

    public BigDecimal getEssFee() {
        return essFee;
    }

    public BigDecimal getCloudBackupFee() {
        return cloudBackupFee;
    }

    public BigDecimal getCloudBackupCost() {
        return cloudBackupCost;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public String getInvoiceStatus() {
        return invoiceStatus;
    }

    public boolean isVisible() {
        return visible;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public FinancialYear getFinancialYear() {
        return financialYear;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void apply(
            SaccosForm form,
            ClientStatus status,
            FinancialYear financialYear,
            String contractFile) {
        this.saccosName = form.getSaccosName();
        this.entryDate = form.getEntryDate();
        this.expireDate = form.getExpireDate();
        this.region = form.getRegion();
        if (contractFile != null) {
            this.contractFile = contractFile;
        }
        this.annualFee = zeroIfNull(form.getAnnualFee());
        this.amount = zeroIfNull(form.getAmount());
        this.datePaid = form.getDatePaid();
        this.withholdingTax = zeroIfNull(form.getWithholdingTax());
        this.commission = zeroIfNull(form.getCommission());
        this.programmerCost = zeroIfNull(form.getProgrammerCost());
        this.systemInstallFee = zeroIfNull(form.getSystemInstallFee());
        this.trainingFee = zeroIfNull(form.getTrainingFee());
        this.androidFee = zeroIfNull(form.getAndroidFee());
        this.androidCost = zeroIfNull(form.getAndroidCost());
        this.systemReinstallFee = zeroIfNull(form.getSystemReinstallFee());
        this.bulkSmsFee = zeroIfNull(form.getBulkSmsFee());
        this.hostingFee = zeroIfNull(form.getHostingFee());
        this.essFee = zeroIfNull(form.getEssFee());
        this.cloudBackupFee = zeroIfNull(form.getCloudBackupFee());
        this.cloudBackupCost = zeroIfNull(form.getCloudBackupCost());
        this.operationalStatus = form.getOperationalStatus();
        this.invoiceStatus = form.getInvoiceStatus();
        this.status = status;
        this.financialYear = financialYear;
        this.visible = true;
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public void hide() {
        visible = false;
        updatedAt = Instant.now();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
