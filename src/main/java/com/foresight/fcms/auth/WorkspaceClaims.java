package com.foresight.fcms.auth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Workspace scope is independent of the capabilities assigned inside that workspace. */
public final class WorkspaceClaims {
    private WorkspaceClaims() {}

    private static final Set<String> PERSONAL = Set.of(
            "dashboard.read", "account.write", "client.lookup",
            "task.read", "task.write", "leave.read", "leave.write",
            "staff-report.read", "staff-report.write", "expense.read", "expense.write");

    public static Set<String> effective(Collection<String> assigned) {
        var claims = new HashSet<>(assigned);
        if (claims.contains("workspace.admin") || claims.contains("workspace.accounting") || claims.contains("workspace.director")) {
            claims.remove("workspace.staff");
        } else {
            claims.retainAll(PERSONAL);
            claims.add("workspace.staff");
        }
        return Set.copyOf(claims);
    }
}
