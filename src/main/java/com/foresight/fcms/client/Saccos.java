package com.foresight.fcms.client;

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
import java.time.LocalDate;

@Entity
@Table(name = "saccos")
public class Saccos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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

    @Column(name = "annual_fee", nullable = false)
    private BigDecimal annualFee;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "system_install_fee", nullable = false)
    private BigDecimal systemInstallFee;

    @Column(name = "training_fee", nullable = false)
    private BigDecimal trainingFee;

    @Column(name = "android_fee", nullable = false)
    private BigDecimal androidFee;

    @ManyToOne
    @JoinColumn(name = "client_status_id")
    private ClientStatus status;

    @ManyToOne
    @JoinColumn(name = "financial_year_id")
    private FinancialYear financialYear;

    public Integer getId() {
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

    public BigDecimal getAnnualFee() {
        return annualFee;
    }

    public BigDecimal getAmount() {
        return amount;
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

    public ClientStatus getStatus() {
        return status;
    }

    public FinancialYear getFinancialYear() {
        return financialYear;
    }
}
