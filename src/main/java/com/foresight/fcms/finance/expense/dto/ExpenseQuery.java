package com.foresight.fcms.finance.expense.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

/** Exact legacy category/year filters, independent of the actor's authorized scope. */
public class ExpenseQuery extends RegisterQuery {
    private String year = "";
    private String category = "";

    public String getYear() {
        return year;
    }

    public void setYear(String value) {
        year = filter(value);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String value) {
        category = filter(value);
    }

    private String filter(String value) {
        if (value == null) return "";
        if (value.length() > 255 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("error.invalid");
        return value;
    }
}
