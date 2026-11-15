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

import java.util.*;

@Service
@Validated
public class AccountingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final SourceCoverageService coverage;
    private final com.foresight.fcms.finance.service.FinanceCutoverService cutover;

    public AccountingService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            com.foresight.fcms.finance.service.FinanceCutoverService cutover,
            SourceCoverageService coverage) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.cutover = cutover;
        this.coverage = coverage;
    }

    private static final String ACCOUNTS =
            "select a.*,coalesce(p.code||' '||p.name,'') parent_name from gl_accounts a left join"
                    + " gl_accounts p on p.id=a.parent_id";

    private Account row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Account(
                r.getLong("id"),
                r.getString("code"),
                r.getString("name"),
                r.getString("description"),
                r.getString("classification"),
                r.getString("normal_balance"),
                r.getInt("depth"),
                r.getObject("parent_id", Long.class),
                r.getString("parent_name"),
                r.getBoolean("cash_account"),
                r.getBoolean("active"),
                r.getLong("version"));
    }

    @Transactional(readOnly = true)
    public RegisterPage<Account> accounts(RegisterQuery q, Long parent) {
        access.require("accounting.read");
        q.validate();
        return RegisterPage.of(
                db.query(
                        ACCOUNTS
                                + " where (?::bigint is null or a.parent_id=?::bigint) and"
                                + " lower(a.code||' '||a.name) like ? escape '!' and (?='' or"
                                + " a.active=(?='ACTIVE')) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "a.id",
                                                "code",
                                                "a.code",
                                                "name",
                                                "a.name",
                                                "classification",
                                                "a.classification",
                                                "normal",
                                                "a.normal_balance",
                                                "status",
                                                "a.active",
                                                "parent",
                                                "parent_name"))
                                + " limit ? offset ?",
                        this::row,
                        parent,
                        parent,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    private static final String ACCOUNT_FILTER =
            " where (?::bigint is null or a.parent_id=?::bigint)"
                    + " and lower(a.code||' '||a.name) like ? escape '!'"
                    + " and (?='' or a.active=(?='ACTIVE'))";
    private static final Set<String> CLASSIFICATIONS =
            Set.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE");

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GroupedAccounts groupedAccounts(
            RegisterQuery q, Long parent, String group, int groupPage) {
        access.require("accounting.read");
        q.validate();
        if (!Set.of("", "ACTIVE", "INACTIVE").contains(q.getStatus())
                || parent != null && parent < 1
                || groupPage < 0
                || groupPage > 10000
                || group != null && !CLASSIFICATIONS.contains(group))
            throw new IllegalArgumentException("error.invalid");
        var counts =
                db.query(
                        "select a.classification,count(*) from gl_accounts a"
                                + ACCOUNT_FILTER
                                + " group by a.classification order by "
                                + (Objects.equals(q.getSort(), "classification")
                                        ? "a.classification " + q.getDirection()
                                        : "min(a.code) asc"),
                        (r, n) ->
                                new AbstractMap.SimpleImmutableEntry<>(
                                        r.getString(1), r.getLong(2)),
                        parent,
                        parent,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus());
        String order =
                q.order(
                        Map.of(
                                "id",
                                "a.id",
                                "code",
                                "a.code",
                                "name",
                                "a.name",
                                "classification",
                                "a.classification",
                                "normal",
                                "a.normal_balance",
                                "status",
                                "a.active",
                                "parent",
                                "parent_name"));
        var groups = new ArrayList<AccountGroup>();
        for (var count : counts) {
            int page = count.getKey().equals(group) ? groupPage : 0;
            var rows =
                    db.query(
                            ACCOUNTS
                                    + ACCOUNT_FILTER
                                    + " and a.classification=? order by "
                                    + order
                                    + " limit ? offset ?",
                            this::row,
                            parent,
                            parent,
                            q.pattern(),
                            q.getStatus(),
                            q.getStatus(),
                            count.getKey(),
                            q.getSize() + 1,
                            (long) page * q.getSize());
            groups.add(
                    new AccountGroup(
                            count.getKey(),
                            count.getValue(),
                            new RegisterPage<>(
                                    List.copyOf(
                                            rows.subList(0, Math.min(q.getSize(), rows.size()))),
                                    page,
                                    q.getSize(),
                                    rows.size() > q.getSize())));
        }
        return new GroupedAccounts(
                List.copyOf(groups), counts.stream().mapToLong(Map.Entry::getValue).sum());
    }

    @Transactional(readOnly = true)
    public Account account(long id) {
        access.require("accounting.read");
        return db.query(ACCOUNTS + " where a.id=?", this::row, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public ChoicePage<Choice> choices(String search, int page, boolean posting) {
        access.require("accounting.read");
        if (search == null || search.length() > 100 || page < 0 || page > 1000)
            throw new IllegalArgumentException("error.invalid");
        return ChoicePage.from(
                db.query(
                        "select id,code||' '||name,depth,classification,normal_balance from"
                                + " gl_accounts where active and "
                                + (posting ? "depth=4" : "depth<4")
                                + " and lower(code||' '||name) like ? escape '!' order by code,id"
                                + " limit 26 offset ?",
                        (r, n) ->
                                new Choice(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getInt(3),
                                        r.getString(4),
                                        r.getString(5)),
                        "%"
                                + search.toLowerCase(Locale.ROOT)
                                        .replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                + "%",
                        page * 25),
                page);
    }

    @Transactional(readOnly = true)
    public List<Choice> selected(Collection<Long> ids) {
        access.require("accounting.read");
        if (ids.size() > 50 || ids.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("error.invalid");
        if (ids.isEmpty()) return List.of();
        return db.query(
                "select id,code||' '||name,depth,classification,normal_balance from gl_accounts"
                        + " where id=any (?) order by code",
                (r, n) ->
                        new Choice(
                                r.getLong(1),
                                r.getString(2),
                                r.getInt(3),
                                r.getString(4),
                                r.getString(5)),
                (Object) ids.toArray(Long[]::new));
    }

    @Transactional
    public long saveAccount(Long id, @jakarta.validation.Valid AccountForm f) {
        var actor = access.require("accounting.setup");
        var p = account(f.getParentId());
        if (id != null) {
            var old = account(id);
            if (old.depth() == 1
                    || !old.code().equals(f.getCode())
                    || !Objects.equals(old.parentId(), f.getParentId())
                    || !old.normalBalance().equals(f.getNormalBalance())
                    || old.cashAccount() != f.getCashAccount())
                throw new IllegalArgumentException("error.invalid");
            if (db.update(
                            "update gl_accounts set name=?,description=?,version=version+1 where"
                                    + " id=? and version=?",
                            f.getName().trim(),
                            f.getDescription().trim(),
                            id,
                            f.getVersion())
                    != 1) throw new OptimisticLockingFailureException("stale");
            audit.record(actor.id(), "accounting.account.edit", "gl_accounts", id, old.code());
            return id;
        }
        if (!p.active() || p.depth() >= 4) throw new IllegalArgumentException("error.invalid");
        int depth = p.depth() + 1;
        if (f.getCashAccount() && (depth != 4 || !p.classification().equals("ASSET")))
            throw new IllegalArgumentException("accounting.error.cashAccount");
        String code = f.getCode();
        if (!code.startsWith(p.code().substring(0, p.depth()))
                || (depth < 4
                        && !code.matches(
                                "[1-5][0-9]{" + (depth - 2) + "}[1-9]0{" + (6 - depth) + "}"))
                || (depth == 4 && code.endsWith("000")))
            throw new IllegalArgumentException("error.invalid");
        String normal = depth < 4 ? p.normalBalance() : f.getNormalBalance();
        long created =
                db.queryForObject(
                        "insert into"
                            + " gl_accounts(code,name,description,classification,normal_balance,depth,parent_id,cash_account,created_by)"
                            + " values (?,?,?,?,?,?,?,?,?) returning id",
                        Long.class,
                        code,
                        f.getName().trim(),
                        f.getDescription().trim(),
                        p.classification(),
                        normal,
                        depth,
                        p.id(),
                        f.getCashAccount(),
                        actor.id());
        audit.record(actor.id(), "accounting.account.create", "gl_accounts", created, code);
        return created;
    }

    @Transactional
    public void accountState(long id, long version, boolean active) {
        var actor = access.require("accounting.setup");
        var row = account(id);
        if (row.depth() == 1) throw new IllegalArgumentException("error.invalid");
        if (db.update(
                        "update gl_accounts set active=?,version=version+1 where id=? and"
                                + " version=?",
                        active,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                active ? "accounting.account.activate" : "accounting.account.deactivate",
                "gl_accounts",
                id,
                row.code());
    }

    @Transactional(readOnly = true)
    public Optional<Book> books() {
        access.require("accounting.read");
        return db
                .query(
                        "select b.*,b.state='APPROVED' and (b.opening_mode='ZERO' or exists(select"
                            + " 1 from gl_journals where kind='OPENING' and state='POSTED')) ready"
                            + " from gl_books b",
                        (r, n) ->
                                new Book(
                                        r.getObject("opening_date", java.time.LocalDate.class),
                                        r.getString("policy_reference"),
                                        r.getString("opening_mode"),
                                        r.getString("state"),
                                        r.getLong("maker_id"),
                                        r.getObject("checker_id", Long.class),
                                        r.getLong("version"),
                                        r.getBoolean("ready")))
                .stream()
                .findFirst();
    }

    @Transactional
    public void configure(@jakarta.validation.Valid BookForm f) {
        var actor = access.require("accounting.setup");
        db.queryForList("select id from gl_books where id=true for update");
        var old = books();
        if (old.isPresent()) {
            if (old.get().state().equals("APPROVED") || old.get().makerId() != actor.id())
                throw new IllegalArgumentException("error.invalid");
            if (db.update(
                            "update gl_books set"
                                + " opening_date=?,policy_reference=?,opening_mode=?,version=version+1"
                                + " where id=true and version=?",
                            f.getOpeningDate(),
                            f.getPolicyReference().trim(),
                            f.getOpeningMode(),
                            f.getVersion())
                    != 1) throw new OptimisticLockingFailureException("stale");
        } else
            db.update(
                    "insert into"
                        + " gl_books(id,opening_date,policy_reference,opening_mode,state,maker_id)"
                        + " values (true,?,?,?,'PENDING',?)",
                    f.getOpeningDate(),
                    f.getPolicyReference().trim(),
                    f.getOpeningMode(),
                    actor.id());
        audit.record(
                actor.id(),
                "accounting.books.configure",
                "gl_books",
                null,
                f.getPolicyReference().trim());
    }

    @Transactional
    public void approveBooks(long version, String reason) {
        var actor = access.require("accounting.review");
        requireReason(reason);
        db.queryForList("select id from gl_books where id=true for update");
        var b = books().orElseThrow(() -> new IllegalArgumentException("error.invalid"));
        if (!b.state().equals("PENDING") || b.makerId() == actor.id())
            throw new IllegalArgumentException("error.invalid");
        var cutoverAt = cutover.approve(b.openingDate(), b.policyReference());
        if (db.update(
                        "update gl_books set"
                                + " state='APPROVED',checker_id=?,approved_at=?,version=version+1"
                                + " where id=true and version=?",
                        actor.id(),
                        java.sql.Timestamp.from(cutoverAt),
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(actor.id(), "accounting.books.approve", "gl_books", null, reason);
    }

    @Transactional(readOnly = true)
    public RegisterPage<Period> periods(RegisterQuery q) {
        access.require("accounting.read");
        q.validate();
        return RegisterPage.of(
                db.query(
                        "select * from gl_periods where lower(name) like ? escape '!' and (?='' or"
                                + " state=?) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "id",
                                                "name",
                                                "name",
                                                "from",
                                                "starts_on",
                                                "to",
                                                "ends_on",
                                                "status",
                                                "state"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Period(
                                        r.getLong("id"),
                                        r.getString("name"),
                                        r.getObject("starts_on", java.time.LocalDate.class),
                                        r.getObject("ends_on", java.time.LocalDate.class),
                                        r.getString("state"),
                                        r.getLong("version")),
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional
    public long createPeriod(@jakarta.validation.Valid PeriodForm f) {
        var actor = access.require("accounting.setup");
        if (f.getEndsOn().isBefore(f.getStartsOn())
                || java.time.temporal.ChronoUnit.DAYS.between(f.getStartsOn(), f.getEndsOn()) > 366)
            throw new IllegalArgumentException("error.dates");
        long id =
                db.queryForObject(
                        "insert into gl_periods(name,starts_on,ends_on,created_by) values (?,?,?,?)"
                                + " returning id",
                        Long.class,
                        f.getName().trim(),
                        f.getStartsOn(),
                        f.getEndsOn(),
                        actor.id());
        audit.record(actor.id(), "accounting.period.create", "gl_periods", id, f.getName().trim());
        return id;
    }

    @Transactional(readOnly = true)
    public Period period(long id) {
        access.require("accounting.read");
        return db
                .query(
                        "select * from gl_periods where id=?",
                        (r, n) ->
                                new Period(
                                        r.getLong("id"),
                                        r.getString("name"),
                                        r.getObject("starts_on", java.time.LocalDate.class),
                                        r.getObject("ends_on", java.time.LocalDate.class),
                                        r.getString("state"),
                                        r.getLong("version")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional
    public void periodState(long id, long version, boolean close, String reason) {
        var actor = access.require("accounting.period.close");
        requireReason(reason);
        var states =
                db.queryForList(
                        "select state from gl_periods where id=? for update", String.class, id);
        if (states.isEmpty() || states.getFirst().equals(close ? "CLOSED" : "OPEN"))
            throw new IllegalArgumentException("error.invalid");
        var current = period(id);
        cutover.periodState(id, current.startsOn(), current.endsOn(), close, reason);
        if (close
                && (!books().map(Book::ready).orElse(false)
                        || !coverage.through(current.endsOn()).complete()))
            throw new IllegalArgumentException("gl.source.incomplete");
        if (db.update(
                        "update gl_periods set"
                            + " state=?,closed_by=?,closed_at=current_timestamp,close_reason=?,version=version+1"
                            + " where id=? and version=?",
                        close ? "CLOSED" : "OPEN",
                        actor.id(),
                        reason,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                close ? "accounting.period.close" : "accounting.period.reopen",
                "gl_periods",
                id,
                reason);
    }

    public static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("error.invalid");
    }

    public record Choice(
            long id, String name, int depth, String classification, String normalBalance) {}
}
