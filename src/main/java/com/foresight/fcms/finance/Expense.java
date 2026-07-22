package com.foresight.fcms.finance;

import com.foresight.fcms.auth.UserAccount;
import com.foresight.fcms.reference.ExpenseStatus;
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
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "date_created")
    private LocalDate dateCreated;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    private String payee;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @ManyToOne(optional = false)
    @JoinColumn(name = "status_id")
    private ExpenseStatus status;

    public Integer getId() {
        return id;
    }

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getPayee() {
        return payee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public ExpenseStatus getStatus() {
        return status;
    }
}
