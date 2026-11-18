package com.foresight.fcms.accounting.dto;

public class TransactionConfigForm extends PostingMappingForm {
    public TransactionConfigForm() { setEvent("MANUAL_JOURNAL"); }
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Long activityId;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long value) {
        activityId = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 160)
    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    @jakarta.validation.constraints.Size(max = 500)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    @jakarta.validation.constraints.PositiveOrZero private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }
}
