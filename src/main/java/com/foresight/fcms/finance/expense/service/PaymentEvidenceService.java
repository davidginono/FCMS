package com.foresight.fcms.finance.expense.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Validated
public class PaymentEvidenceService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public PaymentEvidenceService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.metrics = metrics;
    }

    private AccessService.Actor reader() {
        var actor = access.require("expense.read");
        if (!actor.has("expense.evidence.write") && !actor.has("expense.evidence.review"))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        return actor;
    }

    private static final String CANDIDATES =
            """
            select e.id,e.user_id owner_id,u.first_name||' '||u.last_name owner,e.date_created,e.payee,e.description,e.amount,e.payment_reference,
            s.name expense_state,coalesce(h.state,'UNVERIFIED') evidence_state,h.id evidence_id
            from expense_legacy_payment_candidates c join expenses e on e.id=c.expense_id
            join iam_staff_directory u on u.id=e.user_id join expense_status s on s.id=e.status_id
            left join lateral(select id,state from expense_payment_evidence where expense_id=e.id order by id desc limit 1) h on true
            """;

    private LegacyPayment candidate(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new LegacyPayment(
                r.getLong("id"),
                r.getLong("owner_id"),
                r.getString("owner"),
                r.getObject("date_created", LocalDate.class),
                r.getString("payee"),
                r.getString("description"),
                r.getBigDecimal("amount"),
                r.getString("payment_reference"),
                r.getString("expense_state"),
                r.getString("evidence_state"),
                r.getObject("evidence_id", Long.class));
    }

    @Transactional(readOnly = true)
    public RegisterPage<LegacyPayment> list(RegisterQuery q) {
        reader();
        q.validate();
        if (!Set.of("", "UNVERIFIED", "PENDING", "APPROVED", "REJECTED", "CANCELLED")
                .contains(q.getStatus())) throw new IllegalArgumentException("error.invalid");
        return RegisterPage.of(
                db.query(
                        CANDIDATES
                                + " where lower(e.description||' '||coalesce(e.payee,'')) like ?"
                                + " escape '!' and (?='' or coalesce(h.state,'UNVERIFIED')=?) and"
                                + " (?::date is null or e.date_created>=?::date) and (?::date is"
                                + " null or e.date_created<=?::date) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "e.id",
                                                "date",
                                                "e.date_created",
                                                "owner",
                                                "owner",
                                                "payee",
                                                "e.payee",
                                                "amount",
                                                "e.amount",
                                                "status",
                                                "evidence_state",
                                                "expenseStatus",
                                                "s.name"))
                                + " limit ? offset ?",
                        this::candidate,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public LegacyPayment payment(long id) {
        reader();
        return db.query(CANDIDATES + " where e.id=?", this::candidate, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    private static final String PROOFS =
            """
            select h.*,m.first_name||' '||m.last_name maker,p.first_name||' '||p.last_name payer,coalesce(r.first_name||' '||r.last_name,'') checker
            from expense_payment_evidence h join iam_staff_directory m on m.id=h.maker_id join iam_staff_directory p on p.id=h.payer_id
            left join iam_staff_directory r on r.id=h.checker_id
            """;

    private Evidence evidence(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Evidence(
                r.getLong("id"),
                r.getLong("expense_id"),
                r.getObject("paid_on", LocalDate.class),
                r.getLong("payer_id"),
                r.getString("payer"),
                r.getString("payment_reference"),
                r.getString("document_reference"),
                r.getString("explanation"),
                r.getString("opening_policy_reference"),
                r.getString("state"),
                r.getLong("maker_id"),
                r.getString("maker"),
                r.getTimestamp("created_at").toInstant(),
                r.getObject("checker_id", Long.class),
                r.getString("checker"),
                r.getString("review_comment"),
                r.getTimestamp("reviewed_at") == null
                        ? null
                        : r.getTimestamp("reviewed_at").toInstant(),
                r.getLong("version"));
    }

    @Transactional(readOnly = true)
    public Evidence find(long expense, long id) {
        payment(expense);
        return db
                .query(PROOFS + " where h.expense_id=? and h.id=?", this::evidence, expense, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Workspace workspace(long expense, Long evidenceId, RegisterQuery q) {
        var actor = reader();
        var payment = payment(expense);
        var proof = evidenceId == null ? null : find(expense, evidenceId);
        q.validate();
        var history =
                RegisterPage.of(
                        db.query(
                                PROOFS
                                        + " where h.expense_id=? and lower(h.document_reference||'"
                                        + " '||h.explanation||' '||h.review_comment) like ? escape"
                                        + " '!' order by "
                                        + q.order(
                                                Map.of(
                                                        "id",
                                                        "h.id",
                                                        "date",
                                                        "h.created_at",
                                                        "paidOn",
                                                        "h.paid_on",
                                                        "maker",
                                                        "maker",
                                                        "status",
                                                        "h.state",
                                                        "evidence",
                                                        "h.document_reference"))
                                        + " limit ? offset ?",
                                this::evidence,
                                expense,
                                q.pattern(),
                                q.getSize() + 1,
                                q.offset()),
                        q);
        return new Workspace(
                payment,
                proof,
                history,
                actor.has("expense.evidence.write")
                        && !Set.of("PENDING", "APPROVED").contains(payment.evidenceState()),
                proof != null
                        && proof.state().equals("PENDING")
                        && actor.has("expense.evidence.review")
                        && actor.id() != proof.makerId()
                        && actor.id() != proof.payerId()
                        && actor.id() != payment.ownerId(),
                proof != null
                        && proof.state().equals("PENDING")
                        && actor.has("expense.evidence.write")
                        && actor.id() == proof.makerId());
    }

    @Transactional(readOnly = true)
    public ChoicePage<Payer> payers(String search, int page) {
        reader();
        if (search == null || search.length() > 100 || page < 0 || page > 1000)
            throw new IllegalArgumentException("error.invalid");
        var q = new RegisterQuery();
        q.setSearch(search);
        return ChoicePage.from(
                db.query(
                        "select id,first_name||' '||last_name name from iam_staff_directory where"
                                + " lower(first_name||' '||last_name) like ? escape '!' order by"
                                + " first_name,last_name,id limit 26 offset ?",
                        (r, n) -> new Payer(r.getLong(1), r.getString(2)),
                        q.pattern(),
                        page * 25),
                page);
    }

    @Transactional(readOnly = true)
    public List<Payer> selectedPayer(Long id) {
        reader();
        return id == null
                ? List.of()
                : db.query(
                        "select id,first_name||' '||last_name from iam_staff_directory where id=?",
                        (r, n) -> new Payer(r.getLong(1), r.getString(2)),
                        id);
    }

    @Transactional
    public long create(long expense, @jakarta.validation.Valid PaymentEvidenceForm f) {
        var actor = access.require("expense.evidence.write");
        reader();
        String hash = fingerprint(expense, f);
        db.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "FCMS_PAYMENT_EVIDENCE/" + actor.id() + "/" + f.getRequestKey());
        var prior =
                db.queryForList(
                        "select id,payload_hash from expense_payment_evidence where maker_id=? and"
                                + " request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) prior.getFirst().get("id")).longValue();
        }
        db.queryForList("select id from expenses where id=? for update", expense);
        var payment = payment(expense);
        if (Set.of("PENDING", "APPROVED").contains(payment.evidenceState()))
            throw new IllegalArgumentException("error.conflict");
        if (f.getPaidOn().isBefore(payment.expenseDate())
                || f.getPaidOn()
                        .isAfter(com.foresight.fcms.shared.time.ApplicationCalendar.today()))
            throw new IllegalArgumentException("error.dates");
        if (!payment.paymentReference().isBlank()
                && !payment.paymentReference().trim().equals(f.getPaymentReference().trim()))
            throw new IllegalArgumentException("expense.evidence.retainedReference");
        if (selectedPayer(f.getPayerId()).isEmpty())
            throw new IllegalArgumentException("error.invalid");
        long id =
                db.queryForObject(
                        "insert into"
                            + " expense_payment_evidence(expense_id,paid_on,payer_id,payment_reference,document_reference,explanation,opening_policy_reference,maker_id,request_key,payload_hash)"
                            + " values (?,?,?,?,?,?,?,?,?,?) returning id",
                        Long.class,
                        expense,
                        f.getPaidOn(),
                        f.getPayerId(),
                        payment.paymentReference().isBlank()
                                ? f.getPaymentReference().trim()
                                : payment.paymentReference(),
                        f.getDocumentReference().trim(),
                        f.getExplanation().trim(),
                        f.getOpeningPolicyReference().trim(),
                        actor.id(),
                        f.getRequestKey(),
                        hash);
        audit.record(
                actor.id(),
                "expense.evidence.create",
                "expense_payment_evidence",
                id,
                "Expense #" + expense);
        observed("create");
        return id;
    }

    @Transactional
    public void review(long expense, long id, @jakarta.validation.Valid PaymentEvidenceReview f) {
        var actor = reader();
        if (f.action().equals("cancel")) actor.require("expense.evidence.write");
        else actor.require("expense.evidence.review");
        // Match operational payment writes: acquire the source row before financial boundary locks.
        db.queryForList("select id from expenses where id=? for update", expense);
        db.queryForList(
                "select pg_advisory_xact_lock_shared(hashtextextended('FCMS_FINANCE_CUTOVER',0))");
        db.queryForList(
                "select"
                    + " pg_advisory_xact_lock_shared(hashtextextended('FCMS_FINANCE_PERIOD_CLOSE',0))");
        db.queryForList(
                "select id from expense_payment_evidence where id=? and expense_id=? for update",
                id,
                expense);
        var p = payment(expense);
        var old = find(expense, id);
        String next =
                switch (f.action()) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    case "cancel" -> "CANCELLED";
                    default -> throw new IllegalArgumentException("error.invalid");
                };
        if (old.state().equals(next)
                && Objects.equals(old.checkerId(), actor.id())
                && old.version() == f.version() + 1
                && old.reviewComment().equals(f.comment().trim())) return;
        if (!old.state().equals("PENDING") || old.version() != f.version())
            throw new OptimisticLockingFailureException("stale");
        if (f.action().equals("cancel")) {
            if (old.makerId() != actor.id())
                throw new org.springframework.security.access.AccessDeniedException("denied");
        } else if (actor.id() == old.makerId()
                || actor.id() == old.payerId()
                || actor.id() == p.ownerId())
            throw new IllegalArgumentException("expense.evidence.independent");
        if (next.equals("APPROVED")) {
            var cutover =
                    db.queryForList(
                            "select starts_on,policy_reference from finance_gl_cutover where"
                                    + " id=true");
            if (!cutover.isEmpty()
                    && old.paidOn()
                            .isBefore(
                                    ((java.sql.Date) cutover.getFirst().get("starts_on"))
                                            .toLocalDate())
                    && !old.openingPolicyReference()
                            .equals(cutover.getFirst().get("policy_reference")))
                throw new IllegalArgumentException("expense.evidence.openingPolicyError");
        }
        if (db.update(
                        "update expense_payment_evidence set"
                            + " state=?,checker_id=?,review_comment=?,reviewed_at=clock_timestamp(),version=version+1"
                            + " where id=? and expense_id=? and version=? and state='PENDING'",
                        next,
                        actor.id(),
                        f.comment().trim(),
                        id,
                        expense,
                        f.version())
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "expense.evidence." + f.action(),
                "expense_payment_evidence",
                id,
                "Expense #" + expense + "; " + f.comment().trim());
        observed(f.action());
    }

    private void observed(String action) {
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                metrics.counter("fcms.expense.evidence", "action", action)
                                        .increment();
                            }
                        });
    }

    private static String fingerprint(long expense, PaymentEvidenceForm f) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                out.writeLong(expense);
                out.writeUTF(f.getPaidOn().toString());
                out.writeLong(f.getPayerId());
                out.writeUTF(f.getPaymentReference().trim());
                out.writeUTF(f.getDocumentReference().trim());
                out.writeUTF(f.getExplanation().trim());
                out.writeUTF(f.getOpeningPolicyReference().trim());
            }
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record LegacyPayment(
            long id,
            long ownerId,
            String owner,
            LocalDate expenseDate,
            String payee,
            String description,
            BigDecimal amount,
            String paymentReference,
            String expenseStatus,
            String evidenceState,
            Long evidenceId) {}

    public record Evidence(
            long id,
            long expenseId,
            LocalDate paidOn,
            long payerId,
            String payer,
            String paymentReference,
            String documentReference,
            String explanation,
            String openingPolicyReference,
            String state,
            long makerId,
            String maker,
            Instant createdAt,
            Long checkerId,
            String checker,
            String reviewComment,
            Instant reviewedAt,
            long version) {}

    public record Workspace(
            LegacyPayment payment,
            Evidence evidence,
            RegisterPage<Evidence> history,
            boolean canCreate,
            boolean canReview,
            boolean canCancel) {}

    public record Payer(long id, String name) {}
}
