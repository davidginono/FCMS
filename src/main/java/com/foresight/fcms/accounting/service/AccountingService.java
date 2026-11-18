package com.foresight.fcms.accounting.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
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

    public AccountingService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CoaChart chart(CoaQuery q, Long parent) {
        var actor = access.require("accounting.read");
        q.validate();
        var path = parent == null ? List.<Account>of() : chartPath(parent, false);
        String where = " where lower(a.code||' '||a.name) like ? escape '!'"
                + " and (?='' or a.active=(?='ACTIVE'))"
                + " and (?='' or a.classification=?)"
                + " and (?='' or (case when a.depth=4 then 'POSTING' else 'GROUP' end)=?)";
        var args = new ArrayList<Object>(List.of(q.pattern(), q.getStatus(), q.getStatus(),
                q.getClassification(), q.getClassification(), q.getKind(), q.getKind()));
        if (parent != null) {
            where += " and a.parent_id=?";
            args.add(parent);
        } else if (!q.filtered()) where += " and a.parent_id is null";
        long total = db.queryForObject("select count(*) from gl_accounts a" + where, Long.class, args.toArray());
        String column = Map.of("code", "a.code", "name", "a.name", "parent", "p.name",
                "classification", "a.classification", "kind", "case when a.depth=4 then 'POSTING' else 'GROUP' end",
                "normal", "a.normal_balance", "status", "a.active").get(q.getSort());
        args.add(q.getSize() + 1);
        args.add(q.offset());
        String select = "select a.id,a.code,a.name,a.description,a.classification,a.normal_balance,"
                + "a.depth,a.parent_id,a.cash_account,a.active,a.version,coalesce(p.code||' '||p.name,'') parent_name,"
                + "case when a.depth=4 then true else not exists(select 1 from gl_accounts c"
                + " where c.parent_id=a.id and c.active) end no_active_children,"
                + "coalesce(p.active,false) parent_active from gl_accounts a left join gl_accounts p on p.id=a.parent_id";
        var rows = db.query(select + where + " order by " + column + " " + q.getDirection()
                + ", a.id " + q.getDirection() + " limit ? offset ?",
                (r, n) -> coaRow(row(r, n), actor, r.getBoolean("no_active_children"), r.getBoolean("parent_active")), args.toArray());
        var records = rows.stream().limit(q.getSize()).toList();
        return new CoaChart(new RegisterPage<>(records, q.getPage(), q.getSize(), rows.size() > q.getSize()), path, total);
    }

    private CoaRow coaRow(Account a, AccessService.Actor actor, boolean noActiveChildren, boolean parentActive) {
        boolean group = a.depth() < 4, setup = actor.has("accounting.setup");
        return new CoaRow(a, group ? "GROUP" : "POSTING", group,
                setup && a.active() && a.depth() < 3,
                setup && a.active() && a.depth() == 3,
                setup && a.depth() > 1, setup && a.depth() > 1 && (a.active() ? noActiveChildren : parentActive),
                !group && actor.has("accounting.report"));
    }

    // At most four ancestors; validate the actual chain as well as readable code prefixes.
    private List<Account> chartPath(long id, boolean active) {
        if (id < 1) throw new IllegalArgumentException("error.invalid");
        var path = db.query("with recursive path as (select a.*,0 hops from gl_accounts a where id=?"
                + " union all select a.*,p.hops+1 from gl_accounts a join path p on a.id=p.parent_id"
                + " where p.hops<4) select path.*,coalesce(p.code||' '||p.name,'') parent_name"
                + " from path left join gl_accounts p on p.id=path.parent_id order by hops desc limit 5", this::row, id);
        if (path.isEmpty() || path.size() > 4 || path.getFirst().depth() != 1
                || path.getFirst().parentId() != null || path.getLast().depth() == 4)
            throw new IllegalArgumentException("gl.error.parent");
        String[] types = {"ASSET", "LIABILITY", "EQUITY", "EXPENSE", "INCOME"};
        Account previous = null;
        for (var a : path) {
            boolean codeValid = a.depth() == 1 ? a.code().matches("[1-5]00000")
                    : a.code().matches("[1-5][1-9]" + (a.depth() == 2 ? "0000" : "[1-9]000"));
            if (!codeValid || a.depth() != (previous == null ? 1 : previous.depth() + 1)
                    || !a.classification().equals(types[a.code().charAt(0) - '1'])
                    || active && !a.active()
                    || previous != null && (!Objects.equals(a.parentId(), previous.id())
                        || !a.code().startsWith(previous.code().substring(0, previous.depth()))
                        || !a.normalBalance().equals(previous.normalBalance())))
                throw new IllegalArgumentException("gl.error.parent");
            previous = a;
        }
        return List.copyOf(path);
    }

    @Transactional(readOnly = true)
    public String nextAccountCode(long parentId, String mode) {
        access.require("accounting.setup");
        var parent = creationParent(parentId, mode);
        int step = parent.depth() == 1 ? 10000 : parent.depth() == 2 ? 1000 : 1;
        int base = Integer.parseInt(parent.code());
        return db.query("select n::text from generate_series(?,?,?) n where not exists"
                + " (select 1 from gl_accounts where code=n::text) order by n limit 1",
                (r, n) -> r.getString(1), base + step, base + (parent.depth() == 3 ? 999 : 9 * step), step)
                .stream().findFirst().orElse("");
    }

    private Account creationParent(long id, String mode) {
        if (!Set.of("", "GROUP", "POSTING").contains(mode)) throw new IllegalArgumentException("error.invalid");
        var path = chartPath(id, true);
        var parent = path.getLast();
        if ("GROUP".equals(mode) && parent.depth() >= 3 || "POSTING".equals(mode) && parent.depth() != 3)
            throw new IllegalArgumentException("gl.error.parent");
        return parent;
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
        return choices(search, page, posting, "");
    }

    @Transactional(readOnly = true)
    public ChoicePage<Choice> choices(String search, int page, boolean posting, String parentMode) {
        access.require("accounting.read");
        if (!Set.of("", "GROUP", "POSTING").contains(parentMode) || posting && !parentMode.isEmpty())
            throw new IllegalArgumentException("error.invalid");
        if (search == null || search.length() > 100 || page < 0 || page > 1000)
            throw new IllegalArgumentException("error.invalid");
        return ChoicePage.from(
                db.query(
                        "select id,code||' '||name,depth,classification,normal_balance from"
                                + " gl_accounts where active and "
                                + (posting ? "depth=4" : "GROUP".equals(parentMode) ? "depth<3" : "POSTING".equals(parentMode) ? "depth=3" : "depth<4")
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
        p = creationParent(p.id(), f.getMode());
        int depth = p.depth() + 1;
        if (f.getCashAccount() && (depth != 4 || !p.classification().equals("ASSET")))
            throw new FieldValidationException("accounting.error.cashAccount", "cashAccount");
        String code = f.getCode();
        if (!code.startsWith(p.code().substring(0, p.depth()))
                || (depth < 4
                        && !code.matches(
                                "[1-5][0-9]{" + (depth - 2) + "}[1-9]0{" + (6 - depth) + "}"))
                || (depth == 4 && code.endsWith("000")))
            throw new FieldValidationException("error.invalid", "code");
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

    public static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("error.invalid");
    }

    public record Choice(
            long id, String name, int depth, String classification, String normalBalance) {}
}
