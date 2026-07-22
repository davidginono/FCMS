package com.foresight.fcms.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

    private final ExpenseRepository expenses;

    public FinanceService(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public Page<ExpenseSummaryDto> findExpenses(Pageable pageable) {
        return expenses.findAll(pageable).map(ExpenseSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public long countExpenses() {
        return expenses.count();
    }
}
