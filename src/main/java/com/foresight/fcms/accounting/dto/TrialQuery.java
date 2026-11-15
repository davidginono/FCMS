package com.foresight.fcms.accounting.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

import java.util.Set;

/** The original trial paper starts in account-code order. */
public class TrialQuery extends RegisterQuery {
    private static final Set<String> SORTS =
            Set.of(
                    "code",
                    "name",
                    "classification",
                    "opening",
                    "debit",
                    "credit",
                    "netChange",
                    "closing");

    public TrialQuery() {
        setSort("code");
        setDirection("asc");
    }

    @Override
    public void setSort(String value) {
        super.setSort(value != null && SORTS.contains(value) ? value : "code");
    }
}
