package com.foresight.fcms.finance.ledger.model;

public enum LedgerKind {
    INCOME("income", "nav.income"),
    PETTY("petty-cash", "nav.petty"),
    CLOUD("cloud-backup", "nav.cloud"),
    BULK("bulk-sms", "nav.bulk");
    public final String route, title;

    LedgerKind(String r, String t) {
        route = r;
        title = t;
    }

    public String getRoute() {
        return "/finance/" + route;
    }

    public String getTitle() {
        return title;
    }

    public static LedgerKind from(String s) {
        return java.util.Arrays.stream(values())
                .filter(k -> k.route.equals(s))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.invalid"));
    }
}
