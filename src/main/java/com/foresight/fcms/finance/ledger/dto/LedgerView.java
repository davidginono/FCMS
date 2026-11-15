package com.foresight.fcms.finance.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerView(
        long id,
        String kind,
        LocalDate date,
        String description,
        String category,
        String reference,
        String paymentMode,
        BigDecimal amount,
        long units,
        BigDecimal balance,
        long unitBalance,
        long actorId,
        String actor,
        Long reversalOf,
        Long reversedBy,
        String client) {}
