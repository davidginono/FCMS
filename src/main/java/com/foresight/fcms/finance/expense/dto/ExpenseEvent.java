package com.foresight.fcms.finance.expense.dto;

public record ExpenseEvent(
        long id,
        String action,
        String fromStatus,
        String toStatus,
        String actor,
        String reason,
        String paymentReference,
        java.math.BigDecimal amount,
        java.time.OffsetDateTime occurredAt) {}
