package com.foresight.fcms.workforce.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

/** Date basis is explicit so task creation-date reporting preserves existing start-date callers. */
public class WorkQuery extends RegisterQuery {
    private String dateBasis = "START";

    public String getDateBasis() {
        return dateBasis;
    }

    public void setDateBasis(String value) {
        if (!java.util.Set.of("START", "CREATED").contains(value))
            throw new IllegalArgumentException("error.invalid");
        dateBasis = value;
    }
}
