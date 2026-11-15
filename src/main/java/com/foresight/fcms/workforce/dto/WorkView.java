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
        long version) {}
