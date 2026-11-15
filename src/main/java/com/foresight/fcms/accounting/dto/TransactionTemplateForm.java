package com.foresight.fcms.accounting.dto;

public class TransactionTemplateForm extends PostingMappingForm {
    @jakarta.validation.constraints.PositiveOrZero private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }
}
