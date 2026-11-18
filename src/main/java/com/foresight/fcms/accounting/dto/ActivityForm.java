package com.foresight.fcms.accounting.dto;

public class ActivityForm {
    private String code = "";

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        code = value;
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

    @jakarta.validation.constraints.NotNull
    private java.util.UUID requestKey = java.util.UUID.randomUUID();

    public java.util.UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(java.util.UUID value) {
        requestKey = value;
    }
}
