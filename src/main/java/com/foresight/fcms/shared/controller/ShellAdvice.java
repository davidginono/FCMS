package com.foresight.fcms.shared.controller;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class ShellAdvice {
    private final AccessService access;

    public ShellAdvice(AccessService a) {
        access = a;
    }

    @ModelAttribute
    public void shell(jakarta.servlet.http.HttpServletRequest req, Model m) {
        var uri = req.getRequestURI();
        if (req.getUserPrincipal() != null
                && !uri.equals("/error")
                && !uri.startsWith("/password/")
                && !uri.startsWith("/lookups/")
                && !uri.endsWith(".pdf")
                && !uri.endsWith(".csv")
                && !uri.endsWith("/choices")
                && !uri.endsWith("/next-code")
                && !uri.endsWith("/form-session")
                && !uri.equals("/accounting/accounts/children")
                && !uri.endsWith("/photo")) {
            var actor = access.current();
            m.addAttribute("permissions", actor.claims());
            m.addAttribute("actorId", actor.id());
            m.addAttribute("staffWorkspace", actor.has("workspace.staff"));
            m.addAttribute("accountingWorkspace", actor.has("workspace.accounting"));
            m.addAttribute("directorWorkspace", actor.has("workspace.director"));
            m.addAttribute("administrationWorkspace", actor.has("workspace.admin"));
            var routes =
                    java.util.Map.ofEntries(
                            java.util.Map.entry("/accounting/accounts", "gl.accounts"),
                            java.util.Map.entry("/accounting/journals", "gl.journals"),
                            java.util.Map.entry("/accounting/sources", "gl.sources"),
                            java.util.Map.entry(
                                    "/accounting/config/activities", "config.activities"),
                            java.util.Map.entry(
                                    "/accounting/config/transactions", "config.transactions"),
                            java.util.Map.entry("/accounting/statements", "gl.statements"),
                            java.util.Map.entry("/sales/prospects", "sales.prospect.title"),
                            java.util.Map.entry("/sales/invoices", "sales.invoice.title"),
                            java.util.Map.entry("/tasks", "nav.tasks"),
                            java.util.Map.entry("/leaves", "nav.leaves"),
                            java.util.Map.entry("/staff-reports", "nav.staffReports"),
                            java.util.Map.entry("/admin/users", "nav.users"),
                            java.util.Map.entry("/account", "nav.account"),
                            java.util.Map.entry("/finance/petty-cash", "nav.petty"),
                            java.util.Map.entry("/finance/cloud-backup", "nav.cloud"),
                            java.util.Map.entry("/finance/bulk-sms", "nav.bulk"),
                            java.util.Map.entry("/clients/saccos", "client.category.saccos"),
                            java.util.Map.entry("/clients/amcos", "client.category.amcos"),
                            java.util.Map.entry("/clients/unions", "client.category.unions"),
                            java.util.Map.entry(
                                    "/clients/microfinance",
                                    "client.category.microfinance_clients"),
                            java.util.Map.entry(
                                    "/clients/others", "client.category.other_clients"));
            routes.entrySet().stream()
                    .filter(e -> req.getServletPath().startsWith(e.getKey() + "/"))
                    .findFirst()
                    .ifPresent(
                            e -> {
                                m.addAttribute("breadcrumbUrl", e.getKey());
                                m.addAttribute("breadcrumbLabel", e.getValue());
                                m.addAttribute(
                                        "breadcrumbCurrent",
                                        uri.endsWith("/new")
                                                ? "common.create"
                                                : uri.endsWith("/edit") || uri.endsWith("/password")
                                                        ? "common.edit"
                                                        : "common.details");
                            });
        }
    }
}
