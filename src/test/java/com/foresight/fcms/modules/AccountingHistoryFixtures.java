package com.foresight.fcms.modules;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.UUID;

/** Historical data fixtures for surviving workflows; no application setup endpoints are used. */
final class AccountingHistoryFixtures {
    private AccountingHistoryFixtures() {}

    static void books(JdbcTemplate db, LocalDate opening, String mode, String maker, String checker) {
        db.update("insert into gl_books(id,opening_date,policy_reference,opening_mode,state,maker_id,checker_id,approved_at)"
                + " select true,?,'Retained synthetic policy',?,'APPROVED',m.id,c.id,current_timestamp"
                + " from users m cross join users c where m.email=? and c.email=?", opening, mode, maker, checker);
        db.update("insert into finance_gl_cutover(id,starts_on,policy_reference,approved_by,approved_at)"
                + " select true,opening_date,policy_reference,checker_id,approved_at from gl_books");
    }

    static long period(JdbcTemplate db, String name, LocalDate from, LocalDate to, String maker) {
        return db.queryForObject("insert into gl_periods(name,starts_on,ends_on,created_by)"
                + " select ?,?,?,id from users where email=? returning id", Long.class, name, from, to, maker);
    }

    static void periodState(JdbcTemplate db, long id, boolean closed, String actor) {
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(java.util.Objects.requireNonNull(db.getDataSource())));
        tx.executeWithoutResult(status -> {
            // Match the retained Finance mirror before changing the period, in one transaction.
            db.update("insert into finance_gl_period_guards(period_id,starts_on,ends_on,closed,changed_by,reason)"
                    + " select id,starts_on,ends_on,?,(select id from users where email=?),'Historical fixture state'"
                    + " from gl_periods where id=? on conflict(period_id) do update set"
                    + " closed=excluded.closed,changed_by=excluded.changed_by,reason=excluded.reason",
                    closed, actor, id);
            db.update("update gl_periods set state=?,version=version+1,closed_by=(select id from users where email=?),"
                    + "closed_at=current_timestamp,close_reason='Historical fixture state' where id=?",
                    closed ? "CLOSED" : "OPEN", actor, id);
        });
    }

    static long equity(JdbcTemplate db, String kind, long target, Object payload, String maker, String checker) {
        String json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(payload);
        long id = db.queryForObject("insert into gl_equity_reporting(kind,target_id,payload,reason,maker_id,request_key,payload_hash)"
                + " select ?,?,?::jsonb,'Retained synthetic evidence',id,?,? from users where email=? returning id",
                Long.class, kind, target, json, UUID.randomUUID(), "0".repeat(64), maker);
        db.update("update gl_equity_reporting set state='APPROVED',checker_id=(select id from users where email=?),"
                + "reviewed_at=current_timestamp,review_reason='Retained independent review',version=version+1 where id=?", checker, id);
        return id;
    }
}
