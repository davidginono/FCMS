package com.foresight.fcms.accounting.dto;

public class VoucherForm {
        @jakarta.validation.constraints.NotNull
        private java.util.UUID requestKey = java.util.UUID.randomUUID();
        public java.util.UUID getRequestKey() { return requestKey; }
        public void setRequestKey(java.util.UUID value) { requestKey=value; }
        @jakarta.validation.constraints.NotNull
        private java.time.LocalDate date = com.foresight.fcms.shared.time.ApplicationCalendar.today();
        public java.time.LocalDate getDate() { return date; }
        public void setDate(java.time.LocalDate value) { date=value; }
        @jakarta.validation.constraints.Size(max=160)
        private String party = "";
        public String getParty() { return party; }
        public void setParty(String value) { party=value; }
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100)
        private String reference = "";
        public String getReference() { return reference; }
        public void setReference(String value) { reference=value; }
        @jakarta.validation.constraints.Size(max=1000)
        private String description = "";
        public String getDescription() { return description; }
        public void setDescription(String value) { description=value; }
        @jakarta.validation.constraints.Size(max=200)
        private String evidenceReference = "";
        public String getEvidenceReference() { return evidenceReference; }
        public void setEvidenceReference(String value) { evidenceReference=value; }
        @jakarta.validation.constraints.Size(max=60)
        private String clientKey = "";
        public String getClientKey() { return clientKey; }
        public void setClientKey(String value) { clientKey=value; }
        @jakarta.validation.constraints.Positive
        private Long activityId = null;
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long value) { activityId=value; }
        @jakarta.validation.constraints.Positive
        private Long moneyAccountId = null;
        public Long getMoneyAccountId() { return moneyAccountId; }
        public void setMoneyAccountId(Long value) { moneyAccountId=value; }
        @jakarta.validation.constraints.Pattern(regexp="NONE|OPERATING|INVESTING|FINANCING|INTERNAL")
        private String cashFlow = "NONE";
        public String getCashFlow() { return cashFlow; }
        public void setCashFlow(String value) { cashFlow=value; }
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=1,max=50) @jakarta.validation.Valid
        private java.util.List<Row> rows = new java.util.ArrayList<>(java.util.List.of(new Row()));
        public java.util.List<Row> getRows() { return rows; }
        public void setRows(java.util.List<Row> value) { rows=value; }
    public static class Row {
        @jakarta.validation.constraints.Positive
        private Long mappingId = null;
        public Long getMappingId() { return mappingId; }
        public void setMappingId(Long value) { mappingId=value; }
        @jakarta.validation.constraints.Pattern(regexp="TOTAL|PRINCIPAL|INTEREST|FEES|TAX")
        private String component = "TOTAL";
        public String getComponent() { return component; }
        public void setComponent(String value) { component=value; }
        // Configured rows may use their approved transaction name; manual narration is required by the service.
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max=500)
        private String description = "";
        public String getDescription() { return description; }
        public void setDescription(String value) { description=value; }
        // Required for a configured/legacy pair; explicit-entry totals are derived by the service.
        @jakarta.validation.constraints.DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=12,fraction=2)
        private java.math.BigDecimal amount = null;
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal value) { amount=value; }
        @jakarta.validation.constraints.Positive
        private Long debitAccountId = null;
        public Long getDebitAccountId() { return debitAccountId; }
        public void setDebitAccountId(Long value) { debitAccountId=value; }
        @jakarta.validation.constraints.Positive
        private Long creditAccountId = null;
        public Long getCreditAccountId() { return creditAccountId; }
        public void setCreditAccountId(Long value) { creditAccountId=value; }
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max=50) @jakarta.validation.Valid
        private java.util.List<Entry> entries = new java.util.ArrayList<>();
        public java.util.List<Entry> getEntries() { return entries; }
        public void setEntries(java.util.List<Entry> value) { entries=value; }
    }
    public static class Entry {
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="DEBIT|CREDIT")
        private String side;
        public String getSide() { return side; }
        public void setSide(String value) { side=value; }
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
        private Long accountId;
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long value) { accountId=value; }
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=12,fraction=2)
        private java.math.BigDecimal amount;
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal value) { amount=value; }
    }
}
