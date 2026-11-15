package com.foresight.fcms.finance.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDate;

@Service
public class FinanceCutoverService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;

    public FinanceCutoverService(JdbcTemplate db, AccessService access, AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public java.time.Instant approve(LocalDate date, String policy) {
        var actor = access.require("accounting.review");
        if (date == null
                || date.isAfter(LocalDate.now())
                || policy == null
                || policy.isBlank()
                || policy.length() > 200) throw new IllegalArgumentException("error.invalid");
        db.queryForList("select pg_advisory_xact_lock(hashtextextended('FCMS_FINANCE_CUTOVER',0))");
        var existing =
                db.queryForList(
                        "select starts_on,policy_reference,approved_at from finance_gl_cutover");
        if (!existing.isEmpty()) {
            if (!existing.getFirst().get("starts_on").toString().equals(date.toString())
                    || !existing.getFirst().get("policy_reference").equals(policy))
                throw new IllegalArgumentException("error.conflict");
            return ((java.sql.Timestamp) existing.getFirst().get("approved_at")).toInstant();
        }
        var approved =
                db.queryForObject(
                                "insert into"
                                    + " finance_gl_cutover(id,starts_on,policy_reference,approved_by)"
                                    + " values(true,?,?,?) returning approved_at",
                                java.time.OffsetDateTime.class,
                                date,
                                policy,
                                actor.id())
                        .toInstant();
        audit.record(actor.id(), "finance.cutover.approve", "finance_gl_cutover", null, policy);
        return approved;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void periodState(
            long period, LocalDate from, LocalDate to, boolean closed, String reason) {
        var actor = access.require("accounting.period.close");
        if (period < 1
                || from == null
                || to == null
                || to.isBefore(from)
                || reason == null
                || reason.isBlank()
                || reason.length() > 500) throw new IllegalArgumentException("error.invalid");
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended('FCMS_FINANCE_PERIOD_CLOSE',0))");
        var old =
                db.queryForList(
                        "select starts_on,ends_on from finance_gl_period_guards where period_id=?",
                        period);
        if (!old.isEmpty()
                && (!old.getFirst().get("starts_on").toString().equals(from.toString())
                        || !old.getFirst().get("ends_on").toString().equals(to.toString())))
            throw new IllegalArgumentException("error.conflict");
        db.update(
                "insert into"
                    + " finance_gl_period_guards(period_id,starts_on,ends_on,closed,changed_by,reason)"
                    + " values (?,?,?,?,?,?) on conflict(period_id) do update set"
                    + " closed=excluded.closed,changed_by=excluded.changed_by,reason=excluded.reason,changed_at=clock_timestamp()",
                period,
                from,
                to,
                closed,
                actor.id(),
                reason.trim());
        audit.record(
                actor.id(),
                closed ? "finance.period.close" : "finance.period.reopen",
                "finance_gl_period_guards",
                period,
                reason.trim());
    }
}
