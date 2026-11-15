package com.foresight.fcms.dashboard;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;
import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.finance.expense.service.ExpenseCategoryService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.finance.report.service.FinancialReportService;
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
    private final ExpenseCategoryService expenses;
    private final FinancialReportService finance;

    public DashboardService(
            AccessService access,
            WorkService work,
            ClientRevenueService clients,
            FinancialYearRepository years,
            LedgerService ledgers,
            ExpenseCategoryService expenses,
            FinancialReportService finance) {
        this.access = access;
        this.work = work;
        this.clients = clients;
        this.years = years;
        this.ledgers = ledgers;
        this.expenses = expenses;
        this.finance = finance;
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
        var actor = access.require("dashboard.read");
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
                        ? clients.portfolios(selected.id())
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
                            "income",
                            "dashboard.income",
                            calendar == null
                                    ? null
                                    : ledgers.yearTotal(LedgerKind.INCOME, calendar),
                            range == null ? null : "/finance/income" + range));
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
        if (actor.has("expense.read")) {
            ExpenseQuery query = new ExpenseQuery();
            if (selected != null) query.setYear(selected.label());
            String href =
                    selected == null
                            ? null
                            : "/finance/expenses?year="
                                    + org.springframework.web.util.UriUtils.encodeQueryParam(
                                            selected.label(),
                                            java.nio.charset.StandardCharsets.UTF_8);
            money.add(
                    new Money(
                            "expenses",
                            "dashboard.expenses",
                            selected == null ? null : expenses.total(query),
                            href));
        }
        if (actor.has("report.read"))
            money.add(
                    new Money(
                            "net",
                            "dashboard.feeExpenseMargin",
                            calendar == null ? null : finance.statement(calendar).netProfit(),
                            calendar == null ? null : "/reports?year=" + calendar));
        return new Overview(
                choices,
                selected,
                List.copyOf(metrics),
                portfolios,
                List.copyOf(money),
                actor.has("finance.read") && actor.has("client.read") && calendar != null);
    }
}
