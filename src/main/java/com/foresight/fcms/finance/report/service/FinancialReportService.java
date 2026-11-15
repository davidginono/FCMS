package com.foresight.fcms.finance.report.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FinancialReportService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final ClientRevenueService clients;

    public FinancialReportService(
            JdbcTemplate db, AccessService access, ClientRevenueService clients) {
        this.db = db;
        this.access = access;
        this.clients = clients;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Statement statement(int year) {
        access.require("report.read");
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("error.invalid");
        var rows = clients.summary(year);
        var from = java.time.LocalDate.of(year, 1, 1);
        var to = from.plusYears(1);
        BigDecimal income =
                db.queryForObject(
                        "select coalesce(sum(amount_delta),0) from ledger_entries where"
                                + " kind='INCOME' and transaction_date>=? and transaction_date<?",
                        BigDecimal.class,
                        from,
                        to);
        BigDecimal expense =
                db.queryForObject(
                        "select coalesce(sum(amount),0) from expenses where status_id=(select id"
                                + " from expense_status where name='paid') and date_created>=? and"
                                + " date_created<?",
                        BigDecimal.class,
                        from,
                        to);
        BigDecimal clientProfit =
                rows.stream()
                        .map(ClientRevenueService.Revenue::profit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Statement(
                year, rows, income, expense, clientProfit.add(income).subtract(expense));
    }

    public record Statement(
            int year,
            List<ClientRevenueService.Revenue> clients,
            BigDecimal otherIncome,
            BigDecimal expenses,
            BigDecimal netProfit) {
        public BigDecimal totalIncome() {
            return clients.stream()
                    .map(ClientRevenueService.Revenue::received)
                    .reduce(otherIncome, BigDecimal::add);
        }

        public BigDecimal totalExpenses() {
            return clients.stream()
                    .map(ClientRevenueService.Revenue::costs)
                    .reduce(expenses, BigDecimal::add);
        }
    }
}
