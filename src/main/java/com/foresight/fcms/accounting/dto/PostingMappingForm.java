package com.foresight.fcms.accounting.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public class PostingMappingForm {
    private Long transactionId;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long value) {
        transactionId = value;
    }

    @NotBlank
    @Pattern(
            regexp =
                    "MANUAL_JOURNAL|INCOME_IN|PETTY_IN|PETTY_OUT|CLOUD_IN|CLOUD_OUT|BULK_IN|BULK_OUT|EXPENSE_PAYMENT|INCOME_OPENING_REVERSAL|PETTY_OPENING_REVERSAL|CLOUD_OPENING_REVERSAL|BULK_OPENING_REVERSAL|EXPENSE_OPENING_REVERSAL")
    private String event = "INCOME_IN";

    @Positive private Long debitAccountId;
    @Positive private Long creditAccountId;

    @NotNull @jakarta.validation.Valid @Size(max = 5)
    private java.util.List<RuleForm> rules = new java.util.ArrayList<>();

    public java.util.List<RuleForm> getRules() { return rules; }
    public void setRules(java.util.List<RuleForm> value) { rules = value; }

    public static class RuleForm {
        @NotBlank @Pattern(regexp="TOTAL|PRINCIPAL|INTEREST|FEES|TAX")
        private String component = "TOTAL";
        @NotNull @Positive private Long debitAccountId;
        @NotNull @Positive private Long creditAccountId;
        public String getComponent() { return component; }
        public void setComponent(String value) { component=value; }
        public Long getDebitAccountId() { return debitAccountId; }
        public void setDebitAccountId(Long value) { debitAccountId=value; }
        public Long getCreditAccountId() { return creditAccountId; }
        public void setCreditAccountId(Long value) { creditAccountId=value; }
    }

    @NotBlank
    @Pattern(regexp = "JOURNAL|RECEIPT|PAYMENT")
    private String voucherKind = "JOURNAL";

    @NotBlank
    @Pattern(regexp = "NONE|OPERATING|INVESTING|FINANCING|INTERNAL")
    private String cashFlow = "NONE";

    @NotBlank
    @Size(max = 200)
    private String policyReference = "";

    @NotNull private UUID requestKey = UUID.randomUUID();

    public String getEvent() {
        return event;
    }

    public void setEvent(String value) {
        event = value;
    }

    public Long getDebitAccountId() {
        return debitAccountId;
    }

    public void setDebitAccountId(Long value) {
        debitAccountId = value;
    }

    public Long getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(Long value) {
        creditAccountId = value;
    }

    public String getVoucherKind() {
        return voucherKind;
    }

    public void setVoucherKind(String value) {
        voucherKind = value;
    }

    public String getCashFlow() {
        return cashFlow;
    }

    public void setCashFlow(String value) {
        cashFlow = value;
    }

    public String getPolicyReference() {
        return policyReference;
    }

    public void setPolicyReference(String value) {
        policyReference = value;
    }

    public UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(UUID value) {
        requestKey = value;
    }
}
