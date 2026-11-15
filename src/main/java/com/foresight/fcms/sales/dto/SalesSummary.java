package com.foresight.fcms.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesSummary(
        long id,
        String clientType,
        String clientName,
        String documentNumber,
        LocalDate documentDate,
        LocalDate validUntil,
        String currency,
        BigDecimal amount) {}
