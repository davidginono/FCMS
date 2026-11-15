package com.foresight.fcms.accounting.dto;

import com.foresight.fcms.shared.dto.RegisterQuery;

import java.util.Set;

public class LedgerQuery extends RegisterQuery {
    private static final Set<String> SORTS =
            Set.of("id", "date", "reference", "kind", "description", "debit", "credit", "balance");

    public LedgerQuery() {
        setSort("date");
        setDirection("asc");
    }

    @Override
    public void setSort(String value) {
        super.setSort(value != null && SORTS.contains(value) ? value : "date");
    }
}
