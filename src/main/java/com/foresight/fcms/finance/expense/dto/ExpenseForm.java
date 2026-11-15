package com.foresight.fcms.finance.expense.dto;

public class ExpenseForm {
    @jakarta.validation.constraints.NotNull
    private java.time.LocalDate date = java.time.LocalDate.now();

    public java.time.LocalDate getDate() {
        return date;
    }

    public void setDate(java.time.LocalDate v) {
        date = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String category = "";

    public String getCategory() {
        return category;
    }

    public void setCategory(String v) {
        category = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String payee = "";

    public String getPayee() {
        return payee;
    }

    public void setPayee(String v) {
        payee = v;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal amount = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal v) {
        amount = v;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    @jakarta.validation.constraints.NotNull
    private java.util.UUID requestKey = java.util.UUID.randomUUID();

    public java.util.UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(java.util.UUID v) {
        requestKey = v;
    }
}
