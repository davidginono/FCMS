package com.foresight.fcms.accounting.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class AccountingViews {
    private AccountingViews() {}

    public record Account(
            long id,
            String code,
            String name,
            String description,
            String classification,
            String normalBalance,
            int depth,
            Long parentId,
            String parentName,
            boolean cashAccount,
            boolean active,
            long version) {}

    public record AccountGroup(
            String classification,
            long total,
            com.foresight.fcms.shared.dto.RegisterPage<Account> accounts) {}

    public record GroupedAccounts(List<AccountGroup> groups, long total) {}

    public record Book(
            LocalDate openingDate,
            String policyReference,
            String openingMode,
            String state,
            long makerId,
            Long checkerId,
            long version,
            boolean ready) {}

    public record Period(
            long id,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String state,
            long version) {}

    public record Line(
            long accountId,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            String description) {}

    public record Journal(
            long id,
            String kind,
            String reference,
            String description,
            String evidenceReference,
            LocalDate date,
            long periodId,
            String cashFlow,
            String state,
            long makerId,
            String maker,
            Long checkerId,
            String checker,
            long version,
            Long reversesId,
            Long reversedBy,
            String reviewComment,
            BigDecimal total,
            boolean sourceLinked,
            List<Line> lines) {}
}
