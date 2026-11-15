package com.foresight.fcms.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    public record Revision(
            long id,
            String kind,
            long targetId,
            Long baseId,
            String payload,
            String reason,
            String state,
            long makerId,
            String maker,
            Long checkerId,
            String checker,
            Instant createdAt,
            String reviewReason,
            long version) {}

    public record Choice(long id, String code, String name) {}

    public record Journal(
            long id,
            java.time.LocalDate date,
            String reference,
            String description,
            String category,
            Long revisionId) {}

    public static class Form {
        @NotNull private UUID requestKey = UUID.randomUUID();
        @Positive private Long baseId;

        @NotBlank
        @Size(max = 500)
        private String reason = "";

        @NotNull
        @Size(max = 9)
        private List<@Valid ComponentForm> components = new ArrayList<>();

        public UUID getRequestKey() {
            return requestKey;
        }

        public void setRequestKey(UUID v) {
            requestKey = v;
        }

        public Long getBaseId() {
            return baseId;
        }

        public void setBaseId(Long v) {
            baseId = v;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String v) {
            reason = v;
        }

        public List<ComponentForm> getComponents() {
            return components;
        }

        public void setComponents(List<ComponentForm> v) {
            components = v;
        }
    }

    public static class ComponentForm {
        @NotBlank
        @Size(max = 100)
        private String name = "";

        @NotNull
        @Size(max = 100)
        private String nameSw = "";

        @NotBlank
        @Size(max = 20000)
        private String accounts = "";

        private boolean retained;

        public String getName() {
            return name;
        }

        public void setName(String v) {
            name = v;
        }

        public String getNameSw() {
            return nameSw;
        }

        public void setNameSw(String v) {
            nameSw = v;
        }

        public String getAccounts() {
            return accounts;
        }

        public void setAccounts(String v) {
            accounts = v;
        }

        public boolean isRetained() {
            return retained;
        }

        public void setRetained(boolean v) {
            retained = v;
        }
    }

    public static class MovementForm {
        @NotNull private UUID requestKey = UUID.randomUUID();
        @Positive private Long baseId;
        @NotNull private Category category = Category.UNCLASSIFIED;

        @NotBlank
        @Size(max = 500)
        private String reason = "";

        public UUID getRequestKey() {
            return requestKey;
        }

        public void setRequestKey(UUID v) {
            requestKey = v;
        }

        public Long getBaseId() {
            return baseId;
        }

        public void setBaseId(Long v) {
            baseId = v;
        }

        public Category getCategory() {
            return category;
        }

        public void setCategory(Category v) {
            category = v;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String v) {
            reason = v;
        }
    }
}
