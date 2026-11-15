package com.foresight.fcms.accounting.dto;

public class JournalForm {
    @jakarta.validation.constraints.Pattern(regexp = "JOURNAL|RECEIPT|PAYMENT|OPENING")
    private String kind = "JOURNAL";

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        this.kind = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String reference = "";

    public String getReference() {
        return reference;
    }

    public void setReference(String value) {
        this.reference = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 1000)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    private String evidenceReference = "";

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public void setEvidenceReference(String value) {
        this.evidenceReference = value;
    }

    @jakarta.validation.constraints.NotNull
    private java.time.LocalDate date = java.time.LocalDate.now();

    public java.time.LocalDate getDate() {
        return date;
    }

    public void setDate(java.time.LocalDate value) {
        this.date = value;
    }

    @jakarta.validation.constraints.Pattern(regexp = "NONE|OPERATING|INVESTING|FINANCING|INTERNAL")
    private String cashFlow = "NONE";

    public String getCashFlow() {
        return cashFlow;
    }

    public void setCashFlow(String value) {
        this.cashFlow = value;
    }

    @jakarta.validation.constraints.NotNull
    private java.util.UUID requestKey = java.util.UUID.randomUUID();

    public java.util.UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(java.util.UUID value) {
        this.requestKey = value;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        this.version = value;
    }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(min = 2, max = 50)
    private java.util.List<@jakarta.validation.Valid JournalLineForm> lines =
            new java.util.ArrayList<>(
                    java.util.List.of(new JournalLineForm(), new JournalLineForm()));

    public java.util.List<JournalLineForm> getLines() {
        return lines;
    }

    public void setLines(java.util.List<JournalLineForm> value) {
        this.lines = value;
    }
}
