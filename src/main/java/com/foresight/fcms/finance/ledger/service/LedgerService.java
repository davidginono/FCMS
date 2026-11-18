package com.foresight.fcms.finance.ledger.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.finance.ledger.dto.*;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.*;

@org.springframework.validation.annotation.Validated
@Service
public class LedgerService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final ClientDirectoryService clients;

    public LedgerService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            ClientDirectoryService clients) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.clients = clients;
    }

    private static final String FILTER =
            " where e.kind=? and lower(e.description||' '||e.reference||' '||e.category) like ?"
                + " escape '!' and (?::date is null or e.transaction_date>=?::date) and (?::date is"
                + " null or e.transaction_date<=?::date) and (?='' or e.category=?)";

    private String selection(LedgerKind kind) {
        // Apply search/date/category filters after the chronological balance, so earlier movements
        // remain included.
        String source =
                kind == LedgerKind.PETTY
                        ? "(select l.*,sum(amount_delta) over(order by transaction_date,id rows"
                              + " unbounded preceding) running_balance from ledger_entries l where"
                              + " kind='PETTY')"
                        : "ledger_entries";
        return "select e.*,"
                + (kind == LedgerKind.PETTY ? "e.running_balance" : "e.balance_after")
                + " as display_balance,u.first_name||' '||u.last_name as"
                + " actor,coalesce(c.saccos_name,'') as client,(select r.id from ledger_entries r"
                + " where r.reversal_of=e.id) as reversed_by from "
                + source
                + " e join iam_staff_directory u on u.id=e.actor_id left join"
                + " client_saccos_directory c on c.id=e.client_id";
    }

    private List<Object> parameters(LedgerKind kind, RegisterQuery q) {
        q.validate();
        var from = q.getFrom();
        var to = q.getTo();
        String category = "";
        if (q instanceof LedgerQuery filter) {
            category = kind == LedgerKind.BULK ? filter.getCategory() : "";
            if (!filter.getYear().isEmpty()) {
                var first = java.time.LocalDate.of(Integer.parseInt(filter.getYear()), 1, 1);
                var last = first.plusYears(1).minusDays(1);
                from = from == null || from.isBefore(first) ? first : from;
                to = to == null || to.isAfter(last) ? last : to;
            }
        }
        return new ArrayList<>(
                Arrays.asList(kind.name(), q.pattern(), from, from, to, to, category, category));
    }

    private LedgerView row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new LedgerView(
                r.getLong("id"),
                r.getString("kind"),
                r.getObject("transaction_date", java.time.LocalDate.class),
                r.getString("description"),
                r.getString("category"),
                r.getString("reference"),
                r.getString("payment_mode"),
                r.getBigDecimal("amount_delta"),
                r.getLong("units_delta"),
                r.getBigDecimal("display_balance"),
                r.getLong("units_after"),
                r.getLong("actor_id"),
                r.getString("actor"),
                r.getObject("reversal_of", Long.class),
                r.getObject("reversed_by", Long.class),
                r.getString("client"));
    }

    @Transactional(readOnly = true)
    public RegisterPage<LedgerView> list(LedgerKind k, RegisterQuery q) {
        access.require("finance.read");
        var args = parameters(k, q);
        args.add(q.getSize() + 1);
        args.add(q.offset());
        String order =
                q.order(
                        Map.ofEntries(
                                Map.entry("id", "e.id"),
                                Map.entry("date", "e.transaction_date"),
                                Map.entry("description", "e.description"),
                                Map.entry("category", "e.category"),
                                Map.entry("reference", "e.reference"),
                                Map.entry("amount", "e.amount_delta"),
                                Map.entry("amountIn", "greatest(e.amount_delta,0)"),
                                Map.entry("amountOut", "greatest(-e.amount_delta,0)"),
                                Map.entry("units", "e.units_delta"),
                                Map.entry("unitsIn", "greatest(e.units_delta,0)"),
                                Map.entry("unitsOut", "greatest(-e.units_delta,0)"),
                                Map.entry("unitsBefore", "e.units_after-e.units_delta"),
                                Map.entry("unitsAfter", "e.units_after"),
                                Map.entry("balance", "display_balance"),
                                Map.entry("actor", "actor")));
        return RegisterPage.of(
                db.query(
                        selection(k) + FILTER + " order by " + order + " limit ? offset ?",
                        this::row,
                        args.toArray()),
                q);
    }

    public record Totals(BigDecimal amountIn, BigDecimal amountOut, long unitsIn, long unitsOut) {
        public BigDecimal profit() {
            return amountIn.subtract(amountOut);
        }
    }

    public record Workspace(
            RegisterPage<LedgerView> records,
            Balance balance,
            Totals totals,
            List<Integer> years) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Workspace workspace(LedgerKind kind, LedgerQuery q) {
        access.require("finance.read");
        var totals =
                db.queryForObject(
                        "select"
                            + " coalesce(sum(greatest(amount_delta,0)),0),coalesce(sum(greatest(-amount_delta,0)),0),coalesce(sum(greatest(units_delta,0)),0),coalesce(sum(greatest(-units_delta,0)),0)"
                            + " from ledger_entries e"
                                + FILTER,
                        (r, n) ->
                                new Totals(
                                        r.getBigDecimal(1),
                                        r.getBigDecimal(2),
                                        r.getLong(3),
                                        r.getLong(4)),
                        parameters(kind, q).toArray());
        var years =
                db.queryForList(
                        "select y from (select distinct extract(year from transaction_date)::int y"
                            + " from ledger_entries where kind=? union select ?::int) years order"
                            + " by y desc limit 102",
                        Integer.class,
                        kind.name(),
                        com.foresight.fcms.shared.time.ApplicationCalendar.today().getYear());
        return new Workspace(list(kind, q), balance(kind), totals, years);
    }

    @Transactional(readOnly = true)
    public LedgerView find(LedgerKind k, long id) {
        access.require("finance.read");
        return db
                .query(selection(k) + " where e.kind=? and e.id=?", this::row, k.name(), id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public Balance balance(LedgerKind k) {
        access.require("finance.read");
        return db.queryForObject(
                "select balance,units from ledger_accounts where kind=?",
                (r, n) -> new Balance(r.getBigDecimal(1), r.getLong(2)),
                k.name());
    }

    /** Actual signed movements in the selected calendar year, including linked reversals. */
    @Transactional(readOnly = true)
    public BigDecimal yearTotal(LedgerKind kind, int year) {
        access.require("finance.read");
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("error.invalid");
        var from = java.time.LocalDate.of(year, 1, 1);
        return db.queryForObject(
                "select coalesce(sum(amount_delta),0) from ledger_entries"
                        + " where kind=? and transaction_date>=? and transaction_date<?",
                BigDecimal.class,
                kind.name(),
                from,
                from.plusYears(1));
    }

    @Transactional
    public long post(LedgerKind k, @jakarta.validation.Valid LedgerForm f) {
        var a = access.require("finance.write");
        if (k == LedgerKind.BULK) {
            String operation = f.getOperation();
            if (operation.isEmpty()) {
                operation = f.getUnitDirection().equals("IN") ? "STOCK_PURCHASE" : "CLIENT_SALE";
                if (f.getAmount().signum() != 0
                        && !f.getDirection()
                                .equals(operation.equals("STOCK_PURCHASE") ? "OUT" : "IN"))
                    throw new IllegalArgumentException("error.invalid");
            }
            if (!Set.of("STOCK_PURCHASE", "CLIENT_SALE").contains(operation))
                throw new IllegalArgumentException("error.invalid");
            f.setDirection(operation.equals("STOCK_PURCHASE") ? "OUT" : "IN");
            f.setUnitDirection(operation.equals("STOCK_PURCHASE") ? "IN" : "OUT");
            f.setCategory(operation);
        } else if (k == LedgerKind.PETTY && !f.getOperation().isEmpty()) {
            if (!Set.of("DEPOSIT", "WITHDRAWAL").contains(f.getOperation()))
                throw new IllegalArgumentException("error.invalid");
            f.setDirection(f.getOperation().equals("DEPOSIT") ? "IN" : "OUT");
        }
        db.queryForList(
                "select pg_advisory_xact_lock_shared(hashtextextended('FCMS_FINANCE_CUTOVER',0))");
        if (f.getDate().isAfter(com.foresight.fcms.shared.time.ApplicationCalendar.today()))
            throw new FieldValidationException("error.invalid", "date");
        if (f.getAmount().signum() < 0
                || (f.getAmount().signum() == 0 && (k != LedgerKind.BULK || f.getUnits() == 0))
                || f.getAmount().scale() > 2) throw new FieldValidationException("error.invalid", "amount");
        if (!Set.of("IN", "OUT").contains(f.getDirection())
                || !Set.of("IN", "OUT").contains(f.getUnitDirection())
                || !Set.of("CASH", "BANK", "MOBILE", "CHEQUE").contains(f.getPaymentMode()))
            throw new IllegalArgumentException("error.invalid");
        if (k == LedgerKind.INCOME && !f.getDirection().equals("IN"))
            throw new IllegalArgumentException("error.invalid");
        if (k != LedgerKind.BULK && f.getUnits() != 0)
            throw new IllegalArgumentException("error.invalid");
        if (f.getUnits() < 0 || f.getUnits() > 1000000000L)
            throw new IllegalArgumentException("error.invalid");
        if (f.getClientId() != null) clients.requireSaccos(f.getClientId());
        BigDecimal delta = f.getDirection().equals("IN") ? f.getAmount() : f.getAmount().negate();
        long units = f.getUnitDirection().equals("IN") ? f.getUnits() : -f.getUnits();
        String hash = fingerprint(k, f, delta, units);
        var locked =
                db.queryForObject(
                        "select balance,units from ledger_accounts where kind=? for update",
                        (r, n) -> new Balance(r.getBigDecimal(1), r.getLong(2)),
                        k.name());
        var old =
                db.queryForList(
                        "select id,payload_hash,(transaction_date=? and description=? and"
                            + " category=? and reference=? and payment_mode=? and amount_delta=?"
                            + " and units_delta=? and client_id is not distinct from ?::bigint)"
                            + " same_payload from ledger_entries where actor_id=? and kind=? and"
                            + " request_key=?",
                        f.getDate(),
                        f.getDescription(),
                        f.getCategory(),
                        f.getReference(),
                        f.getPaymentMode(),
                        delta,
                        units,
                        f.getClientId(),
                        a.id(),
                        k.name(),
                        f.getRequestKey());
        if (!old.isEmpty()) {
            if (!hash.equals(old.getFirst().get("payload_hash"))
                    || !Boolean.TRUE.equals(old.getFirst().get("same_payload")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) old.getFirst().get("id")).longValue();
        }
        if (db.queryForObject(
                        "select count(*) from finance_gl_cutover where ?<starts_on",
                        Integer.class,
                        f.getDate())
                != 0) throw new FieldValidationException("error.cutover", "date");
        BigDecimal next = locked.amount().add(delta);
        long nextUnits = Math.addExact(locked.units(), units);
        checkBalance(k, next, nextUnits);
        long id = insert(k, f, a.id(), delta, units, next, nextUnits, hash, null);
        db.update(
                "update ledger_accounts set balance=?,units=?,version=version+1 where kind=?",
                next,
                nextUnits,
                k.name());
        audit.record(a.id(), "finance.post", "ledger_entries", id, k.name());
        return id;
    }

    @Transactional
    public long reverse(LedgerKind k, long id, UUID request, String reason) {
        var a = access.require("finance.reverse");
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("error.invalid");
        var current =
                db.queryForObject(
                        "select balance,units from ledger_accounts where kind=? for update",
                        (r, n) -> new Balance(r.getBigDecimal(1), r.getLong(2)),
                        k.name());
        var original = find(k, id);
        if (original.actorId() == a.id() || original.reversalOf() != null)
            throw new IllegalArgumentException("error.invalid");
        if (original.reversedBy() != null) return original.reversedBy();
        var delta = original.amount().negate();
        var units = -original.units();
        var next = current.amount().add(delta);
        long nextUnits = Math.addExact(current.units(), units);
        checkBalance(k, next, nextUnits);
        var f = new LedgerForm();
        f.setDate(com.foresight.fcms.shared.time.ApplicationCalendar.today());
        f.setDescription(reason);
        f.setCategory(original.category());
        f.setReference("REV-" + id);
        f.setPaymentMode(original.paymentMode());
        f.setRequestKey(request);
        long reversal = insert(k, f, a.id(), delta, units, next, nextUnits, "reversal-" + id, id);
        db.update(
                "update ledger_accounts set balance=?,units=?,version=version+1 where kind=?",
                next,
                nextUnits,
                k.name());
        audit.record(a.id(), "finance.reverse", "ledger_entries", id, "Reversal " + reversal);
        return reversal;
    }

    private long insert(
            LedgerKind k,
            @jakarta.validation.Valid LedgerForm f,
            long actor,
            BigDecimal delta,
            long units,
            BigDecimal balance,
            long unitBalance,
            String hash,
            Long reversed) {
        return db.queryForObject(
                "insert into"
                    + " ledger_entries(kind,transaction_date,description,category,reference,payment_mode,amount_delta,units_delta,client_id,actor_id,request_key,payload_hash,reversal_of,balance_after,units_after)"
                    + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id",
                Long.class,
                k.name(),
                f.getDate(),
                f.getDescription(),
                f.getCategory(),
                f.getReference(),
                f.getPaymentMode(),
                delta,
                units,
                f.getClientId(),
                actor,
                f.getRequestKey(),
                hash,
                reversed,
                balance,
                unitBalance);
    }

    public static void checkBalance(LedgerKind k, BigDecimal balance, long units) {
        if ((k == LedgerKind.PETTY && balance.signum() < 0) || units < 0)
            throw new IllegalArgumentException("error.insufficient");
    }

    private static String fingerprint(
            LedgerKind k, @jakarta.validation.Valid LedgerForm f, BigDecimal amount, long units) {
        try {
            String payload =
                    String.join(
                            "|",
                            k.name(),
                            f.getDate().toString(),
                            f.getDescription(),
                            f.getCategory(),
                            f.getReference(),
                            f.getPaymentMode(),
                            amount.setScale(2).toPlainString(),
                            Long.toString(units),
                            String.valueOf(f.getClientId()));
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            payload.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public List<Reconciliation> reconcile() {
        access.require("finance.read");
        return db.query(
                "select a.kind,a.balance,a.units,coalesce(e.amount,0),coalesce(e.units,0)"
                        + " from ledger_accounts a left join (select kind,sum(amount_delta) amount,"
                        + " sum(units_delta) units from ledger_entries group by kind) e"
                        + " on e.kind=a.kind order by a.kind",
                (r, n) ->
                        new Reconciliation(
                                LedgerKind.valueOf(r.getString(1)),
                                r.getBigDecimal(2),
                                r.getBigDecimal(4),
                                r.getLong(3),
                                r.getLong(5)));
    }

    public record Reconciliation(
            LedgerKind kind,
            BigDecimal storedAmount,
            BigDecimal postedAmount,
            long storedUnits,
            long postedUnits) {
        public boolean matches() {
            return storedAmount.compareTo(postedAmount) == 0 && storedUnits == postedUnits;
        }
    }

    public record Balance(BigDecimal amount, long units) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public java.util.List<LedgerView> report(LedgerKind kind, RegisterQuery query) {
        access.require("finance.read");
        int original = query.getPage();
        int size = query.getSize();
        try {
            query.setSize(100);
            var rows = new java.util.ArrayList<LedgerView>();
            for (int p = 0; p <= 10; p++) {
                query.setPage(p);
                var page = list(kind, query);
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerReport reportSnapshot(LedgerKind kind, RegisterQuery query) {
        var rows = report(kind, query);
        var current = balance(kind);
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        long unitsIn = 0, unitsOut = 0;
        for (var row : rows) {
            if (row.amount().signum() > 0) in = in.add(row.amount());
            else out = out.subtract(row.amount());
            if (row.units() > 0) unitsIn = Math.addExact(unitsIn, row.units());
            else unitsOut = Math.subtractExact(unitsOut, row.units());
        }
        return new LedgerReport(
                rows,
                in,
                out,
                unitsIn,
                unitsOut,
                current.amount(),
                current.units(),
                java.time.Instant.now());
    }
}
