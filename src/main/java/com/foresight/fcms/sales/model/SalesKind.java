package com.foresight.fcms.sales.model;

import java.util.List;

public enum SalesKind {
    PROSPECT("prospects", "sales.prospect", List.of("SACCOS", "AMCOS", "UNION")),
    INVOICE(
            "invoices",
            "sales.invoice",
            List.of("SACCOS", "AMCOS", "UNION", "MICROFINANCE", "OTHER"));
    private final String workspace;
    private final String claim;
    private final List<String> types;

    SalesKind(String workspace, String claim, List<String> types) {
        this.workspace = workspace;
        this.claim = claim;
        this.types = types;
    }

    public String getRoute() {
        return "/sales/" + workspace;
    }

    public String getClaim() {
        return claim;
    }

    public String getTitle() {
        return claim + ".title";
    }

    public List<String> getTypes() {
        return types;
    }

    public static SalesKind from(String workspace) {
        return switch (workspace) {
            case "prospects" -> PROSPECT;
            case "invoices" -> INVOICE;
            default -> throw new IllegalArgumentException("error.invalid");
        };
    }
}
