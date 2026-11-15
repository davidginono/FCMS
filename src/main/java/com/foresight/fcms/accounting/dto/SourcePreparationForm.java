package com.foresight.fcms.accounting.dto;

public class SourcePreparationForm {
    @jakarta.validation.constraints.Positive private Long mappingId;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    private String evidenceReference = "";

    @jakarta.validation.constraints.NotNull
    private java.util.UUID requestKey = java.util.UUID.randomUUID();

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long value) {
        mappingId = value;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public void setEvidenceReference(String value) {
        evidenceReference = value;
    }

    public java.util.UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(java.util.UUID value) {
        requestKey = value;
    }
}
