package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.EquityReporting.*;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.util.*;

/** Reviewed report metadata; never changes a posted journal or its lines. */
@Service
@Validated
public class EquityReportingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final JsonMapper json = JsonMapper.builder().build();

    public EquityReportingService(JdbcTemplate db, AccessService access, AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    private static final String SELECT =
            "select r.*,m.first_name||' '||m.last_name maker,coalesce(c.first_name||'"
                + " '||c.last_name,'') checker from gl_equity_reporting r join users m on"
                + " m.id=r.maker_id left join users c on c.id=r.checker_id";

    private Revision row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Revision(
                r.getLong("id"),
                r.getString("kind"),
                r.getLong("target_id"),
                r.getObject("base_id", Long.class),
                r.getString("payload"),
                r.getString("reason"),
                r.getString("state"),
                r.getLong("maker_id"),
                r.getString("maker"),
                r.getObject("checker_id", Long.class),
                r.getString("checker"),
                r.getTimestamp("created_at").toInstant(),
                r.getString("review_reason"),
                r.getLong("version"));
    }

    private Revision current(String kind, long target) {
        return db
                .query(
                        SELECT + " where r.kind=? and r.target_id=? and r.state='APPROVED'",
                        this::row,
                        kind,
                        target)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Choice> choices() {
        access.require("accounting.setup");
        return accountChoices();
    }

    private List<Choice> accountChoices() {
        var rows =
                db.query(
                        "select id,code,name from gl_accounts where depth=4 and"
                            + " classification='EQUITY' order by code,id limit 1001",
                        (r, n) -> new Choice(r.getLong(1), r.getString(2), r.getString(3)));
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return List.copyOf(rows);
    }

    @Transactional(readOnly = true)
    public Form form() {
        access.require("accounting.setup");
        var f = new Form();
        var v = current("SETUP", 0);
        if (v != null) {
            f.setBaseId(v.id());
            var byId = new HashMap<Long, String>();
            accountChoices().forEach(c -> byId.put(c.id(), c.code()));
            for (var c : json.readValue(v.payload(), Setup.class).components()) {
                var row = new ComponentForm();
                row.setName(c.name());
                row.setNameSw(c.nameSw());
                row.setRetained(c.retained());
                row.setAccounts(
                        c.accounts().stream()
                                .map(byId::get)
                                .collect(java.util.stream.Collectors.joining(", ")));
                f.getComponents().add(row);
            }
        }
        return f;
    }

    @Transactional(readOnly = true)
    public MovementForm movementForm(long journal) {
        access.require("accounting.setup");
        validateJournal(journal, Category.UNCLASSIFIED);
        var f = new MovementForm();
        var v = current("JOURNAL", journal);
        if (v != null) {
            f.setBaseId(v.id());
            f.setCategory(category(v.payload()));
        }
        return f;
    }

    @Transactional(readOnly = true)
    public RegisterPage<Revision> history(RegisterQuery q) {
        var a = access.require("accounting.report");
        q.validate();
        var rows =
                db.query(
                        SELECT
                                + " where (r.state in ('APPROVED','RETIRED') or r.maker_id=? or ?)"
                                + " and lower(r.reason) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "r.id",
                                                "maker",
                                                "maker",
                                                "reason",
                                                "r.reason",
                                                "state",
                                                "r.state"))
                                + " limit ? offset ?",
                        this::row,
                        a.id(),
                        a.has("accounting.review"),
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset());
        return RegisterPage.of(rows, q);
    }

    @Transactional(readOnly = true)
    public Revision revision(long id) {
        var a = access.require("accounting.report");
        return db
                .query(
                        SELECT
                                + " where r.id=? and (r.state in ('APPROVED','RETIRED') or"
                                + " r.maker_id=? or ?)",
                        this::row,
                        id,
                        a.id(),
                        a.has("accounting.review"))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new org.springframework.security.access.AccessDeniedException(
                                        "Access denied"));
    }

    public record Details(
            Revision revision, List<Component> components, String category, Set<String> actions) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Details details(long id) {
        var v = revision(id);
        return new Details(
                v,
                v.kind().equals("SETUP")
                        ? json.readValue(v.payload(), Setup.class).components()
                        : List.of(),
                v.kind().equals("JOURNAL") ? category(v.payload()).name() : "",
                actions(id));
    }

    @Transactional(readOnly = true)
    public Set<String> actions(long id) {
        var a = access.require("accounting.report");
        var v = revision(id);
        if (!v.state().equals("PENDING")) return Set.of();
        if (a.id() == v.makerId()) return a.has("accounting.setup") ? Set.of("cancel") : Set.of();
        return a.has("accounting.review") ? Set.of("approve", "reject") : Set.of();
    }

    @Transactional(readOnly = true)
    public RegisterPage<Journal> journals(RegisterQuery q) {
        access.require("accounting.report");
        q.validate();
        return RegisterPage.of(
                db.query(
                        "select"
                            + " j.id,j.effective_date,j.reference,j.description,coalesce(r.payload->>'category','UNCLASSIFIED')"
                            + " category,r.id revision_id from gl_journals j left join"
                            + " gl_equity_reporting r on r.kind='JOURNAL' and r.target_id=j.id and"
                            + " r.state='APPROVED' where j.state='POSTED' and j.kind<>'OPENING' and"
                            + " j.reverses_id is null and exists(select 1 from gl_lines l join"
                            + " gl_accounts a on a.id=l.account_id where l.journal_id=j.id and"
                            + " a.classification='EQUITY') and (?::date is null or"
                            + " j.effective_date>=?::date) and (?::date is null or"
                            + " j.effective_date<=?::date) and lower(j.reference||'"
                            + " '||j.description) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "j.id",
                                                "date",
                                                "j.effective_date",
                                                "reference",
                                                "j.reference",
                                                "category",
                                                "category"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Journal(
                                        r.getLong(1),
                                        r.getObject(2, java.time.LocalDate.class),
                                        r.getString(3),
                                        r.getString(4),
                                        r.getString(5),
                                        r.getObject(6, Long.class)),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    private Setup setup(Form f) {
        var byCode = new HashMap<String, Long>();
        accountChoices().forEach(c -> byCode.put(c.code(), c.id()));
        var ids = new HashSet<Long>();
        var names = new HashSet<String>();
        var result = new ArrayList<Component>();
        int retained = 0;
        for (var c : f.getComponents()) {
            String name = text(c.getName(), 100), sw = text(c.getNameSw(), 100);
            if (name.isBlank() || !names.add(name.toLowerCase(Locale.ROOT))) throw invalid();
            var accounts = new ArrayList<Long>();
            for (String code : c.getAccounts().split(",", -1)) {
                Long id = byCode.get(code.strip());
                if (id == null || !ids.add(id))
                    throw new IllegalArgumentException("gl.equity.accountError");
                accounts.add(id);
            }
            if (c.isRetained()) retained++;
            result.add(new Component(name, sw, accounts, c.isRetained()));
        }
        if (retained > 1) throw invalid();
        return new Setup(result);
    }

    @Transactional
    public long propose(@jakarta.validation.Valid Form f) {
        access.require("accounting.setup");
        return propose(
                "SETUP",
                0,
                f.getBaseId(),
                json.writeValueAsString(setup(f)),
                f.getReason(),
                f.getRequestKey());
    }

    @Transactional
    public long classify(long journal, @jakarta.validation.Valid MovementForm f) {
        access.require("accounting.setup");
        validateJournal(journal, f.getCategory());
        return propose(
                "JOURNAL",
                journal,
                f.getBaseId(),
                json.writeValueAsString(Map.of("category", f.getCategory())),
                f.getReason(),
                f.getRequestKey());
    }

    private void lock(String kind, long target) {
        db.queryForObject(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                Object.class,
                "fcms-equity/" + kind + "/" + target);
    }

    private long propose(
            String kind, long target, Long base, String payload, String reason, UUID key) {
        var a = access.require("accounting.setup");
        reason = text(reason, 500);
        if (reason.isBlank() || key == null) throw invalid();
        lock(kind, target);
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    json.writeValueAsBytes(
                                                            List.of(
                                                                    kind,
                                                                    target,
                                                                    base == null ? 0 : base,
                                                                    payload,
                                                                    reason))));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var old =
                db.queryForList(
                        "select id,payload_hash from gl_equity_reporting where maker_id=? and"
                            + " request_key=?",
                        a.id(),
                        key);
        if (!old.isEmpty()) {
            if (!hash.equals(old.getFirst().get("payload_hash")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) old.getFirst().get("id")).longValue();
        }
        var current = current(kind, target);
        if (!Objects.equals(base, current == null ? null : current.id()))
            throw new OptimisticLockingFailureException("stale");
        if (Boolean.TRUE.equals(
                db.queryForObject(
                        "select exists(select 1 from gl_equity_reporting where kind=? and"
                            + " target_id=? and state='PENDING')",
                        Boolean.class,
                        kind,
                        target))) throw new IllegalArgumentException("error.conflict");
        long id =
                db.queryForObject(
                        "insert into"
                            + " gl_equity_reporting(kind,target_id,base_id,payload,reason,maker_id,request_key,payload_hash)"
                            + " values (?,?,?,?::jsonb,?,?,?,?) returning id",
                        Long.class,
                        kind,
                        target,
                        base,
                        payload,
                        reason,
                        a.id(),
                        key,
                        hash);
        audit.record(a.id(), "accounting.equity.propose", "gl_equity_reporting", id, reason);
        return id;
    }

    @Transactional
    public void decide(long id, @jakarta.validation.Valid ReportingDecisionForm f) {
        var a =
                access.require(
                        "cancel".equals(f.getAction()) ? "accounting.setup" : "accounting.review");
        var v = revision(id);
        lock(v.kind(), v.targetId());
        db.queryForList("select id from gl_equity_reporting where id=? for update", Long.class, id);
        v = revision(id);
        if (v.version() != f.getVersion()) throw new OptimisticLockingFailureException("stale");
        if (!actions(id).contains(f.getAction()))
            throw new IllegalArgumentException("gl.reporting.independent");
        String reason = text(f.getReason(), 500);
        if (reason.isBlank()) throw invalid();
        String next =
                switch (f.getAction()) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    case "cancel" -> "CANCELLED";
                    default -> throw invalid();
                };
        if (next.equals("APPROVED")) {
            if (v.kind().equals("JOURNAL")) validateJournal(v.targetId(), category(v.payload()));
            else validateSetup(json.readValue(v.payload(), Setup.class));
            var base = current(v.kind(), v.targetId());
            if (!Objects.equals(v.baseId(), base == null ? null : base.id()))
                throw new OptimisticLockingFailureException("stale");
            if (base != null) {
                if (db.update(
                                "update gl_equity_reporting set"
                                    + " state='RETIRED',retired_by=?,retired_at=current_timestamp,replaced_by=?,version=version+1"
                                    + " where id=? and state='APPROVED' and version=?",
                                a.id(),
                                id,
                                base.id(),
                                base.version())
                        != 1) throw new OptimisticLockingFailureException("stale");
                audit.record(
                        a.id(),
                        "accounting.equity.retire",
                        "gl_equity_reporting",
                        base.id(),
                        "Replacement " + id);
            }
        }
        if (db.update(
                        "update gl_equity_reporting set"
                            + " state=?,checker_id=?,reviewed_at=current_timestamp,review_reason=?,version=version+1"
                            + " where id=? and version=? and state='PENDING'",
                        next,
                        a.id(),
                        reason,
                        id,
                        f.getVersion())
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                a.id(), "accounting.equity." + f.getAction(), "gl_equity_reporting", id, reason);
    }

    private void validateSetup(Setup s) {
        var ids = new HashSet<Long>();
        accountChoices().forEach(c -> ids.add(c.id()));
        if (s.components().stream()
                .flatMap(c -> c.accounts().stream())
                .anyMatch(id -> !ids.contains(id)))
            throw new IllegalArgumentException("gl.equity.accountError");
    }

    private void validateJournal(long journal, Category category) {
        var rows =
                db.query(
                        "select a.classification,l.debit-l.credit signed from gl_journals j join"
                            + " gl_lines l on l.journal_id=j.id join gl_accounts a on"
                            + " a.id=l.account_id where j.id=? and j.state='POSTED' and"
                            + " j.kind<>'OPENING' and j.reverses_id is null order by l.id limit 51",
                        (r, n) -> Map.entry(r.getString(1), r.getBigDecimal(2)),
                        journal);
        long eq = rows.stream().filter(r -> r.getKey().equals("EQUITY")).count();
        if (rows.size() > 50 || eq == 0) throw invalid();
        if (Set.of(Category.CONTRIBUTION, Category.DISTRIBUTION, Category.OCI).contains(category)
                && rows.stream().anyMatch(r -> Set.of("INCOME", "EXPENSE").contains(r.getKey())))
            throw invalid();
        if (category == Category.TRANSFER
                && (eq != rows.size()
                        || rows.stream()
                                        .map(Map.Entry::getValue)
                                        .reduce(
                                                java.math.BigDecimal.ZERO,
                                                java.math.BigDecimal::add)
                                        .signum()
                                != 0)) throw invalid();
        if (category == Category.EARNINGS_TRANSFER
                && (eq == rows.size()
                        || rows.stream()
                                .anyMatch(
                                        r ->
                                                !Set.of("EQUITY", "INCOME", "EXPENSE")
                                                        .contains(r.getKey())))) throw invalid();
    }

    static Category category(String payload) {
        return Category.valueOf(
                JsonMapper.builder().build().readTree(payload).get("category").asText());
    }

    private static String text(String value, int max) {
        if (value == null
                || value.length() > max
                || value.chars().anyMatch(c -> Character.isISOControl(c))) throw invalid();
        return value.strip();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("error.invalid");
    }

    @Transactional(readOnly = true)
    public Setup effective() {
        access.require("accounting.report");
        var current = current("SETUP", 0);
        return current == null ? Setup.empty() : json.readValue(current.payload(), Setup.class);
    }

    @Transactional(readOnly = true)
    public List<Long> effectiveRevisionIds(java.time.LocalDate from, java.time.LocalDate to) {
        access.require("accounting.report");
        return db.queryForList(
                "select r.id from gl_equity_reporting r where r.state='APPROVED' and"
                    + " (r.kind='SETUP' or exists(select 1 from gl_journals j where"
                    + " coalesce(j.reverses_id,j.id)=r.target_id and j.state='POSTED' and"
                    + " j.effective_date between ? and ?)) order by r.id",
                Long.class,
                from,
                to);
    }
}
