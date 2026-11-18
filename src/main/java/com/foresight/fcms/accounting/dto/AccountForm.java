package com.foresight.fcms.accounting.dto;

public class AccountForm {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Pattern(regexp = "|GROUP|POSTING")
    private String mode = "";

    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }

    @jakarta.validation.constraints.NotNull private Long parentId;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long value) {
        this.parentId = value;
    }

    @jakarta.validation.constraints.Pattern(regexp = "[1-5][0-9]{5}")
    private String code = "";

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 160)
    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    @jakarta.validation.constraints.Size(max = 500)
    private String description = "";

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
    }

    @jakarta.validation.constraints.Pattern(regexp = "DEBIT|CREDIT")
    private String normalBalance = "DEBIT";

    public String getNormalBalance() {
        return normalBalance;
    }

    public void setNormalBalance(String value) {
        this.normalBalance = value;
    }

    private boolean cashAccount = false;

    public boolean getCashAccount() {
        return cashAccount;
    }

    public void setCashAccount(boolean value) {
        this.cashAccount = value;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        this.version = value;
    }
}
