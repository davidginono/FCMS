package com.foresight.fcms.dashboard;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {
    private final AccessService access;
    private final WorkService work;
    private final ClientRevenueService clients;

    public DashboardService(AccessService access, WorkService work, ClientRevenueService clients) {
        this.access = access;
        this.work = work;
        this.clients = clients;
    }

    public List<Metric> metrics() {
        var a = access.require("dashboard.read");
        var r = new ArrayList<Metric>();
        if (a.has("task.read")) r.add(new Metric("nav.tasks", work.count(WorkKind.TASK)));
        if (a.has("leave.read")) r.add(new Metric("nav.leaves", work.count(WorkKind.LEAVE)));
        if (a.has("staff-report.read"))
            r.add(new Metric("nav.staffReports", work.count(WorkKind.REPORT)));
        return List.copyOf(r);
    }

    public List<ClientRevenueService.Portfolio> portfolios() {
        return access.current().has("client.read") ? clients.portfolios() : List.of();
    }

    public record Metric(String label, long value) {}
}
