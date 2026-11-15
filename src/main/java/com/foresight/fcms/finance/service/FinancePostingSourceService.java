package com.foresight.fcms.finance.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.dto.PostingSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancePostingSourceService {
    private final JdbcTemplate db;
    private final AccessService access;

    public FinancePostingSourceService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PostingSource find(String kind, long id) {
        access.require("finance.gl-source.read");
        return read(kind, id);
    }

    @Transactional
    public PostingSource verify(String kind, long id) {
        access.require("finance.gl-source.read");
        if (kind.startsWith("LEDGER_")
                && java.util.Set.of("INCOME", "PETTY", "CLOUD", "BULK").contains(kind.substring(7)))
            db.queryForList(
                    "select kind from ledger_accounts where kind=? for share", kind.substring(7));
        else if (kind.equals("EXPENSE"))
            db.queryForList("select id from expenses where id=? for share", id);
        else if (kind.equals("EXPENSE_REVERSAL"))
            db.queryForList(
                    "select e.id from expenses e join expense_reversals r on r.expense_id=e.id"
                            + " where r.id=? for share of e",
                    id);
        else throw new IllegalArgumentException("error.invalid");
        var source = read(kind, id);
        if (source.date() == null
                || !java.util.Set.of("ACTIVE", "REVERSED").contains(source.state()))
            throw new IllegalArgumentException("gl.source.unavailable");
        return source;
    }

    @Transactional(readOnly = true)
    public Long paymentEvidenceExpense(String kind, long id) {
        access.require("finance.gl-source.read");
        return db
                .queryForList(
                        "select c.expense_id from finance_gl_sources s join"
                            + " expense_legacy_payment_candidates c on c.expense_id=case when"
                            + " s.source_kind='EXPENSE' then s.source_id else s.reverses_id end"
                            + " where s.source_kind in ('EXPENSE','EXPENSE_REVERSAL') and"
                            + " s.source_kind=? and s.source_id=?",
                        Long.class,
                        kind,
                        id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private PostingSource read(String kind, long id) {
        return db
                .query(
                        "select * from finance_gl_sources where source_kind=? and source_id=?",
                        (r, n) ->
                                new PostingSource(
                                        r.getString("source_kind"),
                                        r.getLong("source_id"),
                                        r.getString("source_event"),
                                        r.getObject("source_date", java.time.LocalDate.class),
                                        r.getString("reference"),
                                        r.getString("description"),
                                        r.getBigDecimal("signed_amount"),
                                        r.getLong("actor_id"),
                                        r.getString("source_state"),
                                        r.getString("reverses_kind"),
                                        r.getObject("reverses_id", Long.class)),
                        kind,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }
}
