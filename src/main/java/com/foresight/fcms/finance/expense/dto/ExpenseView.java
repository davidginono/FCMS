package com.foresight.fcms.finance.expense.dto;

public record ExpenseView(
        long id,
        java.time.LocalDate date,
        long ownerId,
        String owner,
        String category,
        String description,
        String payee,
        java.math.BigDecimal amount,
        String year,
        String status,
        String comment,
        String paymentReference,
        long version,
        Long createdBy,
        String creator,
        Long preparedBy,
        String preparer) {}
