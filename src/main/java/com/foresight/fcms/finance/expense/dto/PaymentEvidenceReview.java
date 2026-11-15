package com.foresight.fcms.finance.expense.dto;

import jakarta.validation.constraints.*;

public record PaymentEvidenceReview(
        @PositiveOrZero long version,
        @NotNull @Pattern(regexp = "approve|reject|cancel") String action,
        @NotBlank @Size(max = 1000) String comment) {}
