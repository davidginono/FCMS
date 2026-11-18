package com.foresight.fcms.finance.ledger.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

import java.util.Set;

public class LedgerQuery extends RegisterQuery {
    private String year = "";
    private String category = "";

    public String getYear() {
        return year;
    }

    public void setYear(String value) {
        year = value == null ? "" : value.trim();
        if (!year.isEmpty() && (!year.matches("20[0-9]{2}|2100")))
            throw new IllegalArgumentException("error.invalid");
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String value) {
        category = value == null ? "" : value;
        if (!Set.of("", "STOCK_PURCHASE", "CLIENT_SALE").contains(category))
            throw new IllegalArgumentException("error.invalid");
    }
}
