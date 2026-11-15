package com.foresight.fcms.finance.expense.dto;

public class ExpenseCategoryQuery extends ExpenseQuery {
    public ExpenseCategoryQuery() {
        setYear(String.valueOf(java.time.LocalDate.now().getYear()));
        setSort("category");
        setDirection("asc");
    }
}
