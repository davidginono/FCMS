package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Service
@Validated
public class ActivityTransactionService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final PostingMappingService templates;

    public ActivityTransactionService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            PostingMappingService templates) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.templates = templates;
    }

    public record Activity(
            long id,
            String code,
            String name,
            String description,
            boolean active,
            long version,
            long transactions) {}

    public record Transaction(
            long id,
            long activityId,
            String activityCode,
            String activityName,
            boolean activityActive,
            String code,
            String name,
            String description,
            String event,
            boolean active,
            long version,
            String templateState) {}

    public record Detail(
            Transaction transaction,
            PostingMapping effective,
            PostingMapping proposal,
            RegisterPage<PostingMapping> pending) {}

    private static final String ACTIVITIES =
            "select a.*,(select count(*) from gl_transactions t where t.activity_id=a.id)"
                    + " transactions from gl_activities a";
    private static final String TRANSACTIONS =
            "select t.*,a.code activity_code,a.name activity_name,a.active"
                + " activity_active,coalesce((select m.state from gl_posting_mappings m where"
                + " m.transaction_id=t.id order by (m.state='PENDING') desc,(m.state='APPROVED')"
                + " desc,m.id desc limit 1),'UNCONFIGURED') template_state from gl_transactions t"
                + " join gl_activities a on a.id=t.activity_id";

    private Activity activityRow(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Activity(
                r.getLong("id"),
                r.getString("code"),
                r.getString("name"),
                r.getString("description"),
                r.getBoolean("active"),
                r.getLong("version"),
                r.getLong("transactions"));
    }

    private Transaction transactionRow(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Transaction(
                r.getLong("id"),
                r.getLong("activity_id"),
                r.getString("activity_code"),
                r.getString("activity_name"),
                r.getBoolean("activity_active"),
                r.getString("code"),
                r.getString("name"),
                r.getString("description"),
                r.getString("source_event"),
                r.getBoolean("active"),
                r.getLong("version"),
                r.getString("template_state"));
    }

    private void query(RegisterQuery q) {
        q.validate();
        if (!Set.of("", "ACTIVE", "INACTIVE").contains(q.getStatus()))
            throw new IllegalArgumentException("error.invalid");
    }

    @Transactional(readOnly = true)
    public RegisterPage<Activity> activities(RegisterQuery q) {
        access.require("accounting.read");
        query(q);
        return RegisterPage.of(
                db.query(
                        ACTIVITIES
                                + " where lower(a.code||' '||a.name) like ? escape '!' and (?='' or"
                                + " a.active=(?='ACTIVE')) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "a.id",
                                                "code",
                                                "a.code",
                                                "name",
                                                "a.name",
                                                "status",
                                                "a.active",
                                                "transactions",
                                                "transactions"))
                                + " limit ? offset ?",
                        this::activityRow,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public Activity activity(long id) {
        access.require("accounting.read");
        return db.query(ACTIVITIES + " where a.id=?", this::activityRow, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public ChoicePage<Choice> activityChoices(String search, int page) {
        access.require("accounting.read");
        if (search == null || search.length() > 100 || page < 0 || page > 1000)
            throw new IllegalArgumentException("error.invalid");
        var q = new RegisterQuery();
        q.setSearch(search);
        return ChoicePage.from(
                db.query(
                        "select id,code||' '||name from gl_activities where active and"
                            + " lower(code||' '||name) like ? escape '!' order by code,id limit 26"
                            + " offset ?",
                        (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                        q.pattern(),
                        page * 25),
                page);
    }

    public record Choice(long id, String name) {}

    @Transactional
    public long createActivity(@jakarta.validation.Valid ActivityForm f) {
        var actor = access.require("accounting.setup");
        String code,
                name = f.getName().trim(),
                description = text(f.getDescription());
        String hash = signature(name, description);
        lockRequest("ACTIVITY", actor.id(), f.getRequestKey());
        var existing =
                db.queryForList(
                        "select id,payload_hash from gl_activities where created_by=? and"
                                + " request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) existing.getFirst().get("id")).longValue();
        }
        code = nextCode(false);
        long id =
                db.queryForObject(
                        "insert into"
                            + " gl_activities(code,name,description,created_by,request_key,payload_hash)"
                            + " values (?,?,?,?,?,?) returning id",
                        Long.class,
                        code,
                        name,
                        description,
                        actor.id(),
                        f.getRequestKey(),
                        hash);
        audit.record(actor.id(), "accounting.activity.create", "gl_activities", id, code);
        return id;
    }

    @Transactional
    public void editActivity(long id, @jakarta.validation.Valid ActivityForm f) {
        var actor = access.require("accounting.setup");
        db.queryForList("select id from gl_activities where id=? for update", Long.class, id);
        var old = activity(id);
        if (!old.code().equals(code(f.getCode())))
            throw new IllegalArgumentException("error.invalid");
        if (db.update(
                        "update gl_activities set name=?,description=?,version=version+1 where id=?"
                                + " and version=?",
                        f.getName().trim(),
                        text(f.getDescription()),
                        id,
                        f.getVersion())
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "accounting.activity.edit",
                "gl_activities",
                id,
                "Name: " + old.name() + " -> " + f.getName().trim());
    }

    @Transactional
    public void activityState(long id, long version, boolean active, String reason) {
        var actor = access.require("accounting.setup");
        AccountingService.requireReason(reason);
        db.queryForList("select id from gl_activities where id=? for update",Long.class,id);
        var old = activity(id);
        if (!active && Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from gl_transactions where activity_id=? and active)",Boolean.class,id)))
            throw new IllegalArgumentException("voucher.error.activeTransactions");
        if (db.update(
                        "update gl_activities set active=?,version=version+1 where id=? and"
                                + " version=?",
                        active,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "accounting.activity.state",
                "gl_activities",
                id,
                old.active() + " -> " + active + "; " + reason.trim());
    }

    @Transactional(readOnly = true)
    public RegisterPage<Transaction> transactions(RegisterQuery q, Long activity) {
        access.require("accounting.read");
        query(q);
        if (activity != null) {
            activity(activity);
        }
        return RegisterPage.of(
                db.query(
                        TRANSACTIONS
                                + " where (?::bigint is null or t.activity_id=?::bigint) and"
                                + " lower(t.code||' '||t.name||' '||a.code) like ? escape '!' and"
                                + " (?='' or t.active=(?='ACTIVE')) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "t.id",
                                                "code",
                                                "t.code",
                                                "name",
                                                "t.name",
                                                "activity",
                                                "a.code",
                                                "event",
                                                "t.source_event",
                                                "status",
                                                "t.active",
                                                "template",
                                                "template_state"))
                                + " limit ? offset ?",
                        this::transactionRow,
                        activity,
                        activity,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public Transaction transaction(long id) {
        access.require("accounting.read");
        return db.query(TRANSACTIONS + " where t.id=?", this::transactionRow, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    private Transaction lockTransaction(long id) {
        var initial = transaction(id);
        db.queryForList(
                "select id from gl_activities where id=? for share",
                Long.class,
                initial.activityId());
        db.queryForList("select id from gl_transactions where id=? for update", Long.class, id);
        return transaction(id);
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, Long review, RegisterQuery q) {
        var transaction = transaction(id);
        PostingMapping proposal;
        if (review != null) {
            proposal = templates.find(review);
            if (proposal.transactionId() != id) throw new IllegalArgumentException("error.missing");
        } else proposal = templates.latest(id).orElse(null);
        return new Detail(
                transaction,
                templates.currentForTransaction(id).orElse(null),
                proposal,
                templates.pending(id, q));
    }

    @Transactional
    public long createTransaction(@jakarta.validation.Valid TransactionConfigForm f) {
        var actor = access.require("accounting.setup");
        f.setEvent("MANUAL_JOURNAL");
        if(f.getRules().size()!=1 || !"TOTAL".equals(f.getRules().getFirst().getComponent()))
            throw new com.foresight.fcms.shared.validation.FieldValidationException("voucher.error.components","rules");
        String code, name=f.getName().trim(), description=text(f.getDescription());
        String hash =
                signature(
                        f.getActivityId(),
                        name,
                        description,
                        f.getEvent(),
                        PostingMappingService.fingerprint(f),
                        f.getVoucherKind(),
                        f.getCashFlow(),
                        text(f.getPolicyReference()));
        lockRequest("TRANSACTION", actor.id(), f.getRequestKey());
        var existing =
                db.queryForList(
                        "select id,payload_hash from gl_transactions where created_by=? and"
                                + " request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) existing.getFirst().get("id")).longValue();
        }
        db.queryForList(
                "select id from gl_activities where id=? for share", Long.class, f.getActivityId());
        if (!activity(f.getActivityId()).active())
            throw new IllegalArgumentException("config.error.inactive");
        code = nextCode(true);
        long id =
                db.queryForObject(
                        "insert into"
                            + " gl_transactions(activity_id,code,name,description,source_event,created_by,request_key,payload_hash)"
                            + " values (?,?,?,?,?,?,?,?) returning id",
                        Long.class,
                        f.getActivityId(),
                        code,
                        name,
                        description,
                        f.getEvent(),
                        actor.id(),
                        f.getRequestKey(),
                        hash);
        f.setTransactionId(id);
        templates.create(f);
        audit.record(actor.id(), "accounting.transaction.create", "gl_transactions", id, code);
        return id;
    }

    @Transactional
    public long saveTemplate(long id, @jakarta.validation.Valid TransactionTemplateForm f) {
        var actor = access.require("accounting.setup");
        var transaction = lockTransaction(id);
        f.setTransactionId(id);
        f.setEvent(transaction.event());
        var retry =
                db.queryForList(
                        "select id,payload_hash,transaction_id from gl_posting_mappings where"
                                + " maker_id=? and request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!retry.isEmpty()) {
            if (((Number) retry.getFirst().get("transaction_id")).longValue() != id
                    || !PostingMappingService.fingerprint(f)
                            .equals(retry.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) retry.getFirst().get("id")).longValue();
        }
        if (transaction.version() != f.getVersion())
            throw new OptimisticLockingFailureException("stale");
        if (!transaction.active() || !transaction.activityActive())
            throw new IllegalArgumentException("config.error.inactive");
        var priorTemplate=templates.latest(id);
        var components=priorTemplate.map(p->templates.rules(p.id()).stream().map(PostingMappingService.Rule::component).collect(java.util.stream.Collectors.toSet())).orElse(Set.of("TOTAL"));
        var submitted=f.getRules().stream().map(PostingMappingForm.RuleForm::getComponent).collect(java.util.stream.Collectors.toSet());
        if(f.getRules().size()!=components.size() || !submitted.equals(components))
            throw new com.foresight.fcms.shared.validation.FieldValidationException("voucher.error.components","rules");
        var pending = templates.pending(id, new RegisterQuery());
        if (pending.hasNext() || pending.rows().stream().anyMatch(p -> p.makerId() != actor.id()))
            throw new IllegalArgumentException("config.error.pending");
        for (var proposal : pending.rows())
            templates.review(
                    proposal.id(),
                    proposal.version(),
                    "cancel",
                    "Replaced by an edited transaction template");
        long mapping = templates.create(f);
        audit.record(
                actor.id(),
                "accounting.transaction.template",
                "gl_transactions",
                id,
                "Posting template submitted for independent review");
        return mapping;
    }

    @Transactional
    public void reviewTemplate(
            long id,
            long template,
            long version,
            long transactionVersion,
            String action,
            String reason) {
        access.require(action.equals("cancel") ? "accounting.setup" : "accounting.review");
        var transaction = lockTransaction(id);
        if (transaction.version() != transactionVersion)
            throw new OptimisticLockingFailureException("stale");
        if (action.equals("approve") && (!transaction.active() || !transaction.activityActive()))
            throw new IllegalArgumentException("config.error.inactive");
        var proposal = templates.find(template);
        if (proposal.transactionId() != id) throw new IllegalArgumentException("error.missing");
        templates.review(template, version, action, reason);
    }

    @Transactional
    public void transactionState(long id, long version, boolean active, String reason) {
        var actor = access.require("accounting.setup");
        AccountingService.requireReason(reason);
        var old = lockTransaction(id);
        if (active && !old.activityActive())
            throw new IllegalArgumentException("config.error.inactive");
        if (db.update(
                        "update gl_transactions set active=?,version=version+1 where id=? and"
                                + " version=?",
                        active,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "accounting.transaction.state",
                "gl_transactions",
                id,
                old.active() + " -> " + active + "; " + reason.trim());
    }

    private void lockRequest(String kind, long actor, UUID request) {
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "FCMS_CONFIG/" + kind + "/" + actor + "/" + request);
    }

    private String nextCode(boolean transaction) {
        String prefix=transaction?"TXN-":"ACT-", table=transaction?"gl_transactions":"gl_activities";
        String sequence=transaction?"gl_transaction_code_sequence":"gl_activity_code_sequence";
        String value;
        do {
            long number=db.queryForObject("select nextval(?::regclass)",Long.class,sequence);
            value=prefix+String.format(Locale.ROOT,"%06d",number);
        } while(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from "+table+" where code=?)",Boolean.class,value)));
        return value;
    }

    private static String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    private static String signature(Object... values) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                for (var value : values) out.writeUTF(String.valueOf(value));
            }
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Configuration fingerprint unavailable", e);
        }
    }
}
