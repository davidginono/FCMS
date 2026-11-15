package com.foresight.fcms.accounting.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public class ReportingForm {
    @NotNull private UUID requestKey;

    @Min(0)
    private long accountVersion;

    @Positive private Long baseId;

    @NotNull
    @Pattern(
            regexp =
                    "|CURRENT_ASSETS|NON_CURRENT_ASSETS|CURRENT_LIABILITIES|NON_CURRENT_LIABILITIES|UNCLASSIFIED")
    private String section = "";

    @Min(0)
    @Max(999)
    private Integer noteNumber;

    @NotNull
    @Size(max = 2000)
    private String noteText = "";

    @NotBlank
    @Size(max = 500)
    private String reason = "";

    public UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(UUID v) {
        requestKey = v;
    }

    public long getAccountVersion() {
        return accountVersion;
    }

    public void setAccountVersion(long v) {
        accountVersion = v;
    }

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long v) {
        baseId = v;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String v) {
        section = v;
    }

    public Integer getNoteNumber() {
        return noteNumber;
    }

    public void setNoteNumber(Integer v) {
        noteNumber = v;
    }

    public String getNoteText() {
        return noteText;
    }

    public void setNoteText(String v) {
        noteText = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }
}
