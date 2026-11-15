package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.AccountingViews.*;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Validated
public class JournalService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final SourceBindingService bindings;

    public JournalService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            SourceBindingService bindings) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.bindings = bindings;
    }

    private static final String SELECT =
            "select j.*,exists(select 1 from gl_source_links sl where sl.journal_id=j.id)"
                + " source_linked,m.first_name||' '||m.last_name maker,coalesce(c.first_name||'"
                + " '||c.last_name,'') checker,(select r.id from gl_journals r where"
                + " r.reverses_id=j.id and r.state<>'CANCELLED') reversed_by,(select"
                + " coalesce(sum(l.debit),0) from gl_lines l where l.journal_id=j.id) total from"
                + " gl_journals j join iam_staff_directory m on m.id=j.maker_id left join"
                + " iam_staff_directory c on c.id=j.checker_id";

    private Journal row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Journal(
                r.getLong("id"),
                r.getString("kind"),
                r.getString("reference"),
                r.getString("description"),
                r.getString("evidence_reference"),
                r.getObject("effective_date", LocalDate.class),
                r.getLong("period_id"),
                r.getString("cash_flow"),
                r.getString("state"),
                r.getLong("maker_id"),
                r.getString("maker"),
                r.getObject("checker_id", Long.class),
                r.getString("checker"),
                r.getLong("version"),
                r.getObject("reverses_id", Long.class),
                r.getObject("reversed_by", Long.class),
                r.getString("review_comment"),
                r.getBigDecimal("total"),
                r.getBoolean("source_linked"),
                List.of());
    }

    @Transactional(readOnly = true)
    public RegisterPage<Journal> list(RegisterQuery q, String kind) {
        access.require("accounting.read");
        q.validate();
        if (!Set.of("", "JOURNAL", "RECEIPT", "PAYMENT", "OPENING", "REVERSAL").contains(kind))
            throw new IllegalArgumentException("error.invalid");
        return RegisterPage.of(
                db.query(
                        SELECT
                                + " where (?='' or j.kind=?) and (?='' or j.state=?) and"
                                + " lower(j.reference||' '||j.description) like ? escape '!' and"
                                + " (?::date is null or j.effective_date>=?::date) and (?::date is"
                                + " null or j.effective_date<=?::date) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "j.id",
                                                "date",
                                                "j.effective_date",
                                                "reference",
                                                "j.reference",
                                                "kind",
                                                "j.kind",
                                                "status",
                                                "j.state",
                                                "amount",
                                                "total",
                                                "maker",
                                                "maker"))
                                + " limit ? offset ?",
                        this::row,
                        kind,
                        kind,
                        q.getStatus(),
                        q.getStatus(),
                        q.pattern(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public Journal find(long id) {
        access.require("accounting.read");
        var j =
                db.query(SELECT + " where j.id=?", this::row, id).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("error.missing"));
        var lines =
                db.query(
                        "select account_id,account_code,account_name,debit,credit,description from"
                                + " gl_lines where journal_id=? order by line_number limit 50",
                        (r, n) ->
                                new Line(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getBigDecimal(4),
                                        r.getBigDecimal(5),
                                        r.getString(6)),
                        id);
        return new Journal(
                j.id(),
                j.kind(),
                j.reference(),
                j.description(),
                j.evidenceReference(),
                j.date(),
                j.periodId(),
                j.cashFlow(),
                j.state(),
                j.makerId(),
                j.maker(),
                j.checkerId(),
                j.checker(),
                j.version(),
                j.reversesId(),
                j.reversedBy(),
                j.reviewComment(),
                j.total(),
                j.sourceLinked(),
                List.copyOf(lines));
    }

    @Transactional(readOnly = true)
    public JournalForm form(long id) {
        var actor = access.require("accounting.write");
        var j = find(id);
        if (j.makerId() != actor.id()
                || !Set.of("DRAFT", "REJECTED").contains(j.state())
                || j.kind().equals("REVERSAL")
                || j.sourceLinked()) throw new IllegalArgumentException("error.invalid");
        var f = new JournalForm();
        f.setKind(j.kind());
        f.setReference(j.reference());
        f.setDescription(j.description());
        f.setEvidenceReference(j.evidenceReference());
        f.setDate(j.date());
        f.setCashFlow(j.cashFlow());
        f.setVersion(j.version());
        f.setLines(
                j.lines().stream()
                        .map(
                                l -> {
                                    var x = new JournalLineForm();
                                    x.setAccountId(l.accountId());
                                    x.setDebit(l.debit());
                                    x.setCredit(l.credit());
                                    x.setDescription(l.description());
                                    return x;
                                })
                        .toList());
        return f;
    }

    @Transactional
    public long save(Long id, @jakarta.validation.Valid JournalForm f) {
        var actor = access.require("accounting.write");
        String hash = fingerprint(f);
        if (id == null) {
            db.queryForList(
                    "select pg_advisory_xact_lock(hashtextextended(?,0))",
                    "FCMS_GL/" + actor.id() + "/" + f.getRequestKey());
            var old =
                    db.queryForList(
                            "select id,payload_hash from gl_journals where maker_id=? and"
                                    + " request_key=?",
                            actor.id(),
                            f.getRequestKey());
            if (!old.isEmpty()) {
                if (!hash.equals(old.getFirst().get("payload_hash")))
                    throw new IllegalArgumentException("error.conflict");
                return ((Number) old.getFirst().get("id")).longValue();
            }
        } else {
            lock(id);
            var old = find(id);
            if (old.makerId() != actor.id()
                    || !Set.of("DRAFT", "REJECTED").contains(old.state())
                    || !old.kind().equals(f.getKind())
                    || old.sourceLinked()) throw new IllegalArgumentException("error.invalid");
        }
        long period = period(f.getDate());
        var ids = new HashSet<Long>();
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (var l : f.getLines()) {
            if (l.getDebit().signum() < 0
                    || l.getCredit().signum() < 0
                    || ((l.getDebit().signum() > 0) == (l.getCredit().signum() > 0)))
                throw new IllegalArgumentException("accounting.error.lines");
            ids.add(l.getAccountId());
            debit = debit.add(l.getDebit());
            credit = credit.add(l.getCredit());
        }
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0)
            throw new IllegalArgumentException("accounting.error.balance");
        var accounts =
                db.query(
                        "select id,code,name from gl_accounts where active and depth=4 and id=any"
                                + " (?) order by id for share",
                        (r, n) -> new PostingAccount(r.getLong(1), r.getString(2), r.getString(3)),
                        (Object) ids.toArray(Long[]::new));
        if (accounts.size() != ids.size())
            throw new IllegalArgumentException("accounting.error.accounts");
        var byId = new HashMap<Long, PostingAccount>();
        accounts.forEach(a -> byId.put(a.id(), a));
        if (id == null)
            id =
                    db.queryForObject(
                            "insert into"
                                + " gl_journals(kind,reference,description,evidence_reference,effective_date,period_id,cash_flow,state,maker_id,request_key,payload_hash)"
                                + " values (?,?,?,?,?,?,?,'DRAFT',?,?,?) returning id",
                            Long.class,
                            f.getKind(),
                            f.getReference().trim(),
                            f.getDescription().trim(),
                            f.getEvidenceReference().trim(),
                            f.getDate(),
                            period,
                            f.getCashFlow(),
                            actor.id(),
                            f.getRequestKey(),
                            hash);
        else {
            if (db.update(
                            "update gl_journals set"
                                + " reference=?,description=?,evidence_reference=?,effective_date=?,period_id=?,cash_flow=?,payload_hash=?,state='DRAFT',checker_id=null,review_comment='',version=version+1"
                                + " where id=? and version=?",
                            f.getReference().trim(),
                            f.getDescription().trim(),
                            f.getEvidenceReference().trim(),
                            f.getDate(),
                            period,
                            f.getCashFlow(),
                            hash,
                            id,
                            f.getVersion())
                    != 1) throw new OptimisticLockingFailureException("stale");
            db.update("delete from gl_lines where journal_id=?", id);
        }
        final long journalId = id;
        var numbered = new ArrayList<Numbered>();
        for (int n = 0; n < f.getLines().size(); n++)
            numbered.add(new Numbered(n + 1, f.getLines().get(n)));
        db.batchUpdate(
                "insert into"
                    + " gl_lines(journal_id,line_number,account_id,account_code,account_name,debit,credit,description)"
                    + " values (?,?,?,?,?,?,?,?)",
                numbered,
                50,
                (s, item) -> {
                    var line = item.line();
                    var a = byId.get(line.getAccountId());
                    s.setLong(1, journalId);
                    s.setInt(2, item.number());
                    s.setLong(3, a.id());
                    s.setString(4, a.code());
                    s.setString(5, a.name());
                    s.setBigDecimal(6, line.getDebit());
                    s.setBigDecimal(7, line.getCredit());
                    s.setString(8, line.getDescription());
                });
        audit.record(actor.id(), "accounting.journal.save", "gl_journals", id, f.getKind());
        return id;
    }

    @Transactional
    public void transition(long id, long version, String action, String reason) {
        var actor = access.current();
        lock(id);
        var j = find(id);
        period(j.date());
        String state;
        switch (action) {
            case "submit" -> {
                actor.require("accounting.write");
                if (j.makerId() != actor.id() || !Set.of("DRAFT", "REJECTED").contains(j.state()))
                    throw new IllegalArgumentException("error.invalid");
                state = "SUBMITTED";
            }
            case "cancel" -> {
                actor.require("accounting.write");
                AccountingService.requireReason(reason);
                if (j.makerId() != actor.id()
                        || j.kind().equals("REVERSAL")
                        || !Set.of("DRAFT", "REJECTED").contains(j.state()))
                    throw new IllegalArgumentException("error.invalid");
                state = "CANCELLED";
            }
            case "post", "reject" -> {
                actor.require("accounting.review");
                AccountingService.requireReason(reason);
                if (j.makerId() == actor.id()
                        || !j.state().equals("SUBMITTED")
                        || j.date().isAfter(LocalDate.now()))
                    throw new IllegalArgumentException("error.invalid");
                state = action.equals("post") ? "POSTED" : "REJECTED";
            }
            default -> throw new IllegalArgumentException("error.invalid");
        }
        if (Set.of("POSTED", "REJECTED").contains(state)) bindings.verifyReview(id, actor.id());
        if (db.update(
                        "update gl_journals set"
                            + " state=?,checker_id=?,review_comment=?,posted_at=case when"
                            + " ?='POSTED' then current_timestamp else null end,version=version+1"
                            + " where id=? and version=?",
                        state,
                        Set.of("POSTED", "REJECTED").contains(state) ? actor.id() : null,
                        reason == null ? "" : reason,
                        state,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(actor.id(), "accounting.journal." + action, "gl_journals", id, state);
    }

    @Transactional
    public long reverse(long id, UUID request, LocalDate date, String reason) {
        var actor = access.require("accounting.reverse");
        AccountingService.requireReason(reason);
        if (request == null || date == null || date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("error.invalid");
        lock(id);
        var original = find(id);
        if (original.sourceLinked()) throw new IllegalArgumentException("gl.source.required");
        return createReversal(
                original, actor.id(), request, date, reason, "Original journal " + id);
    }

    @Transactional
    public long reverseSource(
            long originalId, String kind, long sourceId, UUID request, String evidence) {
        var actor = access.require("accounting.reverse");
        actor.require("accounting.write");
        SourceBindingService.requireEvidence(evidence);
        if (request == null) throw new IllegalArgumentException("error.invalid");
        var source = bindings.reversal(originalId, kind, sourceId);
        lock(originalId);
        var original = find(originalId);
        long reversal =
                createReversal(
                        original,
                        actor.id(),
                        request,
                        source.date(),
                        source.description(),
                        evidence.trim());
        var existing = bindings.forJournal(reversal);
        if (existing.isPresent()) {
            if (!existing.get().kind().equals(kind) || existing.get().sourceId() != sourceId)
                throw new IllegalArgumentException("error.conflict");
        } else
            bindings.bind(
                    kind,
                    sourceId,
                    reversal,
                    bindings.forJournal(originalId).orElseThrow().mappingId(),
                    evidence);
        return reversal;
    }

    private long createReversal(
            Journal original,
            long actor,
            UUID request,
            LocalDate date,
            String reason,
            String evidence) {
        if (!original.state().equals("POSTED")
                || Set.of("OPENING", "REVERSAL").contains(original.kind())
                || original.makerId() == actor
                || date.isBefore(original.date())
                || date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("error.invalid");
        if (original.reversedBy() != null) return original.reversedBy();
        long period = period(date);
        long reversal =
                db.queryForObject(
                        "insert into"
                            + " gl_journals(kind,reference,description,evidence_reference,effective_date,period_id,cash_flow,state,maker_id,request_key,payload_hash,reverses_id)"
                            + " values ('REVERSAL',?,?,?,?,?,?,'DRAFT',?,?,?,?) returning id",
                        Long.class,
                        "REV-" + original.id(),
                        reason,
                        evidence,
                        date,
                        period,
                        original.cashFlow(),
                        actor,
                        request,
                        "reversal-" + original.id(),
                        original.id());
        db.update(
                "insert into"
                    + " gl_lines(journal_id,line_number,account_id,account_code,account_name,debit,credit,description)"
                    + " select"
                    + " ?,line_number,account_id,account_code,account_name,credit,debit,description"
                    + " from gl_lines where journal_id=? order by line_number",
                reversal,
                original.id());
        audit.record(
                actor,
                "accounting.journal.reversal.request",
                "gl_journals",
                original.id(),
                "Reversal " + reversal);
        return reversal;
    }

    private void lock(long id) {
        if (db.queryForList("select id from gl_journals where id=? for update", Long.class, id)
                .isEmpty()) throw new IllegalArgumentException("error.missing");
    }

    private long period(LocalDate date) {
        var ids =
                db.queryForList(
                        "select id from gl_periods where state='OPEN' and ? between starts_on and"
                                + " ends_on for share",
                        Long.class,
                        date);
        if (ids.size() != 1) throw new IllegalArgumentException("accounting.error.period");
        return ids.getFirst();
    }

    private static String fingerprint(JournalForm f) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                out.writeUTF(f.getKind());
                out.writeUTF(f.getReference().trim());
                out.writeUTF(f.getDescription().trim());
                out.writeUTF(f.getEvidenceReference().trim());
                out.writeUTF(f.getDate().toString());
                out.writeUTF(f.getCashFlow());
                for (var l : f.getLines()) {
                    out.writeLong(l.getAccountId());
                    out.writeUTF(l.getDebit().setScale(2).toPlainString());
                    out.writeUTF(l.getCredit().setScale(2).toPlainString());
                    out.writeUTF(l.getDescription());
                }
            }
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record PostingAccount(long id, String code, String name) {}

    private record Numbered(int number, JournalLineForm line) {}
}
