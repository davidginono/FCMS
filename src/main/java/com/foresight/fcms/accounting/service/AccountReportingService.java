package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.AccountReporting.*;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Service
@Validated
public class AccountReportingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AccountingService accounts;
    private final AuditService audit;

    public AccountReportingService(
            JdbcTemplate db, AccessService access, AccountingService accounts, AuditService audit) {
        this.db = db;
        this.access = access;
        this.accounts = accounts;
        this.audit = audit;
    }

    private static final String VERSIONS =
            """
            select v.*,m.first_name||' '||m.last_name maker_name,
             coalesce(c.first_name||' '||c.last_name,'') checker_name,
             coalesce(b.section,'') before_section,b.note_no before_note,coalesce(b.note_text,'') before_text
            from gl_account_reporting v join users m on m.id=v.maker_id
            left join users c on c.id=v.checker_id left join gl_account_reporting b on b.id=v.base_id
            """;

    private static final String VISIBLE =
            "(v.state in ('APPROVED','RETIRED') or v.maker_id=? or ?)";

    private Version row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        var reviewed = r.getTimestamp("reviewed_at");
        var retired = r.getTimestamp("retired_at");
        return new Version(
                r.getLong("id"),
                r.getLong("account_id"),
                r.getLong("account_version"),
                r.getObject("base_id", Long.class),
                new Settings(
                        r.getString("section"),
                        r.getObject("note_no", Integer.class),
                        r.getString("note_text")),
                new Settings(
                        r.getString("before_section"),
                        r.getObject("before_note", Integer.class),
                        r.getString("before_text")),
                r.getString("reason"),
                r.getString("state"),
                r.getLong("maker_id"),
                r.getString("maker_name"),
                r.getObject("checker_id", Long.class),
                r.getString("checker_name"),
                r.getTimestamp("created_at").toInstant(),
                reviewed == null ? null : reviewed.toInstant(),
                r.getString("review_reason"),
                r.getLong("version"),
                r.getObject("replaced_by", Long.class),
                retired == null ? null : retired.toInstant());
    }

    private Version revision(long account, long id, AccessService.Actor actor) {
        return db
                .query(
                        VERSIONS + " where v.account_id=? and v.id=? and " + VISIBLE,
                        this::row,
                        account,
                        id,
                        actor.id(),
                        actor.has("accounting.review"))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new org.springframework.security.access.AccessDeniedException(
                                        "Access denied"));
    }

    private Version current(long account) {
        return db
                .query(
                        VERSIONS + " where v.account_id=? and v.state='APPROVED'",
                        this::row,
                        account)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private boolean hasPending(long account) {
        return Boolean.TRUE.equals(
                db.queryForObject(
                        "select exists(select 1 from gl_account_reporting where account_id=? and"
                            + " state='PENDING')",
                        Boolean.class,
                        account));
    }

    private Version pending(long account, AccessService.Actor actor) {
        return db
                .query(
                        VERSIONS + " where v.account_id=? and v.state='PENDING' and " + VISIBLE,
                        this::row,
                        account,
                        actor.id(),
                        actor.has("accounting.review"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean sectionEditable(AccountingViews.Account a) {
        return a.depth() == 2 && Set.of("ASSET", "LIABILITY").contains(a.classification());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Workspace workspace(long account, RegisterQuery q) {
        var actor = access.require("accounting.read");
        var a = accounts.account(account);
        q.validate();
        if (!Set.of("", "PENDING", "APPROVED", "REJECTED", "CANCELLED", "RETIRED")
                .contains(q.getStatus())) throw new IllegalArgumentException("error.invalid");
        var current = current(account);
        var pending = pending(account, actor);
        boolean pendingExists =
                (actor.has("accounting.setup") || actor.has("accounting.review"))
                        && hasPending(account);
        var history =
                db.query(
                        VERSIONS
                                + " where v.account_id=? and "
                                + VISIBLE
                                + " and (?='' or v.state=?) and"
                                + " lower(v.id::text||' '||v.section||'"
                                + " '||coalesce(v.note_no::text,'')||' '||m.first_name||'"
                                + " '||m.last_name||' '||v.reason) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "v.id",
                                                "state",
                                                "v.state",
                                                "maker",
                                                "maker_name",
                                                "created",
                                                "v.created_at",
                                                "reviewed",
                                                "v.reviewed_at"))
                                + " limit ? offset ?",
                        this::row,
                        account,
                        actor.id(),
                        actor.has("accounting.review"),
                        q.getStatus(),
                        q.getStatus(),
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset());
        return new Workspace(
                a,
                current,
                pending,
                RegisterPage.of(history, q),
                actor.has("accounting.setup") && a.active() && !pendingExists,
                sectionEditable(a),
                pendingExists);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Setup setup(long account, ReportingForm retained) {
        access.require("accounting.setup");
        var a = accounts.account(account);
        var f = retained;
        if (f == null) {
            if (!a.active()) throw new IllegalArgumentException("gl.reporting.inactive");
            if (hasPending(account)) throw new IllegalArgumentException("gl.reporting.pending");
            var current = current(account);
            f = new ReportingForm();
            f.setRequestKey(UUID.randomUUID());
            f.setAccountVersion(a.version());
            if (current != null) {
                f.setBaseId(current.id());
                f.setSection(current.proposed().section());
                f.setNoteNumber(current.proposed().noteNumber());
                f.setNoteText(current.proposed().noteText());
            }
        }
        if (retained == null && sectionEditable(a) && f.getSection().isEmpty())
            f.setSection("UNCLASSIFIED");
        return new Setup(a, f, sectionEditable(a));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Decision details(long account, long id) {
        var actor = access.require("accounting.read");
        var a = accounts.account(account);
        var v = revision(account, id, actor);
        var actions = new LinkedHashSet<String>();
        if (v.state().equals("PENDING")) {
            if (v.makerId() == actor.id() && actor.has("accounting.setup")) actions.add("cancel");
            if (v.makerId() != actor.id() && actor.has("accounting.review")) {
                actions.add("reject");
                var current = current(account);
                if (a.active()
                        && a.version() == v.accountVersion()
                        && Objects.equals(v.baseId(), current == null ? null : current.id()))
                    actions.add("approve");
            }
        }
        return new Decision(a, v, actions);
    }

    @Transactional
    public long propose(long account, @jakarta.validation.Valid ReportingForm f) {
        var actor = access.require("accounting.setup");
        lockAccount(account);
        var a = accounts.account(account);
        validate(a, f);
        String fingerprint = fingerprint(account, f);
        var duplicate =
                db.queryForList(
                        "select id,payload_hash from gl_account_reporting where maker_id=? and"
                                + " request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!duplicate.isEmpty()) {
            if (!fingerprint.equals(duplicate.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) duplicate.getFirst().get("id")).longValue();
        }
        if (!a.active()) throw new IllegalArgumentException("gl.reporting.inactive");
        if (a.version() != f.getAccountVersion())
            throw new OptimisticLockingFailureException("stale");
        if (hasPending(account)) throw new IllegalArgumentException("gl.reporting.pending");
        var current = current(account);
        if (!Objects.equals(f.getBaseId(), current == null ? null : current.id()))
            throw new OptimisticLockingFailureException("stale");
        long id =
                db.queryForObject(
                        """
                        insert into gl_account_reporting(account_id,account_version,base_id,section,note_no,note_text,reason,maker_id,request_key,payload_hash)
                        values (?,?,?,?,?,?,?,?,?,?) returning id
                        """,
                        Long.class,
                        account,
                        f.getAccountVersion(),
                        f.getBaseId(),
                        f.getSection(),
                        f.getNoteNumber(),
                        f.getNoteText().strip(),
                        f.getReason().strip(),
                        actor.id(),
                        f.getRequestKey(),
                        fingerprint);
        audit.record(
                actor.id(),
                "accounting.reporting.propose",
                "gl_account_reporting",
                id,
                "Account "
                        + account
                        + "; base "
                        + f.getBaseId()
                        + "; section "
                        + (current == null ? "" : current.proposed().section())
                        + " -> "
                        + f.getSection()
                        + "; note "
                        + (current == null ? null : current.proposed().noteNumber())
                        + " -> "
                        + f.getNoteNumber());
        return id;
    }

    @Transactional
    public void decide(long account, long id, @jakarta.validation.Valid ReportingDecisionForm f) {
        if (f.getAction() == null || !Set.of("approve", "reject", "cancel").contains(f.getAction()))
            throw new IllegalArgumentException("error.invalid");
        var actor =
                access.require(
                        f.getAction().equals("cancel") ? "accounting.setup" : "accounting.review");
        AccountingService.requireReason(f.getReason());
        lockAccount(account);
        var a = accounts.account(account);
        db.queryForList(
                "select id from gl_account_reporting where account_id=? and id=? for update",
                Long.class,
                account,
                id);
        var v = revision(account, id, actor);
        if (v.version() != f.getVersion()) throw new OptimisticLockingFailureException("stale");
        if (!v.state().equals("PENDING")
                || (f.getAction().equals("cancel")
                        ? v.makerId() != actor.id()
                        : v.makerId() == actor.id()))
            throw new IllegalArgumentException("gl.reporting.independent");
        String next =
                switch (f.getAction()) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    default -> "CANCELLED";
                };
        if (next.equals("APPROVED")) {
            if (!a.active()) throw new IllegalArgumentException("gl.reporting.inactive");
            if (a.version() != v.accountVersion())
                throw new OptimisticLockingFailureException("stale");
            var current = current(account);
            if (!Objects.equals(v.baseId(), current == null ? null : current.id()))
                throw new OptimisticLockingFailureException("stale");
            if (current != null) {
                if (db.update(
                                "update gl_account_reporting set"
                                    + " state='RETIRED',retired_by=?,retired_at=current_timestamp,replaced_by=?,version=version+1"
                                    + " where account_id=? and id=? and version=? and"
                                    + " state='APPROVED'",
                                actor.id(),
                                id,
                                account,
                                current.id(),
                                current.version())
                        != 1) throw new OptimisticLockingFailureException("stale");
                audit.record(
                        actor.id(),
                        "accounting.reporting.retire",
                        "gl_account_reporting",
                        current.id(),
                        "Replacement " + id);
            }
        }
        if (db.update(
                        "update gl_account_reporting set"
                            + " state=?,checker_id=?,review_reason=?,reviewed_at=current_timestamp,version=version+1"
                            + " where account_id=? and id=? and version=?",
                        next,
                        actor.id(),
                        f.getReason().strip(),
                        account,
                        id,
                        f.getVersion())
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "accounting.reporting." + f.getAction(),
                "gl_account_reporting",
                id,
                f.getReason().strip());
    }

    private void lockAccount(long account) {
        if (account < 1) throw new IllegalArgumentException("error.invalid");
        if (db.queryForList("select id from gl_accounts where id=? for update", Long.class, account)
                .isEmpty()) throw new IllegalArgumentException("error.missing");
    }

    private static void validate(AccountingViews.Account a, ReportingForm f) {
        var section = f.getSection();
        if (!section.isEmpty()
                && (!sectionEditable(a)
                        || !(a.classification().equals("ASSET")
                                        ? Set.of(
                                                "CURRENT_ASSETS",
                                                "NON_CURRENT_ASSETS",
                                                "UNCLASSIFIED")
                                        : Set.of(
                                                "CURRENT_LIABILITIES",
                                                "NON_CURRENT_LIABILITIES",
                                                "UNCLASSIFIED"))
                                .contains(section)))
            throw new IllegalArgumentException("gl.reporting.sectionInvalid");
        if ((f.getNoteNumber() == null || f.getNoteNumber() == 0) && !f.getNoteText().isBlank())
            throw new IllegalArgumentException("gl.reporting.noteRequired");
        if (f.getNoteText()
                .chars()
                .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r'))
            throw new IllegalArgumentException("gl.reporting.textInvalid");
    }

    static String fingerprint(long account, ReportingForm f) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                out.writeLong(account);
                out.writeLong(f.getAccountVersion());
                out.writeLong(f.getBaseId() == null ? 0 : f.getBaseId());
                out.writeUTF(f.getSection());
                out.writeInt(f.getNoteNumber() == null ? -1 : f.getNoteNumber());
                out.writeUTF(f.getNoteText().strip());
                out.writeUTF(f.getReason().strip());
            }
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
