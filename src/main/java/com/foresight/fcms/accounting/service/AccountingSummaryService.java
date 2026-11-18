package com.foresight.fcms.accounting.service;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public accounting read contract for dashboard income and expenses. */
@Service
public class AccountingSummaryService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AccountingService books;

    public AccountingSummaryService(
            JdbcTemplate db, AccessService access, AccountingService books) {
        this.db = db;
        this.access = access;
        this.books = books;
    }

    public record Totals(BigDecimal income, BigDecimal expenses) {}

    @Transactional(readOnly = true)
    public Totals posted(int year) {
        access.require("accounting.report");
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("error.invalid");
        if (books.books().map(book -> !book.ready()).orElse(true)) return new Totals(null, null);
        var first = LocalDate.of(year, 1, 1);
        return db.queryForObject(
                "select coalesce(sum(l.credit-l.debit) filter(where"
                    + " a.classification='INCOME'),0),coalesce(sum(l.debit-l.credit) filter(where"
                    + " a.classification='EXPENSE'),0) from gl_lines l join gl_accounts a on"
                    + " a.id=l.account_id join gl_journals j on j.id=l.journal_id where"
                    + " j.state='POSTED' and j.kind<>'OPENING' and j.effective_date>=? and"
                    + " j.effective_date<? and not exists(select 1 from gl_journals original where"
                    + " original.id=j.reverses_id and original.kind='OPENING')",
                (r, n) -> new Totals(r.getBigDecimal(1), r.getBigDecimal(2)),
                first,
                first.plusYears(1));
    }
}
