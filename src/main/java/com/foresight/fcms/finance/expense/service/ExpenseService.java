package com.foresight.fcms.finance.expense.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.auth.service.UserDirectoryService;
import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.reference.FinancialYearRepository;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@org.springframework.validation.annotation.Validated
@Service
public class ExpenseService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final UserDirectoryService users;
    private final FinancialYearRepository years;

    public ExpenseService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            UserDirectoryService users,
            FinancialYearRepository years) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.users = users;
        this.years = years;
    }

    private static final String SELECT =
            "select e.*,s.name as status,coalesce(u.first_name||' '||u.last_name,'') as"
                + " owner,coalesce(c.first_name||' '||c.last_name,'') as"
                + " creator,coalesce(p.first_name||' '||p.last_name,'') as preparer from expenses e"
                + " join expense_status s on s.id=e.status_id left join iam_staff_directory u on"
                + " u.id=e.user_id left join iam_staff_directory c on c.id=e.created_by left join"
                + " iam_staff_directory p on p.id=e.prepared_by";

    private ExpenseView row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new ExpenseView(
                r.getLong("id"),
                r.getObject("date_created", java.time.LocalDate.class),
                r.getLong("user_id"),
                r.getString("owner"),
                r.getString("category"),
                r.getString("description"),
                r.getString("payee"),
                r.getBigDecimal("amount"),
                r.getString("financial_year"),
                r.getString("status"),
                r.getString("review_comment"),
                r.getString("payment_reference"),
                r.getLong("version"),
                r.getObject("created_by", Long.class),
                r.getString("creator"),
                r.getObject("prepared_by", Long.class),
                r.getString("preparer"));
    }

    @Transactional(readOnly = true)
    public RegisterPage<ExpenseView> list(ExpenseQuery q) {
        var a = access.require("expense.read");
        q.validate();
        return RegisterPage.of(
                db.query(
                        SELECT
                                + " where (? or e.user_id=? or (? and e.created_by=?)) and"
                                + " (?::bigint is null or e.user_id=?) and (?='' or"
                                + " e.financial_year=?) and (?='' or e.category=?) and"
                                + " lower(e.description||' '||e.category) like ? escape '!' and"
                                + " (?='' or s.name=?) and (?::date is null or"
                                + " e.date_created>=?::date) and (?::date is null or"
                                + " e.date_created<=?::date) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "e.id",
                                                "date",
                                                "e.date_created",
                                                "category",
                                                "e.category",
                                                "description",
                                                "e.description",
                                                "payee",
                                                "e.payee",
                                                "amount",
                                                "e.amount",
                                                "owner",
                                                "owner",
                                                "status",
                                                "s.name"))
                                + " limit ? offset ?",
                        this::row,
                        a.has("expense.approve"),
                        a.id(),
                        a.has("expense.assign"),
                        a.id(),
                        q.getOwnerId(),
                        q.getOwnerId(),
                        q.getYear(),
                        q.getYear(),
                        q.getCategory(),
                        q.getCategory(),
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
    public ExpenseView find(long id) {
        var a = access.require("expense.read");
        return db
                .query(
                        SELECT + " where e.id=? and (? or e.user_id=? or (? and e.created_by=?))",
                        this::row,
                        id,
                        a.has("expense.approve"),
                        a.id(),
                        a.has("expense.assign"),
                        a.id())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    private boolean mayPrepare(AccessService.Actor actor, ExpenseView record) {
        return record.ownerId() == actor.id()
                || actor.has("expense.assign")
                        && (Objects.equals(record.createdBy(), actor.id())
                                || actor.has("expense.approve"));
    }

    private boolean involved(AccessService.Actor actor, ExpenseView record) {
        return record.ownerId() == actor.id()
                || Objects.equals(record.createdBy(), actor.id())
                || Objects.equals(record.preparedBy(), actor.id());
    }

    public record YearChoice(long id, String label) {}

    public record Options(
            boolean assignmentAllowed,
            List<UserDirectoryService.Choice> staff,
            List<YearChoice> years,
            String retainedYear) {}

    @Transactional(readOnly = true)
    public Options options(ExpenseForm form, Long id) {
        var actor = access.require("expense.write");
        var retained = id == null ? null : find(id);
        if (form.getOwnerId() == null)
            form.setOwnerId(retained == null ? actor.id() : retained.ownerId());
        var choices =
                years.findTop1001ByOrderByFyearDesc().stream()
                        .map(y -> new YearChoice(y.getId(), y.getFyear()))
                        .toList();
        if (choices.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        if (id == null && form.getFinancialYearId() == null && form.getDate() != null)
            choices.stream()
                    .filter(y -> y.label().equals(String.valueOf(form.getDate().getYear())))
                    .findFirst()
                    .ifPresent(y -> form.setFinancialYearId(y.id()));
        var staff = new LinkedHashMap<Long, UserDirectoryService.Choice>();
        if (actor.has("expense.assign")) {
            users.selected(Set.of(form.getOwnerId())).forEach(c -> staff.put(c.id(), c));
            users.staff("").forEach(c -> staff.putIfAbsent(c.id(), c));
        }
        return new Options(
                actor.has("expense.assign"),
                List.copyOf(staff.values()),
                choices,
                retained == null ? null : retained.year());
    }

    @Transactional(readOnly = true)
    public @jakarta.validation.Valid ExpenseForm form(long id) {
        var actor = access.require("expense.write");
        var r = find(id);
        if (!mayPrepare(actor, r))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (!Set.of("draft", "rejected").contains(r.status()))
            throw new IllegalArgumentException("error.invalid");
        var f = new ExpenseForm();
        f.setDate(r.date());
        f.setCategory(r.category());
        f.setDescription(r.description());
        f.setPayee(r.payee());
        f.setAmount(r.amount());
        f.setVersion(r.version());
        f.setOwnerId(r.ownerId());
        years.findByFyear(r.year()).ifPresent(y -> f.setFinancialYearId(y.getId()));
        return f;
    }

    private String creationHash(ExpenseForm form, long owner, String year) {
        return com.foresight.fcms.shared.command.CommandFingerprint.of(
                Long.toString(owner),
                form.getDate().toString(),
                form.getCategory(),
                form.getDescription(),
                form.getPayee(),
                form.getAmount().stripTrailingZeros().toPlainString(),
                year);
    }

    @Transactional
    public long save(Long id, @jakarta.validation.Valid ExpenseForm f) {
        var actor = access.require("expense.write");
        if (f.getAmount().signum() <= 0
                || f.getAmount().scale() > 2
                || f.getDate().isAfter(com.foresight.fcms.shared.time.ApplicationCalendar.today()))
            throw new IllegalArgumentException("error.invalid");
        ExpenseView old = id == null ? null : find(id);
        if (old != null && !mayPrepare(actor, old))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (old != null && !Set.of("draft", "rejected").contains(old.status()))
            throw new IllegalArgumentException("error.invalid");
        long owner =
                f.getOwnerId() == null
                        ? (old == null ? actor.id() : old.ownerId())
                        : f.getOwnerId();
        if (owner != actor.id()
                && !actor.has("expense.assign")
                && (old == null || owner != old.ownerId())) actor.require("expense.assign");
        if (old != null && owner != old.ownerId()) actor.require("expense.assign");
        if (owner != actor.id()) {
            try {
                users.requireActive(Set.of(owner));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("expense.activeStaff");
            }
        }
        String year =
                f.getFinancialYearId() == null
                        ? (old == null ? String.valueOf(f.getDate().getYear()) : old.year())
                        : years.findById(f.getFinancialYearId())
                                .orElseThrow(
                                        () -> new IllegalArgumentException("expense.yearMissing"))
                                .getFyear();
        if (id == null) {
            String hash = creationHash(f, owner, year);
            db.query(
                    "select pg_advisory_xact_lock(hashtextextended(?,0))",
                    (r, n) -> 0,
                    "fcms.expense-create:" + actor.id() + ":" + f.getRequestKey());
            var previous =
                    db.query(
                            "select id,creation_hash,(date_created=? and user_id=? and category=?"
                                + " and description=? and payee=? and amount=? and"
                                + " financial_year=?) same_payload from expenses where created_by=?"
                                + " and request_key=?",
                            (r, n) -> new Object[] {r.getLong(1), r.getString(2), r.getBoolean(3)},
                            f.getDate(),
                            owner,
                            f.getCategory(),
                            f.getDescription(),
                            f.getPayee(),
                            f.getAmount(),
                            year,
                            actor.id(),
                            f.getRequestKey());
            if (!previous.isEmpty()) {
                var retained = previous.getFirst();
                if (retained[1] == null ? (boolean) retained[2] : hash.equals(retained[1]))
                    return (long) retained[0];
                throw new IllegalArgumentException("expense.requestConflict");
            }
            if (db.queryForObject(
                    "select exists(select 1 from expenses where created_by is null and user_id=?"
                            + " and request_key=?)",
                    Boolean.class,
                    owner,
                    f.getRequestKey()))
                throw new IllegalArgumentException("expense.requestConflict");
            id =
                    db.queryForObject(
                            "insert into"
                                + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id,request_key,created_by,prepared_by,creation_hash)"
                                + " values (?,?,?,?,?,?,?,1,?,?,?,?) returning id",
                            Long.class,
                            f.getDate(),
                            owner,
                            f.getCategory(),
                            f.getDescription(),
                            f.getPayee(),
                            f.getAmount(),
                            year,
                            f.getRequestKey(),
                            actor.id(),
                            actor.id(),
                            hash);
        } else if (db.update(
                        "update expenses set"
                            + " date_created=?,user_id=?,category=?,description=?,payee=?,amount=?,financial_year=?,prepared_by=?,status_id=1,version=version+1,updated_at=current_timestamp"
                            + " where id=? and version=?",
                        f.getDate(),
                        owner,
                        f.getCategory(),
                        f.getDescription(),
                        f.getPayee(),
                        f.getAmount(),
                        year,
                        actor.id(),
                        id,
                        f.getVersion())
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                "expense.save",
                "expenses",
                id,
                "Draft saved; beforeOwner="
                        + (old == null ? "" : old.ownerId())
                        + "; owner="
                        + owner);
        return id;
    }

    @Transactional
    public void transition(
            long id, long version, String action, String comment, String reference, UUID request) {
        var a = access.current();
        if (comment == null
                || comment.length() > 1000
                || reference == null
                || reference.length() > 100
                || (action.equals("reverse") && request == null))
            throw new IllegalArgumentException("error.invalid");
        db.queryForList("select id from expenses where id=? for update", id);
        var old = find(id);
        String next;
        switch (action) {
            case "submit" -> {
                a.require("expense.write");
                if (!mayPrepare(a, old) || !Set.of("draft", "rejected").contains(old.status()))
                    throw new IllegalArgumentException();
                next = "submitted";
            }
            case "approve", "reject" -> {
                a.require("expense.approve");
                if (involved(a, old)
                        || !old.status().equals("submitted")
                        || comment.isBlank()
                        || comment.length() > 1000) throw new IllegalArgumentException();
                next = action.equals("approve") ? "approved" : "rejected";
            }
            case "pay" -> {
                a.require("expense.approve");
                if (!old.status().equals("approved")
                        || reference.isBlank()
                        || reference.length() > 100) throw new IllegalArgumentException();
                next = "paid";
            }
            case "reverse" -> {
                a.require("finance.reverse");
                if (old.status().equals("reversed")) return;
                if (!old.status().equals("paid")
                        || involved(a, old)
                        || comment.isBlank()
                        || comment.length() > 1000) throw new IllegalArgumentException();
                db.update(
                        "insert into expense_reversals(expense_id,actor_id,reason,request_key)"
                                + " values (?,?,?,?)",
                        id,
                        a.id(),
                        comment,
                        request);
                next = "reversed";
            }
            default -> throw new IllegalArgumentException();
        }
        if (db.update(
                        "update expenses set status_id=(select id from expense_status where"
                            + " name=?),review_comment=?,payment_reference=case when ?='paid' then"
                            + " ? else payment_reference end,paid_at=case when ?='paid' then"
                            + " current_timestamp else paid_at end,paid_on=case when ?='paid' then"
                            + " ? else paid_on end,paid_by=case when ?='paid' then ? else paid_by"
                            + " end,approved_by=case when ?='approved' then ? else approved_by"
                            + " end,reviewed_at=current_timestamp,reversed_by=case when"
                            + " ?='reversed' then ? else reversed_by end,reversed_at=case when"
                            + " ?='reversed' then current_timestamp else reversed_at"
                            + " end,version=version+1,updated_at=current_timestamp where id=? and"
                            + " version=?",
                        next,
                        comment,
                        next,
                        reference,
                        next,
                        next,
                        com.foresight.fcms.shared.time.ApplicationCalendar.today(),
                        next,
                        a.id(),
                        next,
                        a.id(),
                        next,
                        a.id(),
                        next,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        db.update(
                "insert into"
                    + " expense_events(expense_id,action,from_status,to_status,actor_id,reason,payment_reference,amount,category,payee,version)"
                    + " values (?,?,?,?,?,?,?,?,?,?,?)",
                id,
                action,
                old.status(),
                next,
                a.id(),
                comment,
                action.equals("pay") ? reference : old.paymentReference(),
                old.amount(),
                old.category(),
                old.payee(),
                version + 1);
        audit.record(a.id(), "expense." + action, "expenses", id, next);
    }

    @Transactional(readOnly = true)
    public com.foresight.fcms.shared.dto.RegisterPage<
                    com.foresight.fcms.finance.expense.dto.ExpenseEvent>
            history(long id, RegisterQuery q) {
        find(id);
        return historyRows(id, q);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ExpenseDetails details(long id, RegisterQuery q) {
        var record = find(id);
        return new ExpenseDetails(
                record,
                historyRows(id, q),
                Boolean.TRUE.equals(
                        db.queryForObject(
                                "select exists(select 1 from expense_legacy_payment_candidates"
                                        + " where expense_id=?)",
                                Boolean.class,
                                id)),
                commands(record));
    }

    public record Commands(boolean prepare, boolean review, boolean pay, boolean reverse) {}

    private Commands commands(ExpenseView record) {
        var actor = access.require("expense.read");
        return new Commands(
                actor.has("expense.write")
                        && mayPrepare(actor, record)
                        && Set.of("draft", "rejected").contains(record.status()),
                actor.has("expense.approve")
                        && !involved(actor, record)
                        && record.status().equals("submitted"),
                actor.has("expense.approve") && record.status().equals("approved"),
                actor.has("finance.reverse")
                        && !involved(actor, record)
                        && record.status().equals("paid"));
    }

    public record ExpenseDetails(
            com.foresight.fcms.finance.expense.dto.ExpenseView record,
            com.foresight.fcms.shared.dto.RegisterPage<
                            com.foresight.fcms.finance.expense.dto.ExpenseEvent>
                    history,
            boolean paymentEvidenceAvailable,
            Commands commands) {}

    private com.foresight.fcms.shared.dto.RegisterPage<
                    com.foresight.fcms.finance.expense.dto.ExpenseEvent>
            historyRows(long id, RegisterQuery q) {
        q.validate();
        return com.foresight.fcms.shared.dto.RegisterPage.of(
                db.query(
                        "select h.*,u.first_name||' '||u.last_name actor from expense_events h join"
                            + " iam_staff_directory u on u.id=h.actor_id where h.expense_id=? and"
                            + " lower(h.action||' '||h.reason) like ? escape '!' and (?::date is"
                            + " null or h.occurred_at>=?::date) and (?::date is null or"
                            + " h.occurred_at<(?::date+1)) order by "
                                + q.order(
                                        java.util.Map.of(
                                                "id",
                                                "h.id",
                                                "date",
                                                "h.occurred_at",
                                                "action",
                                                "h.action",
                                                "from",
                                                "h.from_status",
                                                "to",
                                                "h.to_status",
                                                "actor",
                                                "actor",
                                                "reason",
                                                "h.reason",
                                                "reference",
                                                "h.payment_reference",
                                                "amount",
                                                "h.amount"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new com.foresight.fcms.finance.expense.dto.ExpenseEvent(
                                        r.getLong("id"),
                                        r.getString("action"),
                                        r.getString("from_status"),
                                        r.getString("to_status"),
                                        r.getString("actor"),
                                        r.getString("reason"),
                                        r.getString("payment_reference"),
                                        r.getBigDecimal("amount"),
                                        r.getObject("occurred_at", java.time.OffsetDateTime.class)),
                        id,
                        q.pattern(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public java.util.List<com.foresight.fcms.finance.expense.dto.ExpenseView> report(
            ExpenseQuery query) {
        access.require("expense.read");
        int original = query.getPage();
        int size = query.getSize();
        try {
            query.setSize(100);
            var rows =
                    new java.util.ArrayList<com.foresight.fcms.finance.expense.dto.ExpenseView>();
            for (int p = 0; p <= 10; p++) {
                query.setPage(p);
                var page = list(query);
                rows.addAll(page.rows());
                if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
                if (!page.hasNext()) return java.util.List.copyOf(rows);
            }
            throw new IllegalArgumentException("error.narrowReport");
        } finally {
            query.setPage(original);
            query.setSize(size);
        }
    }
}
