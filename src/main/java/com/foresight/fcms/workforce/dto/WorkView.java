package com.foresight.fcms.workforce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkView(
        long id,
        String kind,
        long ownerId,
        String owner,
        Long clientId,
        String client,
        String subject,
        String description,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String workType,
        String outcome,
        String explanation,
        BigDecimal allowance,
        BigDecimal amount,
        String status,
        String reviewComment,
        long version,
        long createdBy,
        String creator,
        Long preparedBy,
        String preparer,
        String activityStaffName) {
    /** Export presentation never changes the canonical owner/creator identity. */
    public String activityStaff() {
        return activityStaffName == null ? owner : activityStaffName;
    }

    public WorkView forActivityStaff(String name) {
        return new WorkView(
                id,
                kind,
                ownerId,
                owner,
                clientId,
                client,
                subject,
                description,
                startAt,
                endAt,
                workType,
                outcome,
                explanation,
                allowance,
                amount,
                status,
                reviewComment,
                version,
                createdBy,
                creator,
                preparedBy,
                preparer,
                name);
    }
}
