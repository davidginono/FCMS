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
public class PostingMappingService {
    public static final List<String> EVENTS =
            List.of(
                    "MANUAL_JOURNAL",
                    "INCOME_IN",
                    "PETTY_IN",
                    "PETTY_OUT",
                    "CLOUD_IN",
                    "CLOUD_OUT",
                    "BULK_IN",
                    "BULK_OUT",
                    "EXPENSE_PAYMENT",
                    "INCOME_OPENING_REVERSAL",
                    "PETTY_OPENING_REVERSAL",
                    "CLOUD_OPENING_REVERSAL",
                    "BULK_OPENING_REVERSAL",
                    "EXPENSE_OPENING_REVERSAL");
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;

    public PostingMappingService(JdbcTemplate db, AccessService access, AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    private static final String SELECT =
            """
            select m.*,t.code transaction_code,t.name transaction_name,d.code||' '||d.name debit_account,c.code||' '||c.name credit_account,
             u.first_name||' '||u.last_name maker,coalesce(v.first_name||' '||v.last_name,'') checker
            from gl_posting_mappings m join gl_transactions t on t.id=m.transaction_id join gl_accounts d on d.id=m.debit_account_id
            join gl_accounts c on c.id=m.credit_account_id join iam_staff_directory u on u.id=m.maker_id
            left join iam_staff_directory v on v.id=m.checker_id
            """;

    private PostingMapping row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new PostingMapping(
                r.getLong("id"),
                r.getString("source_event"),
                r.getLong("debit_account_id"),
                r.getString("debit_account"),
                r.getLong("credit_account_id"),
                r.getString("credit_account"),
                r.getString("voucher_kind"),
                r.getString("cash_flow"),
                r.getString("policy_reference"),
                r.getString("state"),
                r.getLong("maker_id"),
                r.getString("maker"),
                r.getObject("checker_id", Long.class),
                r.getString("checker"),
                r.getString("review_reason"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("reviewed_at") == null
                        ? null
                        : r.getTimestamp("reviewed_at").toInstant(),
                r.getObject("retired_by", Long.class),
                r.getTimestamp("retired_at") == null
                        ? null
                        : r.getTimestamp("retired_at").toInstant(),
                r.getLong("version"),
                r.getLong("transaction_id"),
                r.getString("transaction_code"),
                r.getString("transaction_name"));
    }

    @Transactional(readOnly = true)
    public PostingMapping find(long id) {
        access.require("accounting.read");
        return db.query(SELECT + " where m.id=?", this::row, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public Optional<PostingMapping> current(String event) {
        access.require("accounting.read");
        requireEvent(event);
        var rows =
                db.query(
                        SELECT
                                + " join gl_activities a on a.id=t.activity_id where"
                                + " m.source_event=? and m.state='APPROVED' and t.active and"
                                + " a.active order by m.id limit 2",
                        this::row,
                        event);
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<PostingMapping> currentForTransaction(long transaction) {
        access.require("accounting.read");
        return db
                .query(
                        SELECT + " where m.transaction_id=? and m.state='APPROVED'",
                        this::row,
                        transaction)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<PostingMapping> latest(long transaction) {
        access.require("accounting.read");
        return db
                .query(
                        SELECT
                                + " where m.transaction_id=? order by (m.state='PENDING')"
                                + " desc,(m.state='APPROVED') desc,m.id desc limit 1",
                        this::row,
                        transaction)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public RegisterPage<PostingMapping> pending(long transaction, RegisterQuery q) {
        access.require("accounting.read");
        q.validate();
        return RegisterPage.of(
                db.query(
                        SELECT
                                + " where m.transaction_id=? and m.state='PENDING' and"
                                + " lower(m.policy_reference||' '||d.code||' '||c.code||'"
                                + " '||u.first_name||' '||u.last_name) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "m.id",
                                                "date",
                                                "m.created_at",
                                                "maker",
                                                "maker",
                                                "debit",
                                                "debit_account",
                                                "credit",
                                                "credit_account"))
                                + " limit ? offset ?",
                        this::row,
                        transaction,
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    private void lockTransaction(long transaction) {
        lockTransaction(transaction, true);
    }

    private void lockTransaction(long transaction, boolean write) {
        var ids =
                db.queryForList(
                        "select a.id from gl_activities a join gl_transactions t on"
                                + " t.activity_id=a.id where t.id=? for share of a",
                        Long.class,
                        transaction);
        if (ids.isEmpty()) throw new IllegalArgumentException("error.missing");
        db.queryForList(
                "select id from gl_transactions where id=? for " + (write ? "update" : "share"),
                Long.class,
                transaction);
    }

    @Transactional(readOnly = true)
    public ChoicePage<TemplateChoice> choices(String event, String search, int page) {
        access.require("accounting.read");
        requireEvent(event);
        if (search == null || search.length() > 100 || page < 0 || page > 1000)
            throw new IllegalArgumentException("error.invalid");
        var q = new RegisterQuery();
        q.setSearch(search);
        return ChoicePage.from(
                db.query(
                        "select m.id,t.code||' '||t.name from gl_posting_mappings m join"
                            + " gl_transactions t on t.id=m.transaction_id join gl_activities a on"
                            + " a.id=t.activity_id where m.source_event=? and m.state='APPROVED'"
                            + " and t.active and a.active and lower(t.code||' '||t.name) like ?"
                            + " escape '!' order by t.code,t.id limit 26 offset ?",
                        (r, n) -> new TemplateChoice(r.getLong(1), r.getString(2)),
                        event,
                        q.pattern(),
                        page * 25),
                page);
    }

    public record TemplateChoice(long id, String name) {}

    @Transactional
    public PostingMapping forPreparation(long id, String event) {
        var initial = find(id);
        lockTransaction(initial.transactionId(), false);
        db.queryForList("select id from gl_posting_mappings where id=? for share", Long.class, id);
        var mapping = find(id);
        if (!mapping.state().equals("APPROVED")
                || !mapping.event().equals(event)
                || !Boolean.TRUE.equals(
                        db.queryForObject(
                                "select t.active and a.active from gl_transactions t join"
                                        + " gl_activities a on a.id=t.activity_id where t.id=?",
                                Boolean.class,
                                mapping.transactionId())))
            throw new IllegalArgumentException("gl.source.mappingChanged");
        return mapping;
    }

    public record Rule(String component, long debitAccountId, String debitAccount, long creditAccountId, String creditAccount) {}
    public record VoucherTemplate(long id,long activityId,String name,String kind,String flow,List<Rule> rules) {}

    @Transactional
    public Map<Long,VoucherTemplate> voucherTemplates(Collection<Long> requested) {
        access.require("accounting.write");
        if(requested.size()>50)throw new IllegalArgumentException("error.invalid");
        if(requested.isEmpty())return Map.of();
        var ids=requested.stream().distinct().sorted().toArray(Long[]::new);
        // Match the catalogue's activity -> transaction -> revision lock order for the entire form.
        db.queryForList("select id from gl_activities where id in (select t.activity_id from gl_transactions t join gl_posting_mappings m on m.transaction_id=t.id where m.id=any(?)) order by id for share",Long.class,(Object)ids);
        db.queryForList("select id from gl_transactions where id in (select transaction_id from gl_posting_mappings where id=any(?)) order by id for share",Long.class,(Object)ids);
        db.queryForList("select id from gl_posting_mappings where id=any(?) order by id for share",Long.class,(Object)ids);
        var rules=new HashMap<Long,List<Rule>>();
        db.query("select r.mapping_id,r.component,r.debit_account_id,d.code||' '||d.name,r.credit_account_id,c.code||' '||c.name from gl_mapping_rules r join gl_accounts d on d.id=r.debit_account_id join gl_accounts c on c.id=r.credit_account_id where r.mapping_id=any(?) order by r.mapping_id,r.component",
                (org.springframework.jdbc.core.RowCallbackHandler)r->rules.computeIfAbsent(r.getLong(1),key->new ArrayList<>()).add(new Rule(r.getString(2),r.getLong(3),r.getString(4),r.getLong(5),r.getString(6))),(Object)ids);
        var result=new LinkedHashMap<Long,VoucherTemplate>();
        db.query("select m.id,t.activity_id,t.name,m.voucher_kind,m.cash_flow from gl_posting_mappings m join gl_transactions t on t.id=m.transaction_id join gl_activities a on a.id=t.activity_id where m.id=any(?) and m.state='APPROVED' and t.active and a.active order by m.id",
                (org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getLong(1),new VoucherTemplate(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),List.copyOf(rules.getOrDefault(r.getLong(1),List.of())))),(Object)ids);
        if(result.size()!=ids.length)throw new IllegalArgumentException("gl.source.mappingChanged");
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<Rule> rules(long mapping) {
        access.require("accounting.read");
        return db.query("select r.component,r.debit_account_id,d.code||' '||d.name,r.credit_account_id,c.code||' '||c.name from gl_mapping_rules r join gl_accounts d on d.id=r.debit_account_id join gl_accounts c on c.id=r.credit_account_id where r.mapping_id=? order by r.component",
                (r,n)->new Rule(r.getString(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5)),mapping);
    }

    private List<PostingMappingForm.RuleForm> validatedRules(PostingMappingForm f) {
        var rules = f.getRules();
        // Retained automatic finance adapters still supply one TOTAL pair.
        if (rules.isEmpty()) {
            var rule = new PostingMappingForm.RuleForm();
            rule.setDebitAccountId(f.getDebitAccountId()); rule.setCreditAccountId(f.getCreditAccountId());
            rules = List.of(rule);
        }
        var components = new HashSet<String>();
        for (int i=0;i<rules.size();i++) {
            var rule=rules.get(i);
            if (!Set.of("TOTAL","PRINCIPAL","INTEREST","FEES","TAX").contains(rule.getComponent()) || !components.add(rule.getComponent()))
                throw new com.foresight.fcms.shared.validation.FieldValidationException("voucher.error.components","rules["+i+"].component");
            try { validatePair(rule.getDebitAccountId(),rule.getCreditAccountId(),f.getVoucherKind(),f.getCashFlow()); }
            catch (IllegalArgumentException e) { throw new com.foresight.fcms.shared.validation.FieldValidationException(e.getMessage(),"rules["+i+"].debitAccountId","rules["+i+"].creditAccountId"); }
        }
        if (rules.size()>5 || (components.contains("TOTAL") && rules.size()!=1) || (!f.getEvent().equals("MANUAL_JOURNAL") && (rules.size()!=1 || !components.contains("TOTAL"))))
            throw new com.foresight.fcms.shared.validation.FieldValidationException("voucher.error.components","rules");
        f.setDebitAccountId(rules.getFirst().getDebitAccountId()); f.setCreditAccountId(rules.getFirst().getCreditAccountId());
        return rules;
    }

    @Transactional
    public long create(@jakarta.validation.Valid PostingMappingForm f) {
        var actor = access.require("accounting.setup");
        requireEvent(f.getEvent());
        long transaction =
                f.getTransactionId() == null
                        ? db.queryForObject(
                                "select id from gl_transactions where code=?",
                                Long.class,
                                f.getEvent())
                        : f.getTransactionId();
        lockTransaction(transaction);
        if (!Boolean.TRUE.equals(
                db.queryForObject(
                        "select t.active and a.active and t.source_event=? from gl_transactions t"
                                + " join gl_activities a on a.id=t.activity_id where t.id=?",
                        Boolean.class,
                        f.getEvent(),
                        transaction))) throw new IllegalArgumentException("error.invalid");
        var rules = validatedRules(f);
        String hash = fingerprint(f);
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "FCMS_GL_MAPPING_REQUEST/" + actor.id() + "/" + f.getRequestKey());
        var prior =
                db.queryForList(
                        "select id,payload_hash,transaction_id from gl_posting_mappings where"
                                + " maker_id=? and request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("payload_hash"))
                    || ((Number) prior.getFirst().get("transaction_id")).longValue() != transaction)
                throw new IllegalArgumentException("error.conflict");
            return ((Number) prior.getFirst().get("id")).longValue();
        }
        validatePair(
                f.getDebitAccountId(), f.getCreditAccountId(), f.getVoucherKind(), f.getCashFlow());
        long id =
                db.queryForObject(
                        "insert into"
                            + " gl_posting_mappings(source_event,debit_account_id,credit_account_id,voucher_kind,cash_flow,policy_reference,maker_id,request_key,payload_hash,transaction_id)"
                            + " values (?,?,?,?,?,?,?,?,?,?) returning id",
                        Long.class,
                        f.getEvent(),
                        f.getDebitAccountId(),
                        f.getCreditAccountId(),
                        f.getVoucherKind(),
                        f.getCashFlow(),
                        f.getPolicyReference().trim(),
                        actor.id(),
                        f.getRequestKey(),
                        hash,
                        transaction);
        for (var rule : rules) db.update("insert into gl_mapping_rules(mapping_id,component,debit_account_id,credit_account_id) values (?,?,?,?)",id,rule.getComponent(),rule.getDebitAccountId(),rule.getCreditAccountId());
        db.update("update gl_transactions set version=version+1 where id=?", transaction);
        audit.record(
                actor.id(), "accounting.mapping.create", "gl_posting_mappings", id, f.getEvent());
        return id;
    }

    @Transactional
    public void review(long id, long version, String action, String reason) {
        var actor = access.current();
        actor.require(action.equals("cancel") ? "accounting.setup" : "accounting.review");
        AccountingService.requireReason(reason);
        var initial = find(id);
        lockTransaction(initial.transactionId());
        // Serialize replacement inside one transaction configuration.
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "FCMS_GL_MAPPING_TRANSACTION/" + initial.transactionId());
        db.queryForList("select id from gl_posting_mappings where id=? for update", Long.class, id);
        var mapping = find(id);
        if (mapping.version() != version) throw new OptimisticLockingFailureException("stale");
        if (!mapping.state().equals("PENDING")
                || !Set.of("approve", "reject", "cancel").contains(action)
                || (action.equals("cancel")
                        ? mapping.makerId() != actor.id()
                        : mapping.makerId() == actor.id()))
            throw new IllegalArgumentException("error.invalid");
        String state =
                switch (action) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    default -> "CANCELLED";
                };
        if (state.equals("APPROVED")) {
            for (var rule : rules(id)) validatePair(rule.debitAccountId(),rule.creditAccountId(),mapping.voucherKind(),mapping.cashFlow());
            validatePair(
                    mapping.debitAccountId(),
                    mapping.creditAccountId(),
                    mapping.voucherKind(),
                    mapping.cashFlow());
            var old = currentForTransaction(mapping.transactionId());
            if (old.isPresent()) {
                db.update(
                        "update gl_posting_mappings set"
                            + " state='RETIRED',retired_by=?,retired_at=current_timestamp,version=version+1"
                            + " where id=?",
                        actor.id(),
                        old.get().id());
                audit.record(
                        actor.id(),
                        "accounting.mapping.retire",
                        "gl_posting_mappings",
                        old.get().id(),
                        "Replacement " + id);
            }
        }
        if (db.update(
                        "update gl_posting_mappings set"
                            + " state=?,checker_id=?,review_reason=?,reviewed_at=current_timestamp,version=version+1"
                            + " where id=? and version=?",
                        state,
                        actor.id(),
                        reason.trim(),
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        db.update(
                "update gl_transactions set version=version+1 where id=?", mapping.transactionId());
        audit.record(
                actor.id(),
                "accounting.mapping." + action,
                "gl_posting_mappings",
                id,
                reason.trim());
    }

    private void validatePair(Long debit, Long credit, String kind, String flow) {
        if (debit == null || credit == null || debit.equals(credit))
            throw new IllegalArgumentException("accounting.error.mappingAccounts");
        var accounts =
                db.query(
                        "select id,cash_account from gl_accounts where active and depth=4 and"
                                + " id=any(?) order by id for share",
                        (r, n) -> Map.entry(r.getLong(1), r.getBoolean(2)),
                        (Object) new Long[] {debit, credit});
        if (accounts.size() != 2)
            throw new IllegalArgumentException("accounting.error.mappingAccounts");
        var byId = new HashMap<Long, Boolean>();
        accounts.forEach(a -> byId.put(a.getKey(), a.getValue()));
        int delta = (byId.get(debit) ? 1 : 0) - (byId.get(credit) ? 1 : 0);
        if ((kind.equals("RECEIPT") && delta <= 0) || (kind.equals("PAYMENT") && delta >= 0))
            throw new IllegalArgumentException("accounting.error.mappingDirection");
        if ((delta != 0 && !Set.of("OPERATING", "INVESTING", "FINANCING").contains(flow))
                || (delta == 0
                        && !flow.equals(byId.get(debit) || byId.get(credit) ? "INTERNAL" : "NONE")))
            throw new IllegalArgumentException("accounting.error.mappingFlow");
    }

    private static void requireEvent(String event) {
        if (!EVENTS.contains(event)) throw new IllegalArgumentException("error.invalid");
    }

    static String fingerprint(PostingMappingForm f) {
        if(f.getRules().isEmpty() && (f.getDebitAccountId()==null || f.getCreditAccountId()==null))
            throw new com.foresight.fcms.shared.validation.FieldValidationException("accounting.error.mappingAccounts","rules");
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                out.writeUTF(f.getEvent());
                out.writeLong(f.getRules().isEmpty()?f.getDebitAccountId():f.getRules().getFirst().getDebitAccountId());
                out.writeLong(f.getRules().isEmpty()?f.getCreditAccountId():f.getRules().getFirst().getCreditAccountId());
                out.writeUTF(f.getVoucherKind());
                out.writeUTF(f.getCashFlow());
                out.writeUTF(f.getPolicyReference().trim());
                for (var rule : f.getRules()) { out.writeUTF(rule.getComponent());out.writeLong(rule.getDebitAccountId());out.writeLong(rule.getCreditAccountId()); }
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
