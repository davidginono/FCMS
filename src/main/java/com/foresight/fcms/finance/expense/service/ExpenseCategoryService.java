package com.foresight.fcms.finance.expense.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.shared.dto.RegisterPage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Legacy expense-category register totals; these include each selected workflow state. */
@Service
public class ExpenseCategoryService {
    private final JdbcTemplate db;
    private final AccessService access;

    public ExpenseCategoryService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    private static final String WHERE =
            " from expenses e join expense_status s on s.id=e.status_id where (? or e.user_id=? or"
                    + " (? and e.created_by=?)) and (?::bigint is null or e.user_id=?) and"
                    + " e.financial_year=? and lower(e.category) like ? escape '!' and (?='' or"
                    + " e.category=?) and (?='' or s.name=?) and (?::date is null or"
                    + " e.date_created>=?::date) and (?::date is null or e.date_created<=?::date)";

    public record Category(String category, BigDecimal amount) {}

    public record Summary(RegisterPage<Category> records, BigDecimal total) {}

    public record Export(List<Category> rows, BigDecimal total) {}

    private Object[] parameters(ExpenseQuery q) {
        var actor = access.require("expense.read");
        q.validate();
        if (q.getYear().isBlank()) throw new IllegalArgumentException("error.invalid");
        return new Object[] {
            actor.has("expense.approve"),
            actor.id(),
            actor.has("expense.assign"),
            actor.id(),
            q.getOwnerId(),
            q.getOwnerId(),
            q.getYear(),
            q.pattern(),
            q.getCategory(),
            q.getCategory(),
            q.getStatus(),
            q.getStatus(),
            q.getFrom(),
            q.getFrom(),
            q.getTo(),
            q.getTo()
        };
    }

    private List<Category> rows(ExpenseQuery q, Object[] parameters, int limit, long offset) {
        var args = new ArrayList<Object>(Arrays.asList(parameters));
        args.add(limit);
        args.add(offset);
        String column = "amount".equals(q.getSort()) ? "sum(e.amount)" : "e.category";
        return db.query(
                "select e.category,sum(e.amount) amount"
                        + WHERE
                        + " group by e.category order by "
                        + column
                        + " "
                        + q.getDirection()
                        + ",e.category asc limit ? offset ?",
                (r, n) -> new Category(r.getString("category"), r.getBigDecimal("amount")),
                args.toArray());
    }

    private BigDecimal total(Object[] parameters) {
        return db.queryForObject(
                "select coalesce(sum(e.amount),0)" + WHERE, BigDecimal.class, parameters);
    }

    /** Whole authorized expense amount, independent of category/page count. */
    @Transactional(readOnly = true)
    public BigDecimal total(ExpenseQuery query) {
        return total(parameters(query));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(ExpenseQuery q) {
        var parameters = parameters(q);
        return new Summary(
                RegisterPage.of(rows(q, parameters, q.getSize() + 1, q.offset()), q),
                total(parameters));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Export export(ExpenseQuery q) {
        var parameters = parameters(q);
        var rows = rows(q, parameters, 1001, 0);
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return new Export(List.copyOf(rows), total(parameters));
    }
}
