package com.foresight.fcms.finance.expense.dto;

public class ExpenseCategoryQuery extends ExpenseQuery {
    public ExpenseCategoryQuery() {
        setYear(
                String.valueOf(
                        com.foresight.fcms.shared.time.ApplicationCalendar.today().getYear()));
        setSort("category");
        setDirection("asc");
    }
}
