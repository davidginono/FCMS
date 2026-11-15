package com.foresight.fcms.workforce.model;

public enum WorkKind {
    TASK("tasks", "task", "nav.tasks"),
    LEAVE("leaves", "leave", "nav.leaves"),
    REPORT("staff-reports", "staff-report", "nav.staffReports");
    public final String route, claim, title;

    WorkKind(String r, String c, String t) {
        route = r;
        claim = c;
        title = t;
    }

    public String getClaim() {
        return claim;
    }

    public String getRoute() {
        return "/" + route;
    }

    public String getTitle() {
        return title;
    }

    public static WorkKind from(String route) {
        return java.util.Arrays.stream(values())
                .filter(k -> k.route.equals(route))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.invalid"));
    }
}
