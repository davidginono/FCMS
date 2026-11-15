package com.foresight.fcms.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SalesDocument(
        long id,
        String kind,
        SalesForm data,
        BigDecimal amount,
        long createdBy,
        long updatedBy,
        Instant createdAt,
        Instant updatedAt) {}
