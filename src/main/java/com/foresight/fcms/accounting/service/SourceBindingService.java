package com.foresight.fcms.accounting.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.dto.PostingSource;
import com.foresight.fcms.finance.service.FinancePostingSourceService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class SourceBindingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final FinancePostingSourceService finance;
    private final AuditService audit;

    public SourceBindingService(
            JdbcTemplate db,
            AccessService access,
            FinancePostingSourceService finance,
            AuditService audit) {
        this.db = db;
        this.access = access;
        this.finance = finance;
        this.audit = audit;
    }

    private static final String SELECT =
            "select l.*,j.state from gl_source_links l join gl_journals j on j.id=l.journal_id";

    private Binding row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Binding(
                r.getString("source_kind"),
                r.getLong("source_id"),
                r.getLong("journal_id"),
                r.getObject("mapping_id", Long.class),
                r.getString("evidence_reference"),
                r.getObject("source_date", LocalDate.class),
                r.getBigDecimal("signed_amount"),
                r.getString("state"));
    }

    @Transactional(readOnly = true)
    public Optional<Binding> current(String kind, long id) {
        access.require("accounting.read");
        return db
                .query(
                        SELECT
                                + " join gl_source_reservations x on x.journal_id=l.journal_id"
                                + " where x.source_kind=? and x.source_id=?",
                        this::row,
                        kind,
                        id)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Binding> forJournal(long id) {
        access.require("accounting.read");
        return db.query(SELECT + " where l.journal_id=?", this::row, id).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String kind, long id) {
        access.require("accounting.write");
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "FCMS_GL_SOURCE/" + kind + "/" + id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(String kind, long sourceId, long journal, Long mappingId, String evidence) {
        var actor = access.require("accounting.write");
        requireEvidence(evidence);
        lock(kind, sourceId);
        var s = finance.verify(kind, sourceId);
        db.update(
                "insert into"
                    + " gl_source_links(source_kind,source_id,journal_id,evidence_reference,linked_by,mapping_id,source_date,signed_amount)"
                    + " values (?,?,?,?,?,?,?,?)",
                kind,
                sourceId,
                journal,
                evidence.trim(),
                actor.id(),
                mappingId,
                s.date(),
                s.signedAmount());
        db.update(
                "insert into gl_source_reservations(source_kind,source_id,journal_id) values"
                        + " (?,?,?) on conflict(source_kind,source_id) do update set"
                        + " journal_id=excluded.journal_id",
                kind,
                sourceId,
                journal);
        audit.record(
                actor.id(),
                "accounting.source.bind",
                "gl_journals",
                journal,
                kind + "/" + sourceId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PostingSource reversal(long originalJournal, String kind, long id) {
        access.require("accounting.reverse");
        lock(kind, id);
        var source = finance.verify(kind, id);
        var original =
                forJournal(originalJournal)
                        .orElseThrow(() -> new IllegalArgumentException("gl.source.unavailable"));
        if (!Objects.equals(source.reversesKind(), original.kind())
                || !Objects.equals(source.reversesId(), original.sourceId())
                || source.signedAmount().compareTo(original.signedAmount().negate()) != 0)
            throw new IllegalArgumentException("gl.source.unavailable");
        return source;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyReview(long journal, long checker) {
        access.require("accounting.review");
        var binding = forJournal(journal);
        if (binding.isEmpty()) return;
        var l = binding.get();
        var source = finance.verify(l.kind(), l.sourceId());
        if (!source.date().equals(l.date())
                || source.signedAmount().compareTo(l.signedAmount()) != 0
                || source.actorId() == checker)
            throw new IllegalArgumentException("gl.source.unavailable");
        if (source.reversesId() != null) {
            var original = finance.verify(source.reversesKind(), source.reversesId());
            if (original.actorId() == checker)
                throw new IllegalArgumentException("gl.source.independentReview");
        }
    }

    @Transactional(readOnly = true)
    public boolean canReview(long journal) {
        var actor = access.require("accounting.read");
        var binding = forJournal(journal);
        if (binding.isEmpty()) return true;
        if (!actor.has("finance.gl-source.read")) return false;
        var source = finance.find(binding.get().kind(), binding.get().sourceId());
        if (source.actorId() == actor.id()) return false;
        return source.reversesId() == null
                || finance.find(source.reversesKind(), source.reversesId()).actorId() != actor.id();
    }

    @Transactional(readOnly = true)
    public RegisterPage<Attempt> history(String kind, long id, RegisterQuery q) {
        access.require("accounting.read");
        access.require("finance.gl-source.read");
        q.validate();
        return RegisterPage.of(
                db.query(
                        "select"
                            + " j.id,l.mapping_id,l.evidence_reference,j.state,l.linked_at,u.first_name||'"
                            + " '||u.last_name maker,t.id transaction_id,t.code transaction_code"
                            + " from gl_source_links l join gl_journals j on j.id=l.journal_id join"
                            + " iam_staff_directory u on u.id=l.linked_by left join"
                            + " gl_posting_mappings m on m.id=l.mapping_id left join"
                            + " gl_transactions t on t.id=m.transaction_id where l.source_kind=?"
                            + " and l.source_id=? and lower(l.evidence_reference) like ? escape '!'"
                            + " order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "j.id",
                                                "mapping",
                                                "t.code",
                                                "evidence",
                                                "l.evidence_reference",
                                                "status",
                                                "j.state",
                                                "date",
                                                "l.linked_at",
                                                "maker",
                                                "maker"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Attempt(
                                        r.getLong(1),
                                        r.getObject(2, Long.class),
                                        r.getString(3),
                                        r.getString(4),
                                        r.getTimestamp(5).toInstant(),
                                        r.getString(6),
                                        r.getObject(7, Long.class),
                                        r.getString(8)),
                        kind,
                        id,
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    public static void requireEvidence(String evidence) {
        if (evidence == null || evidence.isBlank() || evidence.length() > 200)
            throw new IllegalArgumentException("error.invalid");
    }

    public record Binding(
            String kind,
            long sourceId,
            long journalId,
            Long mappingId,
            String evidence,
            LocalDate date,
            BigDecimal signedAmount,
            String state) {}

    public record Attempt(
            long id,
            Long mappingId,
            String evidence,
            String state,
            java.time.Instant linkedAt,
            String maker,
            Long transactionId,
            String transactionCode) {}
}
