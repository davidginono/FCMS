package com.foresight.fcms.accounting.dto;

public class BookForm {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PastOrPresent
    private java.time.LocalDate openingDate;

    public java.time.LocalDate getOpeningDate() {
        return openingDate;
    }

    public void setOpeningDate(java.time.LocalDate value) {
        this.openingDate = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    private String policyReference = "";

    public String getPolicyReference() {
        return policyReference;
    }

    public void setPolicyReference(String value) {
        this.policyReference = value;
    }

    @jakarta.validation.constraints.Pattern(regexp = "ZERO|JOURNAL")
    private String openingMode = "JOURNAL";

    public String getOpeningMode() {
        return openingMode;
    }

    public void setOpeningMode(String value) {
        this.openingMode = value;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        this.version = value;
    }
}
