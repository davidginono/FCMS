package com.foresight.fcms.finance.dto;

public record PostingSource(
        String kind,
        long id,
        String event,
        java.time.LocalDate date,
        String reference,
        String description,
        java.math.BigDecimal signedAmount,
        long actorId,
        String state,
        String reversesKind,
        Long reversesId) {}
