package com.foresight.fcms.accounting.dto;

import java.math.BigDecimal;
import java.util.*;

public final class EquityReporting {
    private EquityReporting() {}

    public enum Category {
        CONTRIBUTION,
        DISTRIBUTION,
        TRANSFER,
        EARNINGS_TRANSFER,
        OCI,
        ADJUSTMENT,
        UNCLASSIFIED
    }

    public record Component(String name, String nameSw, List<Long> accounts, boolean retained) {
        public Component {
            accounts = List.copyOf(accounts);
        }
    }

    public record Setup(List<Component> components) {
        public Setup {
            components = List.copyOf(components);
        }

        public static Setup empty() {
            return new Setup(List.of());
        }
    }

    public record Movement(
            long accountId,
            Category category,
            boolean previous,
            BigDecimal debit,
            BigDecimal credit) {
        public BigDecimal signed() {
            return debit.subtract(credit);
        }
    }

    public record MatrixRow(String key, Map<String, BigDecimal> values, BigDecimal total) {
        public MatrixRow {
            values = Map.copyOf(values);
        }
    }

    public record Matrix(
            Setup setup,
            List<String> columns,
            List<MatrixRow> current,
            List<MatrixRow> previous,
            List<String> checks,
            List<Long> revisionIds) {
        public Matrix {
            columns = List.copyOf(columns);
            current = List.copyOf(current);
            previous = previous == null ? null : List.copyOf(previous);
            checks = List.copyOf(checks);
            revisionIds = List.copyOf(revisionIds);
        }
    }

}
