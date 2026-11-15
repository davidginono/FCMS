package com.foresight.fcms.accounting.dto;

public class JournalLineForm {
    @jakarta.validation.constraints.NotNull private Long accountId;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long value) {
        this.accountId = value;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal debit = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(java.math.BigDecimal value) {
        this.debit = value;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal credit = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(java.math.BigDecimal value) {
        this.credit = value;
    }

    @jakarta.validation.constraints.Size(max = 500)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
    }
}
