package com.foresight.fcms.accounting.dto;

import com.foresight.fcms.shared.dto.RegisterPage;

import java.time.Instant;
import java.util.Set;

public final class AccountReporting {
    private AccountReporting() {}

    public record Settings(String section, Integer noteNumber, String noteText) {}

    public record Version(
            long id,
            long accountId,
            long accountVersion,
            Long baseId,
            Settings proposed,
            Settings before,
            String reason,
            String state,
            long makerId,
            String makerName,
            Long checkerId,
            String checkerName,
            Instant createdAt,
            Instant reviewedAt,
            String reviewReason,
            long version,
            Long replacedBy,
            Instant retiredAt) {}

    public record Workspace(
            AccountingViews.Account account,
            Version effective,
            Version pending,
            RegisterPage<Version> history,
            boolean canPropose,
            boolean sectionEditable,
            boolean pendingExists) {}

    public record Decision(AccountingViews.Account account, Version revision, Set<String> actions) {
        public Decision {
            actions = Set.copyOf(actions);
        }
    }

    public record Setup(
            AccountingViews.Account account, ReportingForm form, boolean sectionEditable) {}

    public record Mapping(
            Long groupId,
            String groupCode,
            String groupName,
            String section,
            int noteNumber,
            String noteText,
            Long sectionVersionId,
            Long noteVersionId,
            java.util.List<Long> revisionIds) {
        public Mapping {
            revisionIds = java.util.List.copyOf(revisionIds);
        }
    }
}
