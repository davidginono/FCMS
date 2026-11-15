package com.foresight.fcms.sales.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class SalesItem {
    @NotBlank
    @Size(max = 4000)
    private String description = "";

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    public SalesItem() {}

    public SalesItem(String description, BigDecimal amount) {
        this.description = description;
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal value) {
        amount = value;
    }
}
