package com.foresight.fcms.accounting.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

import java.util.Set;

public class CoaQuery extends RegisterQuery {
    private String classification = "";
    private String kind = "";

    public CoaQuery() {
        setSort("code");
        setDirection("asc");
    }

    public String getClassification() { return classification; }
    public void setClassification(String value) { classification = value == null ? "" : value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value == null ? "" : value; }

    @Override
    public void validate() {
        super.validate();
        if (!Set.of("", "ASSET", "LIABILITY", "EQUITY", "EXPENSE", "INCOME").contains(classification)
                || !Set.of("", "GROUP", "POSTING").contains(kind)
                || !Set.of("", "ACTIVE", "INACTIVE").contains(getStatus())
                || !Set.of("code", "name", "parent", "classification", "kind", "normal", "status").contains(getSort()))
            throw new IllegalArgumentException("error.invalid");
    }

    public boolean filtered() {
        return !getSearch().isEmpty() || !getStatus().isEmpty() || !classification.isEmpty() || !kind.isEmpty();
    }
}
