package com.foresight.fcms.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseSummaryDto(
    Integer id,
    LocalDate date,
    String category,
    String description,
    String payee,
    BigDecimal amount,
    String financialYear,
    String status
) {
    static ExpenseSummaryDto from(Expense expense) {
        return new ExpenseSummaryDto(
            expense.getId(),
            expense.getDateCreated(),
            expense.getCategory(),
            expense.getDescription(),
            expense.getPayee(),
            expense.getAmount(),
            expense.getFinancialYear(),
            expense.getStatus().getName()
        );
    }
}
