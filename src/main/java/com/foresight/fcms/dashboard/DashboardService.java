package com.foresight.fcms.dashboard;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.reference.FinancialYearRepository;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DashboardService {
    private final AccessService access;
    private final WorkService work;
    private final ClientRevenueService clients;
    private final FinancialYearRepository years;
    private final LedgerService ledgers;
    private final com.foresight.fcms.accounting.service.AccountingSummaryService accounting;

    public DashboardService(
            AccessService access,
            WorkService work,
            ClientRevenueService clients,
            FinancialYearRepository years,
            LedgerService ledgers,
            com.foresight.fcms.accounting.service.AccountingSummaryService accounting) {
        this.access = access;
        this.work = work;
        this.clients = clients;
        this.years = years;
        this.ledgers = ledgers;
        this.accounting = accounting;
    }

    public record Year(long id, String label) {
        public Integer calendarYear() {
            if (!label.matches("[0-9]{4}")) return null;
            int year = Integer.parseInt(label);
            return year >= 2000 && year <= 2100 ? year : null;
        }
    }

    public record Metric(String label, long value, String href) {}

    public record Money(String key, String label, BigDecimal value, String href) {}

    public record Overview(
            List<Year> years,
            Year selected,
            List<Metric> work,
            List<ClientRevenueService.Portfolio> portfolios,
            List<Money> money,
            boolean cloudBilling) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview overview(Long financialYearId) {
        return overview(financialYearId, "saccos");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview overview(Long financialYearId, String category) {
        if (!Set.of("saccos", "amcos", "unions", "microfinance", "others").contains(category))
            throw new IllegalArgumentException("error.invalid");
        var actor = access.require("dashboard.read");
        if (actor.has("workspace.staff")) return personal(actor);
        var choices =
                years.findTop1001ByOrderByFyearDesc().stream()
                        .map(y -> new Year(y.getId(), y.getFyear()))
                        .toList();
        if (choices.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        Year selected =
                financialYearId == null
                        ? (choices.isEmpty() ? null : choices.getFirst())
                        : choices.stream()
                                .filter(y -> y.id() == financialYearId)
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("error.invalid"));
        var metrics = new ArrayList<Metric>();
        for (var kind : WorkKind.values())
            if (actor.has(kind.claim + ".read"))
                metrics.add(new Metric(kind.title, work.count(kind), kind.getRoute()));
        var portfolios =
                actor.has("client.read") && selected != null
                        ? clients.portfolios(selected.id()).stream()
                                .filter(p -> p.slug().equals(category))
                                .toList()
                        : List.<ClientRevenueService.Portfolio>of();
        Integer calendar = selected == null ? null : selected.calendarYear();
        var money = new ArrayList<Money>();
        if (actor.has("finance.read")) {
            String range =
                    calendar == null
                            ? null
                            : "?from=" + calendar + "-01-01&to=" + calendar + "-12-31";
            money.add(
                    new Money(
                            "bulk",
                            "dashboard.bulkNet",
                            calendar == null ? null : ledgers.yearTotal(LedgerKind.BULK, calendar),
                            range == null ? null : "/finance/bulk-sms" + range));
            money.add(
                    new Money(
                            "petty",
                            "dashboard.pettyBalance",
                            ledgers.balance(LedgerKind.PETTY).amount(),
                            "/finance/petty-cash"));
        }
        if (actor.has("accounting.report")) {
            var totals =
                    calendar == null
                            ? new com.foresight.fcms.accounting.service.AccountingSummaryService
                                    .Totals(null, null)
                            : accounting.posted(calendar);
            String range =
                    calendar == null
                            ? null
                            : "?from=" + calendar + "-01-01&to=" + calendar + "-12-31";
            money.add(
                    new Money(
                            "income",
                            "dashboard.postedIncome",
                            totals.income(),
                            range == null
                                    ? null
                                    : "/accounting/statements/income-statement" + range));
            money.add(
                    new Money(
                            "expenses",
                            "dashboard.postedExpenses",
                            totals.expenses(),
                            range == null
                                    ? null
                                    : "/accounting/statements/income-statement" + range));
        }
        return new Overview(
                choices, selected, List.copyOf(metrics), portfolios, List.copyOf(money), false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview adminOverview(Long financialYearId) {
        access.require("workspace.admin");
        return overview(financialYearId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview staffOverview() {
        access.require("workspace.staff");
        return personal(access.require("dashboard.read"));
    }

    public String home() {
        var actor = access.current();
        if (actor.has("workspace.accounting")) return "/accounting/dashboard";
        if (actor.has("workspace.director")) return "/director/dashboard";
        if (actor.has("workspace.admin")) return "/admin/users";
        return "/staff/dashboard";
    }

    public String administratorHome() {
        var actor=access.require("workspace.admin");
        actor.require("iam.user.read");
        return "/admin/users";
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview accountantOverview(Long year, String category) {
        access.require("workspace.accounting");
        return overview(year, category);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Overview directorOverview(Long year, String category) {
        access.require("workspace.director");
        return overview(year, category);
    }

    private Overview personal(AccessService.Actor actor) {
        var metrics = new ArrayList<Metric>();
        for (var kind : List.of(WorkKind.LEAVE, WorkKind.REPORT))
            if (actor.has(kind.claim + ".read"))
                metrics.add(
                        new Metric(
                                "workspace.my." + kind.claim, work.count(kind), kind.getRoute()));

        return new Overview(List.of(), null, List.copyOf(metrics), List.of(), List.of(), false);
    }
}
