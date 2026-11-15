package com.foresight.fcms.accounting.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.auth.service.AuthRateLimitService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class StatementService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AccountingService accounting;
    private final SourceCoverageService coverage;
    private final AuthRateLimitService requestLimits;
    private final EquityReportingService equityReporting;

    public StatementService(
            JdbcTemplate db,
            AccessService access,
            AccountingService accounting,
            SourceCoverageService coverage,
            AuthRateLimitService requestLimits,
            EquityReportingService equityReporting) {
        this.db = db;
        this.access = access;
        this.accounting = accounting;
        this.coverage = coverage;
        this.requestLimits = requestLimits;
        this.equityReporting = equityReporting;
    }

    // Reviewed opening journals are the start-of-day balance, never period cash flow or owner
    // contributions.
    private static final String BALANCES =
            """
            with balances as (
             select l.account_id,
              coalesce(sum(l.debit-l.credit) filter(where j.effective_date<? or (j.kind='OPENING' and j.effective_date<=?)),0) opening,
              coalesce(sum(l.debit) filter(where j.effective_date>=? and j.kind<>'OPENING'),0) debit,
              coalesce(sum(l.credit) filter(where j.effective_date>=? and j.kind<>'OPENING'),0) credit,
              coalesce(sum(l.debit-l.credit),0) closing
             from gl_journals j join gl_lines l on l.journal_id=j.id
             where j.state='POSTED' and j.effective_date<=? group by l.account_id
            )
            """;

    private static final Map<String, String> TRIAL_ORDER =
            Map.ofEntries(
                    Map.entry("id", "a.id"),
                    Map.entry("code", "a.code"),
                    Map.entry("name", "a.name"),
                    Map.entry("classification", "a.classification"),
                    Map.entry("opening", "coalesce(t.opening,0)"),
                    Map.entry("debit", "coalesce(t.debit,0)"),
                    Map.entry("credit", "coalesce(t.credit,0)"),
                    Map.entry("netChange", "coalesce(t.debit,0)-coalesce(t.credit,0)"),
                    Map.entry("closing", "coalesce(t.closing,0)"),
                    Map.entry("closingDebit", "greatest(coalesce(t.closing,0),0)"),
                    Map.entry("closingCredit", "greatest(-coalesce(t.closing,0),0)"));

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Statements statements(LocalDate from, LocalDate to) {
        validate(from, to);
        var rows =
                db.query(
                        BALANCES
                                + "select"
                                + " a.classification,coalesce(sum(b.opening),0),coalesce(sum(b.debit),0),coalesce(sum(b.credit),0),coalesce(sum(b.closing),0)"
                                + " from gl_accounts a left join balances b on b.account_id=a.id"
                                + " where a.depth=4 group by a.classification",
                        (r, n) ->
                                new Classification(
                                        r.getString(1),
                                        r.getBigDecimal(2),
                                        r.getBigDecimal(3),
                                        r.getBigDecimal(4),
                                        r.getBigDecimal(5)),
                        from,
                        from,
                        from,
                        from,
                        to);
        var classes = new HashMap<String, Classification>();
        rows.forEach(r -> classes.put(r.name(), r));
        var order = List.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE");
        for (String c : order)
            classes.putIfAbsent(
                    c,
                    new Classification(
                            c, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        var transferred = new HashMap<String, BigDecimal>();
        db.query(
                "select a.classification,coalesce(sum(l.credit-l.debit),0) amount from gl_journals"
                    + " j join gl_lines l on l.journal_id=j.id join gl_accounts a on"
                    + " a.id=l.account_id join gl_equity_reporting r on r.kind='JOURNAL' and"
                    + " r.target_id=coalesce(j.reverses_id,j.id) and r.state='APPROVED' and"
                    + " r.payload->>'category'='EARNINGS_TRANSFER' where j.state='POSTED' and"
                    + " j.kind<>'OPENING' and j.effective_date between ? and ? group by"
                    + " a.classification",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        r -> transferred.put(r.getString(1), r.getBigDecimal(2)),
                from,
                to);
        BigDecimal income = classes.get("INCOME").credit().subtract(classes.get("INCOME").debit()),
                expenses = classes.get("EXPENSE").debit().subtract(classes.get("EXPENSE").credit()),
                profit;
        income = income.subtract(transferred.getOrDefault("INCOME", BigDecimal.ZERO));
        expenses = expenses.add(transferred.getOrDefault("EXPENSE", BigDecimal.ZERO));
        profit = income.subtract(expenses);
        BigDecimal assets = classes.get("ASSET").closing(),
                liabilities = classes.get("LIABILITY").closing().negate(),
                equity = classes.get("EQUITY").closing().negate(),
                accumulated =
                        classes.get("INCOME")
                                .closing()
                                .negate()
                                .subtract(classes.get("EXPENSE").closing());
        BigDecimal
                equityOpening =
                        classes.get("EQUITY")
                                .opening()
                                .negate()
                                .subtract(classes.get("INCOME").opening())
                                .subtract(classes.get("EXPENSE").opening()),
                equityMovement =
                        classes.get("EQUITY")
                                .credit()
                                .subtract(classes.get("EQUITY").debit())
                                .subtract(transferred.getOrDefault("EQUITY", BigDecimal.ZERO));
        var cash =
                db.queryForMap(
                        "select coalesce(sum(l.debit-l.credit) filter(where j.effective_date<? or"
                                + " (j.kind='OPENING' and j.effective_date<=?)),0)"
                                + " opening,coalesce(sum(l.debit-l.credit),0)"
                                + " closing,coalesce(sum(l.debit-l.credit) filter(where"
                                + " j.effective_date>=? and j.kind<>'OPENING' and"
                                + " j.cash_flow='OPERATING'),0)"
                                + " operating,coalesce(sum(l.debit-l.credit) filter(where"
                                + " j.effective_date>=? and j.kind<>'OPENING' and"
                                + " j.cash_flow='INVESTING'),0)"
                                + " investing,coalesce(sum(l.debit-l.credit) filter(where"
                                + " j.effective_date>=? and j.kind<>'OPENING' and"
                                + " j.cash_flow='FINANCING'),0) financing from gl_journals j join"
                                + " gl_lines l on l.journal_id=j.id join gl_accounts a on"
                                + " a.id=l.account_id where j.state='POSTED' and a.cash_account and"
                                + " j.effective_date<=?",
                        from,
                        from,
                        from,
                        from,
                        from,
                        to);
        long unposted =
                db.queryForObject(
                        "select count(*) from gl_journals where state in ('DRAFT','SUBMITTED') and"
                                + " effective_date between ? and ?",
                        Long.class,
                        from,
                        to);
        var book = accounting.books().orElseThrow();
        return new Statements(
                from,
                to,
                book.policyReference(),
                order.stream().map(classes::get).toList(),
                income,
                expenses,
                profit,
                assets,
                liabilities,
                equity,
                accumulated,
                equityOpening,
                equityMovement,
                (BigDecimal) cash.get("opening"),
                (BigDecimal) cash.get("operating"),
                (BigDecimal) cash.get("investing"),
                (BigDecimal) cash.get("financing"),
                (BigDecimal) cash.get("closing"),
                unposted,
                coverage.through(to));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RegisterPage<TrialRow> trial(RegisterQuery q) {
        validate(q.getFrom(), q.getTo());
        q.validate();
        return RegisterPage.of(
                db.query(
                        BALANCES
                                + "select"
                                + " a.id,a.code,a.name,a.classification,coalesce(t.opening,0),coalesce(t.debit,0),coalesce(t.credit,0),greatest(coalesce(t.closing,0),0),greatest(-coalesce(t.closing,0),0)"
                                + " from gl_accounts a left join balances t on t.account_id=a.id"
                                + " where a.depth=4 and lower(a.code||' '||a.name) like ? escape"
                                + " '!' order by "
                                + q.order(TRIAL_ORDER)
                                + " limit ? offset ?",
                        (r, n) ->
                                new TrialRow(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getString(4),
                                        r.getBigDecimal(5),
                                        r.getBigDecimal(6),
                                        r.getBigDecimal(7),
                                        r.getBigDecimal(8),
                                        r.getBigDecimal(9)),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    private static final Map<String, String> LEDGER_ORDER =
            Map.of(
                    "id",
                    "id",
                    "date",
                    "ledger_sequence",
                    "reference",
                    "reference",
                    "kind",
                    "kind",
                    "description",
                    "description",
                    "debit",
                    "debit",
                    "credit",
                    "credit",
                    "balance",
                    "running_movement");

    // Filtering and pagination must follow this window: hidden movements still affect balances.
    private static final String LEDGER_MOVEMENTS =
            """
            with movement as (
             select l.id,j.id journal_id,j.reference,j.effective_date,j.kind,
              coalesce(nullif(l.description,''),j.description) description,l.debit,l.credit,
              sum(l.debit-l.credit) over(order by j.effective_date,j.id,l.id rows between unbounded preceding and current row) running_movement,
              row_number() over(order by j.effective_date,j.id,l.id) ledger_sequence
             from gl_lines l join gl_journals j on j.id=l.journal_id
             where l.account_id=? and j.state='POSTED' and j.kind<>'OPENING'
              and j.effective_date between ? and ?
            )
            """;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountLedger ledger(long account, RegisterQuery q) {
        return readLedger(account, q, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountLedger ledgerExport(long account, RegisterQuery q) {
        return readLedger(account, q, true);
    }

    private AccountLedger readLedger(long account, RegisterQuery q, boolean full) {
        var book = validate(q.getFrom(), q.getTo());
        q.validate();
        var a = accounting.account(account);
        if (!requestLimits.allow(
                (full ? "gl-ledger-export:" : "gl-ledger-read:")
                        + access.require("accounting.report").id(),
                full ? 60 : 600)) throw new ReportLimitException();
        var amounts =
                db.queryForMap(
                        """
                        select coalesce(sum(l.debit-l.credit) filter(where j.effective_date<? or (j.kind='OPENING' and j.effective_date<=?)),0) opening,
                         coalesce(sum(l.debit) filter(where j.effective_date>=? and j.kind<>'OPENING'),0) debit,
                         coalesce(sum(l.credit) filter(where j.effective_date>=? and j.kind<>'OPENING'),0) credit,
                         coalesce(sum(l.debit-l.credit),0) closing
                        from gl_lines l join gl_journals j on j.id=l.journal_id
                        where l.account_id=? and j.state='POSTED' and j.effective_date<=?
                        """,
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        account,
                        q.getTo());
        long count =
                db.queryForObject(
                        """
                        select count(*) from gl_lines l join gl_journals j on j.id=l.journal_id
                        where l.account_id=? and j.state='POSTED' and j.kind<>'OPENING'
                         and j.effective_date between ? and ?
                         and lower(j.reference||' '||coalesce(nullif(l.description,''),j.description)) like ? escape '!'
                        """,
                        Long.class,
                        account,
                        q.getFrom(),
                        q.getTo(),
                        q.pattern());
        if (full && count > 5000) throw new LedgerScopeLimitException();
        if (!full) {
            int last = (int) Math.min(10000, Math.max(0, (count - 1) / q.getSize()));
            q.setPage(Math.min(q.getPage(), last));
            if (q.getPage() == 10000 && count > ((long) q.getPage() + 1) * q.getSize())
                throw new LedgerScopeLimitException();
        }
        var opening = (BigDecimal) amounts.get("opening");
        var rows =
                db.query(
                        LEDGER_MOVEMENTS
                                + """
                                select id,journal_id,reference,effective_date,kind,description,debit,credit,running_movement
                                from movement where lower(reference||' '||description) like ? escape '!' order by
                                """
                                + q.order(LEDGER_ORDER)
                                + " limit ? offset ?",
                        (r, n) ->
                                new Activity(
                                        r.getLong(1),
                                        r.getLong(2),
                                        r.getString(3),
                                        r.getObject(4, LocalDate.class),
                                        r.getString(5),
                                        r.getString(6),
                                        r.getBigDecimal(7),
                                        r.getBigDecimal(8),
                                        opening.add(r.getBigDecimal(9))),
                        account,
                        q.getFrom(),
                        q.getTo(),
                        q.pattern(),
                        full ? 5001 : q.getSize() + 1,
                        full ? 0 : q.offset());
        if (full && rows.size() > 5000) throw new LedgerScopeLimitException();
        var records =
                full
                        ? new RegisterPage<>(List.copyOf(rows), 0, 5000, false)
                        : RegisterPage.of(rows, q);
        return new AccountLedger(
                a,
                records,
                opening,
                (BigDecimal) amounts.get("debit"),
                (BigDecimal) amounts.get("credit"),
                (BigDecimal) amounts.get("closing"),
                coverage.through(q.getTo()),
                q.getFrom(),
                q.getTo(),
                book.policyReference(),
                java.time.Instant.now(),
                count,
                q.getSearch());
    }

    /**
     * Validates the optional report return path before the canonical journal details are loaded.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerContext ledgerContext(long account, long journal, LocalDate from, LocalDate to) {
        validate(from, to);
        accounting.account(account);
        boolean belongs =
                Boolean.TRUE.equals(
                        db.queryForObject(
                                """
                                select exists(select 1 from gl_lines l join gl_journals j on j.id=l.journal_id
                                 where l.account_id=? and j.id=? and j.state='POSTED' and j.kind<>'OPENING'
                                  and j.effective_date between ? and ?)
                                """,
                                Boolean.class,
                                account,
                                journal,
                                from,
                                to));
        if (!belongs)
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return new LedgerContext(account, from, to);
    }

    public record LedgerContext(long accountId, LocalDate from, LocalDate to) {}

    public static final class ReportLimitException extends RuntimeException {}

    public static final class LedgerScopeLimitException extends IllegalArgumentException {
        public LedgerScopeLimitException() {
            super("error.narrowReport");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Trial trialWorkspace(RegisterQuery q) {
        validate(q.getFrom(), q.getTo());
        q.validate();
        var totals =
                db.queryForObject(
                        BALANCES
                                + "select"
                                + " count(*),coalesce(sum(t.opening),0),coalesce(sum(t.debit),0),"
                                + "coalesce(sum(t.credit),0),coalesce(sum(greatest(coalesce(t.closing,0),0)),0),coalesce(sum(greatest(-coalesce(t.closing,0),0)),0)"
                                + " from gl_accounts a left join balances t on t.account_id=a.id"
                                + " where a.depth=4 and lower(a.code||' '||a.name) like ? escape"
                                + " '!'",
                        (r, n) ->
                                new TrialTotals(
                                        r.getLong(1),
                                        r.getBigDecimal(2),
                                        r.getBigDecimal(3),
                                        r.getBigDecimal(4),
                                        r.getBigDecimal(5),
                                        r.getBigDecimal(6)),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.pattern());
        int lastPage = (int) Math.min(10000, Math.max(0, (totals.count() - 1) / q.getSize()));
        q.setPage(Math.min(q.getPage(), lastPage));
        return new Trial(statements(q.getFrom(), q.getTo()), trial(q), totals);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<TrialRow> trialReport(RegisterQuery q) {
        var rows = new ArrayList<TrialRow>();
        int page = q.getPage(), size = q.getSize();
        try {
            q.setSize(100);
            for (int p = 0; p <= 10; p++) {
                q.setPage(p);
                var result = trial(q);
                rows.addAll(result.rows());
                if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
                if (!result.hasNext()) return List.copyOf(rows);
            }
            throw new IllegalArgumentException("error.narrowReport");
        } finally {
            q.setPage(page);
            q.setSize(size);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TrialExport trialExport(RegisterQuery q) {
        return new TrialExport(statements(q.getFrom(), q.getTo()), trialReport(q), q.getSearch());
    }

    public record TrialExport(Statements statement, List<TrialRow> rows, String search) {
        public TrialExport {
            rows = List.copyOf(rows);
            search = search == null ? "" : search;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison comparison(String type, LocalDate from, LocalDate to) {
        access.require("accounting.report");
        if (!Set.of("income-statement", "balance-sheet", "cash-flow", "changes-in-equity", "notes")
                .contains(type)) throw new IllegalArgumentException("error.invalid");
        var current = statements(from, to);
        LocalDate previousFrom = from.minusYears(1), previousTo = to.minusYears(1);
        boolean available = !previousFrom.isBefore(accounting.books().orElseThrow().openingDate());
        var previous = available ? statements(previousFrom, previousTo) : null;
        var prior = new HashMap<Long, TrialRow>();
        boolean details = true;
        if (available && details)
            statementAccounts(type, previousFrom, previousTo)
                    .forEach(mapped -> prior.put(mapped.row().id(), mapped.row()));
        var accounts =
                (details ? statementAccounts(type, from, to) : List.<ReportedAccount>of())
                        .stream()
                                .map(
                                        mapped ->
                                                new ComparedAccount(
                                                        mapped.row(),
                                                        prior.get(mapped.row().id()),
                                                        mapped.mapping()))
                                .toList();
        var cash =
                type.equals("cash-flow")
                        ? cashCounterparts(
                                from,
                                to,
                                available ? previousFrom : null,
                                available ? previousTo : null)
                        : List.<CashCounterpart>of();
        com.foresight.fcms.accounting.dto.EquityReporting.Matrix matrix = null;
        if (type.equals("changes-in-equity")) {
            var moves = equityMovements(from, to, false);
            if (available) moves.addAll(equityMovements(previousFrom, previousTo, true));
            matrix =
                    EquityMatrixCalculator.calculate(
                            accounts,
                            moves,
                            equityReporting.effective(),
                            available,
                            equityReporting.effectiveRevisionIds(
                                    available ? previousFrom : from, to));
        }
        return new Comparison(
                current,
                previous,
                previousFrom,
                previousTo,
                accounts,
                java.time.Instant.now(),
                cash,
                matrix);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison notesExport(LocalDate from, LocalDate to) {
        var actor = access.require("accounting.report");
        if (!requestLimits.allow("gl-notes-export:" + actor.id(), 60))
            throw new ReportLimitException();
        return comparison("notes", from, to);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Comparison cashEquityExport(String type, LocalDate from, LocalDate to) {
        var actor = access.require("accounting.report");
        if (!Set.of("cash-flow", "changes-in-equity").contains(type))
            throw new IllegalArgumentException("error.invalid");
        if (!requestLimits.allow("gl-cash-equity-export:" + actor.id(), 60))
            throw new ReportLimitException();
        return comparison(type, from, to);
    }

    private record ReportedAccount(
            TrialRow row, com.foresight.fcms.accounting.dto.AccountReporting.Mapping mapping) {}

    // One membership contract is shared by cash lines and their account/entry drills.
    // Mixed-sign compound journals cannot be allocated without inventing a purpose.
    static final String CASH_MEMBERSHIP =
            """
            with cash_journals as (
             select j.id,j.effective_date,j.cash_flow,j.reverses_id,
              coalesce(sum(l.debit-l.credit) filter(where a.cash_account),0) money,
              count(*) filter(where not a.cash_account and l.debit<>l.credit) others,
              bool_or(not a.cash_account and l.debit>l.credit) positive,
              bool_or(not a.cash_account and l.debit<l.credit) negative
             from gl_journals j join gl_lines l on l.journal_id=j.id join gl_accounts a on a.id=l.account_id
             where j.state='POSTED' and j.kind<>'OPENING' and j.effective_date between ?::date and ?::date
             group by j.id
            ), cash_status as (
             select *,others>0 and (money=0 or money>0 and positive or money<0 and negative) ambiguous
             from cash_journals
            ), cash_contributions as (
             select l.id,l.account_id,a.code,a.name,c.id journal_id,c.effective_date,l.debit,l.credit,
              case when c.cash_flow not in ('OPERATING','INVESTING','FINANCING') then 'UNCLASSIFIED' else c.cash_flow end flow,
              case when c.ambiguous then 0 else m.group_id end group_id,
              case when c.ambiguous then '' else coalesce(m.group_name,a.name) end group_name,
              case when c.ambiguous then l.debit-l.credit else l.credit-l.debit end amount,c.ambiguous
             from cash_status c join gl_lines l on l.journal_id=c.id join gl_accounts a on a.id=l.account_id
             left join gl_report_account_settings m on m.account_id=a.id
             where c.others>0 and (c.ambiguous and a.cash_account or not c.ambiguous and not a.cash_account)
            )
            """;

    public record CashCounterpart(
            String flow,
            long groupId,
            String label,
            BigDecimal current,
            BigDecimal previous,
            boolean ambiguous) {
        public String line() {
            return "cash-" + flow + "-" + groupId;
        }
    }

    private List<CashCounterpart> cashCounterparts(
            LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
        var rows =
                db.query(
                        CASH_MEMBERSHIP
                                + "select flow,group_id,group_name,coalesce(sum(amount)"
                                + " filter(where effective_date between ?::date and ?::date),0)"
                                + " current,coalesce(sum(amount) filter(where effective_date"
                                + " between ?::date and ?::date),0) previous,bool_or(ambiguous)"
                                + " ambiguous from cash_contributions group by"
                                + " flow,group_id,group_name order by flow,group_id limit 1001",
                        (r, n) ->
                                new CashCounterpart(
                                        r.getString("flow"),
                                        r.getLong("group_id"),
                                        r.getString("group_name"),
                                        r.getBigDecimal("current"),
                                        previousFrom == null ? null : r.getBigDecimal("previous"),
                                        r.getBoolean("ambiguous")),
                        previousFrom == null ? from : previousFrom,
                        to,
                        from,
                        to,
                        previousFrom,
                        previousTo);
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return List.copyOf(rows);
    }

    private ArrayList<com.foresight.fcms.accounting.dto.EquityReporting.Movement> equityMovements(
            LocalDate from, LocalDate to, boolean previous) {
        // Aggregation bounds the result by posting accounts and the seven movement categories.
        var rows =
                db.query(
                        "select l.account_id,coalesce(r.payload->>'category','UNCLASSIFIED')"
                            + " category,sum(l.debit) debit,sum(l.credit) credit from gl_journals j"
                            + " join gl_lines l on l.journal_id=j.id join gl_accounts a on"
                            + " a.id=l.account_id left join gl_equity_reporting r on"
                            + " r.kind='JOURNAL' and r.target_id=coalesce(j.reverses_id,j.id) and"
                            + " r.state='APPROVED' where j.state='POSTED' and j.kind<>'OPENING' and"
                            + " j.effective_date between ? and ? and (a.classification='EQUITY' or"
                            + " r.payload->>'category'='EARNINGS_TRANSFER' and a.classification in"
                            + " ('INCOME','EXPENSE')) group by l.account_id,category order by"
                            + " l.account_id,category limit 7001",
                        (r, n) ->
                                new com.foresight.fcms.accounting.dto.EquityReporting.Movement(
                                        r.getLong(1),
                                        com.foresight.fcms.accounting.dto.EquityReporting.Category
                                                .valueOf(r.getString(2)),
                                        previous,
                                        r.getBigDecimal(3),
                                        r.getBigDecimal(4)),
                        from,
                        to);
        if (rows.size() > 7000) throw new IllegalArgumentException("error.narrowReport");
        return new ArrayList<>(rows);
    }

    private List<ReportedAccount> statementAccounts(String type, LocalDate from, LocalDate to) {
        String classification =
                switch (type) {
                    case "income-statement" -> "a.classification in ('INCOME','EXPENSE')";
                    case "notes" -> "true";
                    case "cash-flow" -> "true";
                    case "changes-in-equity" -> "a.classification in ('EQUITY','INCOME','EXPENSE')";
                    default -> "a.classification in ('ASSET','LIABILITY','EQUITY')";
                };
        var rows =
                db.query(
                        BALANCES
                                + """
                                select a.id,a.code,a.name,a.classification,coalesce(b.opening,0),coalesce(b.debit,0),coalesce(b.credit,0),
                                 greatest(coalesce(b.closing,0),0),greatest(-coalesce(b.closing,0),0),
                                 m.group_id,m.group_code,m.group_name,m.section,m.note_no,m.note_text,m.section_version_id,m.note_version_id,m.leaf_version_id,m.family_version_id,m.subgroup_version_id,m.root_version_id
                                from gl_accounts a left join balances b on b.account_id=a.id
                                left join gl_report_account_settings m on m.account_id=a.id
                                where a.depth=4 and
                                """
                                + classification
                                + " order by a.code,a.id limit 1001",
                        (r, n) -> {
                            var revisions = new ArrayList<Long>();
                            for (var column :
                                    List.of(
                                            "leaf_version_id",
                                            "family_version_id",
                                            "subgroup_version_id",
                                            "root_version_id")) {
                                var id = r.getObject(column, Long.class);
                                if (id != null) revisions.add(id);
                            }
                            return new ReportedAccount(
                                    new TrialRow(
                                            r.getLong(1),
                                            r.getString(2),
                                            r.getString(3),
                                            r.getString(4),
                                            r.getBigDecimal(5),
                                            r.getBigDecimal(6),
                                            r.getBigDecimal(7),
                                            r.getBigDecimal(8),
                                            r.getBigDecimal(9)),
                                    new com.foresight.fcms.accounting.dto.AccountReporting.Mapping(
                                            r.getObject("group_id", Long.class),
                                            r.getString("group_code"),
                                            r.getString("group_name"),
                                            r.getString("section"),
                                            r.getInt("note_no"),
                                            r.getString("note_text"),
                                            r.getObject("section_version_id", Long.class),
                                            r.getObject("note_version_id", Long.class),
                                            revisions));
                        },
                        from,
                        from,
                        from,
                        from,
                        to);
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        if (Set.of("income-statement", "notes", "cash-flow").contains(type)) {
            var transfers = new HashMap<Long, BigDecimal[]>();
            var amounts =
                    db.query(
                            "select l.account_id,sum(l.debit),sum(l.credit) from gl_journals j join"
                                + " gl_lines l on l.journal_id=j.id join gl_accounts a on"
                                + " a.id=l.account_id join gl_equity_reporting r on"
                                + " r.kind='JOURNAL' and r.target_id=coalesce(j.reverses_id,j.id)"
                                + " and r.state='APPROVED' and"
                                + " r.payload->>'category'='EARNINGS_TRANSFER' where"
                                + " j.state='POSTED' and j.kind<>'OPENING' and j.effective_date"
                                + " between ? and ? and a.classification in ('INCOME','EXPENSE')"
                                + " group by l.account_id order by l.account_id limit 1001",
                            (r, n) ->
                                    Map.entry(
                                            r.getLong(1),
                                            new BigDecimal[] {
                                                r.getBigDecimal(2), r.getBigDecimal(3)
                                            }),
                            from,
                            to);
            if (amounts.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
            amounts.forEach(e -> transfers.put(e.getKey(), e.getValue()));
            return rows.stream()
                    .map(
                            a -> {
                                var t = transfers.get(a.row().id());
                                if (t == null) return a;
                                var b = a.row();
                                return new ReportedAccount(
                                        new TrialRow(
                                                b.id(),
                                                b.code(),
                                                b.name(),
                                                b.classification(),
                                                b.opening(),
                                                b.debit().subtract(t[0]),
                                                b.credit().subtract(t[1]),
                                                b.closingDebit(),
                                                b.closingCredit()),
                                        a.mapping());
                            })
                    .toList();
        }
        return List.copyOf(rows);
    }

    public record ComparedAccount(
            TrialRow current,
            TrialRow previous,
            com.foresight.fcms.accounting.dto.AccountReporting.Mapping mapping) {
        public ComparedAccount(TrialRow current, TrialRow previous) {
            this(current, previous, null);
        }
    }

    public record Comparison(
            Statements current,
            Statements previous,
            LocalDate previousFrom,
            LocalDate previousTo,
            List<ComparedAccount> accounts,
            java.time.Instant generatedAt,
            List<CashCounterpart> cashCounterparts,
            com.foresight.fcms.accounting.dto.EquityReporting.Matrix equityMatrix) {
        public Comparison {
            accounts = List.copyOf(accounts);
            cashCounterparts = List.copyOf(cashCounterparts);
        }

        public Comparison(
                Statements current,
                Statements previous,
                LocalDate previousFrom,
                LocalDate previousTo,
                List<ComparedAccount> accounts,
                java.time.Instant generatedAt) {
            this(
                    current,
                    previous,
                    previousFrom,
                    previousTo,
                    accounts,
                    generatedAt,
                    List.of(),
                    null);
        }

        public boolean previousAvailable() {
            return previous != null;
        }

        public long unclassifiedCount() {
            return accounts.stream()
                    .filter(
                            a ->
                                    Set.of("ASSET", "LIABILITY")
                                            .contains(a.current().classification()))
                    .filter(
                            a ->
                                    a.mapping() == null
                                            || a.mapping().section().equals("UNCLASSIFIED"))
                    .filter(
                            a ->
                                    a.current().closingDebit().signum() != 0
                                            || a.current().closingCredit().signum() != 0
                                            || a.previous() != null
                                                    && (a.previous().closingDebit().signum() != 0
                                                            || a.previous().closingCredit().signum()
                                                                    != 0))
                    .count();
        }

        public List<Long> reportingVersionIds() {
            var ids = new TreeSet<Long>();
            for (var a : accounts)
                if (a.mapping() != null) {
                    ids.addAll(a.mapping().revisionIds());
                    if (a.mapping().sectionVersionId() != null)
                        ids.add(a.mapping().sectionVersionId());
                    if (a.mapping().noteVersionId() != null) ids.add(a.mapping().noteVersionId());
                }
            return List.copyOf(ids);
        }
    }

    private com.foresight.fcms.accounting.dto.AccountingViews.Book validate(
            LocalDate from, LocalDate to) {
        access.require("accounting.report");
        var book =
                accounting
                        .books()
                        .orElseThrow(
                                () -> new IllegalArgumentException("accounting.error.opening"));
        if (!book.ready()) throw new IllegalArgumentException("accounting.error.opening");
        if (from == null
                || to == null
                || from.isBefore(book.openingDate())
                || to.isBefore(from)
                || to.isAfter(LocalDate.now())) throw new IllegalArgumentException("error.dates");
        return book;
    }

    public record Classification(
            String name,
            BigDecimal opening,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal closing) {}

    public record TrialRow(
            long id,
            String code,
            String name,
            String classification,
            BigDecimal opening,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {}

    public record Activity(
            long id,
            long journalId,
            String reference,
            LocalDate date,
            String kind,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance) {}

    public record TrialTotals(
            long count,
            BigDecimal opening,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {}

    public record Trial(Statements statement, RegisterPage<TrialRow> records, TrialTotals totals) {}

    public record AccountLedger(
            com.foresight.fcms.accounting.dto.AccountingViews.Account account,
            RegisterPage<Activity> records,
            BigDecimal opening,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal closing,
            SourceCoverageService.Coverage sourceCoverage,
            LocalDate from,
            LocalDate to,
            String policy,
            java.time.Instant generatedAt,
            long matchingCount,
            String search) {}

    public record Statements(
            LocalDate from,
            LocalDate to,
            String policy,
            List<Classification> classifications,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal profit,
            BigDecimal assets,
            BigDecimal liabilities,
            BigDecimal equity,
            BigDecimal accumulatedEarnings,
            BigDecimal equityOpening,
            BigDecimal equityMovement,
            BigDecimal cashOpening,
            BigDecimal operatingCash,
            BigDecimal investingCash,
            BigDecimal financingCash,
            BigDecimal cashClosing,
            long unposted,
            SourceCoverageService.Coverage sourceCoverage) {
        public BigDecimal totalEquity() {
            return equity.add(accumulatedEarnings);
        }

        public BigDecimal liabilitiesAndEquity() {
            return liabilities.add(totalEquity());
        }

        public boolean balanceSheetMatches() {
            return assets.compareTo(liabilitiesAndEquity()) == 0;
        }

        public BigDecimal cashMovement() {
            return operatingCash.add(investingCash).add(financingCash);
        }

        public boolean cashFlowMatches() {
            return cashClosing.compareTo(
                            cashOpening.add(operatingCash).add(investingCash).add(financingCash))
                    == 0;
        }

        public boolean equityMatches() {
            return totalEquity().compareTo(equityOpening.add(equityMovement).add(profit)) == 0;
        }
    }

    public static List<StatementRow> rows(String type, Statements r) {
        return switch (type) {
            case "income-statement" ->
                    List.of(
                            new StatementRow("gl.income", r.income()),
                            new StatementRow("gl.expenses", r.expenses()),
                            new StatementRow("report.profit", r.profit()));
            case "balance-sheet" ->
                    List.of(
                            new StatementRow("gl.class.ASSET", r.assets()),
                            new StatementRow("gl.class.LIABILITY", r.liabilities()),
                            new StatementRow("gl.ownerEquity", r.equity()),
                            new StatementRow("gl.accumulatedEarnings", r.accumulatedEarnings()),
                            new StatementRow("gl.totalEquity", r.totalEquity()),
                            new StatementRow("gl.liabilitiesAndEquity", r.liabilitiesAndEquity()));
            case "cash-flow" ->
                    List.of(
                            new StatementRow("gl.openingBalance", r.cashOpening()),
                            new StatementRow("gl.flow.OPERATING", r.operatingCash()),
                            new StatementRow("gl.flow.INVESTING", r.investingCash()),
                            new StatementRow("gl.flow.FINANCING", r.financingCash()),
                            new StatementRow("gl.closingBalance", r.cashClosing()));
            case "changes-in-equity" ->
                    List.of(
                            new StatementRow("gl.openingBalance", r.equityOpening()),
                            new StatementRow("gl.equityMovement", r.equityMovement()),
                            new StatementRow("report.profit", r.profit()),
                            new StatementRow("gl.closingBalance", r.totalEquity()));
            default -> throw new IllegalArgumentException();
        };
    }

    public record StatementRow(String label, BigDecimal amount) {
        public boolean total() {
            return Set.of(
                            "report.profit",
                            "gl.totalEquity",
                            "gl.liabilitiesAndEquity",
                            "gl.closingBalance")
                    .contains(label);
        }
    }
}
