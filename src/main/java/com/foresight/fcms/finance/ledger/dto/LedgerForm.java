package com.foresight.fcms.finance.ledger.dto;

public class LedgerForm {
    @jakarta.validation.constraints.NotNull
    private java.time.LocalDate date = java.time.LocalDate.now();

    public java.time.LocalDate getDate() {
        return date;
    }

    public void setDate(java.time.LocalDate v) {
        date = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 1000)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String category = "";

    public String getCategory() {
        return category;
    }

    public void setCategory(String v) {
        category = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String reference = "";

    public String getReference() {
        return reference;
    }

    public void setReference(String v) {
        reference = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 30)
    private String paymentMode = "CASH";

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String v) {
        paymentMode = v;
    }

    @jakarta.validation.constraints.Pattern(regexp = "IN|OUT")
    private String direction = "IN";

    public String getDirection() {
        return direction;
    }

    public void setDirection(String v) {
        direction = v;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal amount = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal v) {
        amount = v;
    }

    @jakarta.validation.constraints.Pattern(regexp = "IN|OUT")
    private String unitDirection = "IN";

    public String getUnitDirection() {
        return unitDirection;
    }

    public void setUnitDirection(String v) {
        unitDirection = v;
    }

    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(1000000000)
    private long units = 0;

    public long getUnits() {
        return units;
    }

    public void setUnits(long v) {
        units = v;
    }

    private Long clientId;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long v) {
        clientId = v;
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
