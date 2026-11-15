package com.foresight.fcms.finance.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One authorized snapshot of the selected movements and current account counters. */
public record LedgerReport(
        List<LedgerView> rows,
        BigDecimal amountIn,
        BigDecimal amountOut,
        long unitsIn,
        long unitsOut,
        BigDecimal currentBalance,
        long currentUnits,
        Instant generatedAt) {
    public LedgerReport {
        rows = List.copyOf(rows);
    }

    public BigDecimal movement() {
        return amountIn.subtract(amountOut);
    }
}
