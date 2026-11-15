package com.foresight.fcms.accounting.dto;

import jakarta.validation.constraints.*;

public class ReportingDecisionForm {
    @Min(0)
    private long version;

    @NotBlank
    @Size(max = 500)
    private String reason = "";

    @NotNull
    @Pattern(regexp = "approve|reject|cancel")
    private String action;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String v) {
        action = v;
    }
}
