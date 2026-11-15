package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.EquityReporting.*;
import com.foresight.fcms.accounting.service.StatementService.*;

import java.math.BigDecimal;
import java.util.*;

/** Adapted from Microfinance EquityStatementCalculator; signed, posted FCMS amounts only. */
public final class EquityMatrixCalculator {
    private EquityMatrixCalculator() {}

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public static Matrix calculate(
            List<ComparedAccount> accounts,
            List<Movement> movements,
            Setup setup,
            boolean prior,
            List<Long> revisions) {
        var columns = new LinkedHashSet<String>();
        var components = new HashMap<Long, String>();
        String retained = "retained";
        for (int i = 0; i < setup.components().size(); i++) {
            var c = setup.components().get(i);
            String key = "component:" + i;
            columns.add(key);
            for (long id : c.accounts()) components.put(id, key);
            if (c.retained()) retained = key;
        }
        var checks = new ArrayList<String>();
        for (var a : accounts)
            if (a.current().classification().equals("EQUITY")
                    && !components.containsKey(a.current().id())) {
                components.put(a.current().id(), "unclassified");
                columns.add("unclassified");
                if (active(a.current()) || a.previous() != null && active(a.previous()))
                    checks.add("components");
            }
        columns.add(retained);
        if (columns.size() > 10) throw new IllegalArgumentException("gl.equity.columnLimit");
        var current =
                period(
                        accounts,
                        movements,
                        components,
                        List.copyOf(columns),
                        retained,
                        false,
                        checks);
        var previous =
                prior
                        ? period(
                                accounts,
                                movements,
                                components,
                                List.copyOf(columns),
                                retained,
                                true,
                                checks)
                        : null;
        return new Matrix(
                setup,
                List.copyOf(columns),
                current,
                previous,
                checks.stream().distinct().toList(),
                revisions);
    }

    private static boolean active(TrialRow r) {
        return r.opening().signum() != 0
                || r.debit().signum() != 0
                || r.credit().signum() != 0
                || r.closingDebit().subtract(r.closingCredit()).signum() != 0;
    }

    private static List<MatrixRow> period(
            List<ComparedAccount> accounts,
            List<Movement> movements,
            Map<Long, String> components,
            List<String> columns,
            String retained,
            boolean previous,
            List<String> checks) {
        var opening = zero(columns);
        var closing = zero(columns);
        var profit = zero(columns);
        var byId = new HashMap<Long, TrialRow>();
        for (var a : accounts) {
            var b = previous ? a.previous() : a.current();
            if (b == null) continue;
            byId.put(b.id(), b);
            if (b.classification().equals("EQUITY")) {
                String c = components.get(b.id());
                opening.merge(c, b.opening().negate(), BigDecimal::add);
                closing.merge(c, b.closingCredit().subtract(b.closingDebit()), BigDecimal::add);
            } else if (Set.of("INCOME", "EXPENSE").contains(b.classification())) {
                opening.merge(retained, b.opening().negate(), BigDecimal::add);
                closing.merge(
                        retained, b.closingCredit().subtract(b.closingDebit()), BigDecimal::add);
                profit.merge(retained, b.credit().subtract(b.debit()), BigDecimal::add);
            }
        }
        var changes = new EnumMap<Category, Map<String, BigDecimal>>(Category.class);
        for (var c : Category.values()) changes.put(c, zero(columns));
        for (var m : movements)
            if (m.previous() == previous) {
                var a = byId.get(m.accountId());
                if (a == null) throw new IllegalArgumentException("error.invalid");
                if (a.classification().equals("EQUITY"))
                    changes.get(m.category())
                            .merge(components.get(a.id()), m.signed().negate(), BigDecimal::add);
                else if (m.category() == Category.EARNINGS_TRANSFER
                        && Set.of("INCOME", "EXPENSE").contains(a.classification())) {
                    changes.get(Category.EARNINGS_TRANSFER)
                            .merge(retained, m.signed().negate(), BigDecimal::add);
                    profit.merge(retained, m.signed(), BigDecimal::add);
                }
            }
        var rows = new ArrayList<MatrixRow>();
        rows.add(row("opening", opening));
        rows.add(row("profit", profit));
        for (var c : Category.values()) {
            var values = changes.get(c);
            if (values.values().stream().anyMatch(v -> v.signum() != 0)
                    || c == Category.UNCLASSIFIED
                            && movements.stream()
                                    .anyMatch(
                                            m ->
                                                    m.previous() == previous
                                                            && m.category() == c
                                                            && (m.debit().signum() != 0
                                                                    || m.credit().signum() != 0)))
                rows.add(row(c.name(), values));
        }
        rows.add(row("closing", closing));
        for (String col : columns) {
            BigDecimal difference = opening.get(col).add(profit.get(col));
            for (var c : Category.values()) difference = difference.add(changes.get(c).get(col));
            difference = difference.subtract(closing.get(col));
            if (difference.signum() != 0)
                checks.add(
                        (previous ? "previous:" : "current:")
                                + col
                                + ":"
                                + difference.toPlainString());
        }
        if (movements.stream()
                .anyMatch(
                        m ->
                                m.previous() == previous
                                        && m.category() == Category.UNCLASSIFIED
                                        && (m.debit().signum() != 0 || m.credit().signum() != 0)))
            checks.add(previous ? "previousMovements" : "movements");
        return List.copyOf(rows);
    }

    private static Map<String, BigDecimal> zero(List<String> cols) {
        var result = new LinkedHashMap<String, BigDecimal>();
        cols.forEach(c -> result.put(c, ZERO));
        return result;
    }

    private static MatrixRow row(String key, Map<String, BigDecimal> values) {
        return new MatrixRow(key, values, values.values().stream().reduce(ZERO, BigDecimal::add));
    }
}
