package com.foresight.fcms.workforce.dto;

public class WorkForm {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(max = 20)
    private java.util.List<Long> participants = new java.util.ArrayList<>();

    public java.util.List<Long> getParticipants() {
        return participants;
    }

    public void setParticipants(java.util.List<Long> p) {
        participants = p;
    }

    @jakarta.validation.constraints.Positive private Long ownerId;

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long v) {
        ownerId = v;
    }

    private Long clientId;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long v) {
        clientId = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 180)
    private String subject = "";

    public String getSubject() {
        return subject;
    }

    public void setSubject(String v) {
        subject = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 4000)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    @jakarta.validation.constraints.NotNull
    private java.time.LocalDateTime startAt =
            com.foresight.fcms.shared.time.ApplicationCalendar.now().withSecond(0).withNano(0);

    public java.time.LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(java.time.LocalDateTime v) {
        startAt = v;
    }

    @jakarta.validation.constraints.NotNull
    private java.time.LocalDateTime endAt =
            com.foresight.fcms.shared.time.ApplicationCalendar.now().withSecond(0).withNano(0);

    public java.time.LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(java.time.LocalDateTime v) {
        endAt = v;
    }

    @jakarta.validation.constraints.Size(max = 100)
    private String workType = "";

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String v) {
        workType = v;
    }

    @jakarta.validation.constraints.Size(max = 30)
    private String outcome = "";

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String v) {
        outcome = v;
    }

    @jakarta.validation.constraints.Size(max = 4000)
    private String explanation = "";

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String v) {
        explanation = v;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal allowance = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getAllowance() {
        return allowance;
    }

    public void setAllowance(java.math.BigDecimal v) {
        allowance = v;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private java.math.BigDecimal amount = java.math.BigDecimal.ZERO;

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal v) {
        amount = v;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    @jakarta.validation.constraints.NotNull
    private java.util.UUID requestKey = java.util.UUID.randomUUID();

    public java.util.UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(java.util.UUID v) {
        requestKey = v;
    }
}
