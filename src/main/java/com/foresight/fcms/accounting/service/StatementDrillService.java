package com.foresight.fcms.accounting.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class StatementDrillService {
    private final JdbcTemplate db;
    private final StatementService statements;
    private final AccessService access;
    private final EquityReportingService equityReporting;
    private final com.foresight.fcms.auth.service.AuthRateLimitService limits;

    public StatementDrillService(
            JdbcTemplate db,
            StatementService statements,
            AccessService access,
            EquityReportingService equityReporting,
            com.foresight.fcms.auth.service.AuthRateLimitService limits) {
        this.db = db;
        this.statements = statements;
        this.access = access;
        this.equityReporting = equityReporting;
        this.limits = limits;
    }

    record Plan(
            String accounts,
            int sign,
            String period,
            String flow,
            Long group,
            String section,
            String label,
            boolean labelKey,
            String equityCategory) {
        Plan(
                String accounts,
                int sign,
                String period,
                String flow,
                Long group,
                String section,
                String label,
                boolean labelKey) {
            this(accounts, sign, period, flow, group, section, label, labelKey, null);
        }

        Plan(String accounts, int sign, String period, String flow) {
            this(accounts, sign, period, flow, null, null, null, true);
        }
    }

    private Plan plan(String type, String line) {
        if (type.equals("changes-in-equity") && line.startsWith("matrix-")) return equityPlan(line);
        if (type.equals("cash-flow") && line.matches("cash-adj-[0-9]{1,19}")) {
            final long group;
            try {
                group = Long.parseLong(line.substring(9));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("error.invalid");
            }
            String label =
                    group == 0
                            ? "gl.cash.ambiguous"
                            : db
                                    .queryForList(
                                            "select name from gl_accounts where id=? and depth=3",
                                            String.class,
                                            group)
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () -> new IllegalArgumentException("error.missing"));
            return new Plan("", 1, "CASH_ADJUST", null, group, null, label, group == 0);
        }
        if (type.equals("cash-flow")
                && line.matches("cash-(OPERATING|INVESTING|FINANCING|UNCLASSIFIED)-[0-9]{1,19}")) {
            int separator = line.lastIndexOf('-');
            long group;
            try {
                group = Long.parseLong(line.substring(separator + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("error.invalid");
            }
            String flow = line.substring(5, separator);
            String label =
                    group == 0
                            ? "gl.cash.ambiguous"
                            : db
                                    .queryForList(
                                            "select name from gl_accounts where id=? and depth=3",
                                            String.class,
                                            group)
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () -> new IllegalArgumentException("error.missing"));
            return new Plan("", -1, "CASH", flow, group, null, label, group == 0);
        }
        if (line.startsWith("group-")) {
            if (!line.matches("group-[1-9][0-9]{0,18}"))
                throw new IllegalArgumentException("error.invalid");
            final long id;
            try {
                id = Long.parseLong(line.substring(6));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("error.invalid");
            }
            var groups =
                    db.query(
                            "select classification,name from gl_accounts where id=? and depth=3",
                            (r, n) -> Map.entry(r.getString(1), r.getString(2)),
                            id);
            if (groups.isEmpty()) throw new IllegalArgumentException("error.missing");
            var group = groups.getFirst();
            String classification = group.getKey();
            String key;
            if (type.equals("income-statement")
                    && Set.of("INCOME", "EXPENSE").contains(classification))
                key = classification.equals("INCOME") ? "gl.income" : "gl.expenses";
            else if (type.equals("balance-sheet")
                    && Set.of("ASSET", "LIABILITY", "EQUITY").contains(classification))
                key =
                        classification.equals("EQUITY")
                                ? "gl.ownerEquity"
                                : "gl.class." + classification;
            else throw new IllegalArgumentException("error.invalid");
            var base = plan(type, key);
            return new Plan(
                    base.accounts(),
                    base.sign(),
                    base.period(),
                    base.flow(),
                    id,
                    null,
                    group.getValue(),
                    false,
                    base.equityCategory());
        }
        if (line.startsWith("section-")) {
            String section = line.substring(8);
            if (!type.equals("balance-sheet")
                    || !Set.of(
                                    "CURRENT_ASSETS",
                                    "NON_CURRENT_ASSETS",
                                    "CURRENT_LIABILITIES",
                                    "NON_CURRENT_LIABILITIES")
                            .contains(section)) throw new IllegalArgumentException("error.invalid");
            var base =
                    plan(
                            type,
                            section.endsWith("ASSETS") ? "gl.class.ASSET" : "gl.class.LIABILITY");
            return new Plan(
                    base.accounts(),
                    base.sign(),
                    base.period(),
                    base.flow(),
                    null,
                    section,
                    "gl.presentation.section." + section,
                    true);
        }
        return switch (type + "/" + line) {
            case "income-statement/gl.income" ->
                    new Plan(
                            "a.classification='INCOME'",
                            -1,
                            "ACTIVITY",
                            null,
                            null,
                            null,
                            null,
                            true,
                            "PROFIT");
            case "income-statement/gl.expenses" ->
                    new Plan(
                            "a.classification='EXPENSE'",
                            1,
                            "ACTIVITY",
                            null,
                            null,
                            null,
                            null,
                            true,
                            "PROFIT");
            case "income-statement/report.profit",
                    "changes-in-equity/report.profit",
                    "cash-flow/report.profit" ->
                    new Plan(
                            "a.classification in ('INCOME','EXPENSE')",
                            -1,
                            "ACTIVITY",
                            null,
                            null,
                            null,
                            null,
                            true,
                            "PROFIT");
            case "balance-sheet/gl.class.ASSET" ->
                    new Plan("a.classification='ASSET'", 1, "CLOSING", null);
            case "balance-sheet/gl.class.LIABILITY" ->
                    new Plan("a.classification='LIABILITY'", -1, "CLOSING", null);
            case "balance-sheet/gl.ownerEquity" ->
                    new Plan("a.classification='EQUITY'", -1, "CLOSING", null);
            case "balance-sheet/gl.accumulatedEarnings" ->
                    new Plan("a.classification in ('INCOME','EXPENSE')", -1, "CLOSING", null);
            case "balance-sheet/gl.totalEquity", "changes-in-equity/gl.closingBalance" ->
                    new Plan(
                            "a.classification in ('EQUITY','INCOME','EXPENSE')",
                            -1,
                            "CLOSING",
                            null);
            case "balance-sheet/gl.liabilitiesAndEquity" ->
                    new Plan(
                            "a.classification in ('LIABILITY','EQUITY','INCOME','EXPENSE')",
                            -1,
                            "CLOSING",
                            null);
            case "cash-flow/gl.openingBalance" -> new Plan("a.cash_account", 1, "OPENING", null);
            case "cash-flow/gl.closingBalance" -> new Plan("a.cash_account", 1, "CLOSING", null);
            case "cash-flow/gl.flow.OPERATING" ->
                    new Plan("a.cash_account", 1, "ACTIVITY", "OPERATING");
            case "cash-flow/gl.flow.INVESTING" ->
                    new Plan("a.cash_account", 1, "ACTIVITY", "INVESTING");
            case "cash-flow/gl.flow.FINANCING" ->
                    new Plan("a.cash_account", 1, "ACTIVITY", "FINANCING");
            case "changes-in-equity/gl.openingBalance" ->
                    new Plan(
                            "a.classification in ('EQUITY','INCOME','EXPENSE')",
                            -1,
                            "OPENING",
                            null);
            case "changes-in-equity/gl.equityMovement" ->
                    new Plan(
                            "a.classification='EQUITY'",
                            -1,
                            "ACTIVITY",
                            null,
                            null,
                            null,
                            null,
                            true,
                            "PROFIT");
            default -> throw new IllegalArgumentException("error.invalid");
        };
    }

    private Plan equityPlan(String line) {
        var match =
                java.util.regex.Pattern.compile(
                                "matrix-(opening|closing|profit|CONTRIBUTION|DISTRIBUTION|TRANSFER|EARNINGS_TRANSFER|OCI|ADJUSTMENT|UNCLASSIFIED)-(all|retained|unclassified|component-[0-8])")
                        .matcher(line);
        if (!match.matches()) throw new IllegalArgumentException("error.invalid");
        String key = match.group(1), scope = match.group(2);
        var setup = equityReporting.effective();
        boolean retained =
                scope.equals("all")
                        || scope.equals("retained")
                                && setup.components().stream().noneMatch(c -> c.retained());
        String predicate;
        if (scope.startsWith("component-")) {
            int i = Integer.parseInt(scope.substring(10));
            if (i >= setup.components().size()) throw new IllegalArgumentException("error.invalid");
            var c = setup.components().get(i);
            retained = c.retained();
            predicate = ids(c.accounts());
        } else if (scope.equals("unclassified")) {
            var used = setup.components().stream().flatMap(c -> c.accounts().stream()).toList();
            predicate =
                    "a.classification='EQUITY'"
                            + (used.isEmpty() ? "" : " and not (" + ids(used) + ")");
        } else predicate = scope.equals("all") ? "a.classification='EQUITY'" : "false";
        String income = "a.classification in ('INCOME','EXPENSE')";
        if (key.equals("profit")) predicate = retained ? income : "false";
        else if (retained && Set.of("opening", "closing", "EARNINGS_TRANSFER").contains(key))
            predicate = "(" + predicate + ") or (" + income + ")";
        String period =
                key.equals("opening") ? "OPENING" : key.equals("closing") ? "CLOSING" : "ACTIVITY";
        String category =
                Set.of("opening", "closing").contains(key)
                        ? null
                        : key.equals("profit") ? "PROFIT" : key;
        return new Plan(
                "(" + predicate + ")",
                -1,
                period,
                null,
                null,
                null,
                key.equals("opening")
                        ? "gl.openingBalance"
                        : key.equals("closing")
                                ? "gl.closingBalance"
                                : key.equals("profit")
                                        ? "report.profit"
                                        : "gl.equity.movement." + key,
                true,
                category);
    }

    private static String ids(List<Long> ids) {
        return ids.isEmpty()
                ? "false"
                : "a.id in ("
                        + ids.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(","))
                        + ")";
    }

    private String contributions(Plan p) {
        if (p.period().equals("CASH_ADJUST"))
            return StatementService.CASH_MEMBERSHIP
                    + ", adjustment_lines as (select"
                    + " c.id,c.account_id,c.code,c.name,c.journal_id,c.effective_date,c.debit,c.credit,c.amount"
                    + " from cash_contributions c where c.flow='OPERATING' and c.group_id=? union"
                    + " all select"
                    + " l.id,l.account_id,a.code,a.name,c.id,c.effective_date,l.debit,l.credit,l.debit-l.credit"
                    + " from cash_journals c join gl_lines l on l.journal_id=c.id join gl_accounts"
                    + " a on a.id=l.account_id where a.classification in ('INCOME','EXPENSE') and"
                    + " a.parent_id=? and not exists(select 1 from gl_equity_reporting er where"
                    + " er.kind='JOURNAL' and er.target_id=coalesce(c.reverses_id,c.id) and"
                    + " er.state='APPROVED' and er.payload->>'category'='EARNINGS_TRANSFER')),"
                    + " contributions as (select"
                    + " c.id,c.account_id,c.code,c.name,c.journal_id,j.reference,j.kind,c.effective_date,coalesce(nullif(l.description,''),j.description)"
                    + " description,c.debit,c.credit,sum(c.amount) amount from adjustment_lines c"
                    + " join gl_journals j on j.id=c.journal_id join gl_lines l on l.id=c.id group"
                    + " by c.id,c.account_id,c.code,c.name,c.journal_id,j.reference,j.kind,c.effective_date,l.description,j.description,c.debit,c.credit"
                    + " having sum(c.amount)<>0) ";
        if (p.period().equals("CASH"))
            return StatementService.CASH_MEMBERSHIP
                    + ", contributions as (select"
                    + " c.id,c.account_id,c.code,c.name,c.journal_id,j.reference,j.kind,c.effective_date,coalesce(nullif(l.description,''),j.description)"
                    + " description,c.debit,c.credit,c.amount from cash_contributions c join"
                    + " gl_journals j on j.id=c.journal_id join gl_lines l on l.id=c.id where"
                    + " c.flow=? and c.group_id=?) ";
        String dates =
                switch (p.period()) {
                    case "ACTIVITY" -> "j.effective_date>=?::date and j.kind<>'OPENING'";
                    case "OPENING" ->
                            "(j.effective_date<?::date or (j.kind='OPENING' and"
                                    + " j.effective_date<=?::date))";
                    default -> "?::date is not null";
                };
        return "with contributions as (select l.id,l.account_id,a.code,a.name,j.id"
                   + " journal_id,j.reference,j.kind,j.effective_date,coalesce(nullif(l.description,''),j.description)"
                   + " description,l.debit,l.credit,"
                + p.sign()
                + "*(l.debit-l.credit) amount from gl_lines l join gl_journals j on"
                + " j.id=l.journal_id join gl_accounts a on a.id=l.account_id where"
                + " j.state='POSTED' and "
                + p.accounts()
                + " and "
                + dates
                + " and j.effective_date<=?::date"
                + (p.flow() == null ? "" : " and j.cash_flow=?")
                + (p.equityCategory() == null
                        ? ""
                        : p.equityCategory().equals("PROFIT")
                                ? " and not exists(select 1 from gl_equity_reporting er where"
                                        + " er.kind='JOURNAL' and"
                                        + " er.target_id=coalesce(j.reverses_id,j.id) and"
                                        + " er.state='APPROVED' and"
                                        + " er.payload->>'category'='EARNINGS_TRANSFER')"
                                : " and coalesce((select er.payload->>'category' from"
                                        + " gl_equity_reporting er where er.kind='JOURNAL' and"
                                        + " er.target_id=coalesce(j.reverses_id,j.id) and"
                                        + " er.state='APPROVED'),'UNCLASSIFIED')=?")
                + (p.group() == null ? "" : " and a.parent_id=?")
                + (p.section() == null
                        ? ""
                        : " and exists(select 1 from gl_report_account_settings rs where"
                                + " rs.account_id=a.id and rs.section=?)")
                + ") ";
    }

    private List<Object> parameters(Plan p, RegisterQuery q) {
        var a = new ArrayList<Object>();
        a.add(q.getFrom());
        if (p.period().equals("CASH")) {
            a.add(q.getTo());
            a.add(p.flow());
            a.add(p.group());
            return a;
        }
        if (p.period().equals("CASH_ADJUST")) {
            a.add(q.getTo());
            a.add(p.group());
            a.add(p.group());
            return a;
        }
        if (p.period().equals("OPENING")) a.add(q.getFrom());
        a.add(q.getTo());
        if (p.flow() != null) a.add(p.flow());
        if (p.equityCategory() != null && !p.equityCategory().equals("PROFIT"))
            a.add(p.equityCategory());
        if (p.group() != null) a.add(p.group());
        if (p.section() != null) a.add(p.section());
        return a;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Breakdown accounts(String type, String line, RegisterQuery q) {
        access.require("accounting.report");
        var report = statements.statements(q.getFrom(), q.getTo());
        q.validate();
        var p = plan(type, line);
        var sql = contributions(p);
        var args = parameters(p, q);
        var totals = new ArrayList<>(args);
        totals.add(q.pattern());
        var sums =
                db.queryForMap(
                        sql
                                + "select coalesce(sum(amount),0) total,coalesce(sum(amount)"
                                + " filter(where lower(code||' '||name) like ? escape '!'),0)"
                                + " filtered from contributions",
                        totals.toArray());
        var list = new ArrayList<>(args);
        list.add(q.pattern());
        list.add(q.getSize() + 1);
        list.add(q.offset());
        var rows =
                db.query(
                        sql
                                + "select account_id id,code,name,sum(amount) amount from"
                                + " contributions where lower(code||' '||name) like ? escape '!'"
                                + " group by account_id,code,name having sum(amount)<>0 order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "account_id",
                                                "code",
                                                "code",
                                                "name",
                                                "name",
                                                "amount",
                                                "amount"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new AccountContribution(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getBigDecimal(4)),
                        list.toArray());
        return new Breakdown(
                type,
                line,
                report,
                RegisterPage.of(rows, q),
                (BigDecimal) sums.get("total"),
                (BigDecimal) sums.get("filtered"),
                p.label() == null ? line : p.label(),
                p.labelKey(),
                trial(p, q, report));
    }

    private StatementService.Trial trial(
            Plan p, RegisterQuery q, StatementService.Statements report) {
        return trial(p, q, report, false);
    }

    private StatementService.Trial trial(
            Plan p, RegisterQuery q, StatementService.Statements report, boolean full) {
        String sql =
                contributions(p)
                        + ", balances as (select l.account_id,coalesce(sum(l.debit-l.credit)"
                        + " filter(where j.effective_date<?::date or j.kind='OPENING' and"
                        + " j.effective_date<=?::date),0) opening,coalesce(sum(l.debit)"
                        + " filter(where j.effective_date>=?::date and j.kind<>'OPENING'),0)"
                        + " debit,coalesce(sum(l.credit) filter(where j.effective_date>=?::date and"
                        + " j.kind<>'OPENING'),0) credit,coalesce(sum(l.debit-l.credit),0) closing"
                        + " from gl_journals j join gl_lines l on l.journal_id=j.id where"
                        + " j.state='POSTED' and j.effective_date<=?::date group by l.account_id) ";
        String membership =
                " from gl_accounts a left join balances t on t.account_id=a.id where a.id in"
                    + " (select account_id from contributions where lower(code||' '||name) like ?"
                    + " escape '!' group by account_id having sum(amount)<>0)";
        var args = parameters(p, q);
        args.addAll(
                List.of(
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.pattern()));
        var total =
                db.queryForMap(
                        sql
                                + "select count(*) accounts,coalesce(sum(t.opening),0)"
                                + " opening,coalesce(sum(t.debit),0)"
                                + " debit,coalesce(sum(t.credit),0)"
                                + " credit,coalesce(sum(greatest(t.closing,0)),0)"
                                + " closing_debit,coalesce(sum(greatest(-t.closing,0)),0)"
                                + " closing_credit"
                                + membership,
                        args.toArray());
        var totals =
                new StatementService.TrialTotals(
                        ((Number) total.get("accounts")).longValue(),
                        (BigDecimal) total.get("opening"),
                        (BigDecimal) total.get("debit"),
                        (BigDecimal) total.get("credit"),
                        (BigDecimal) total.get("closing_debit"),
                        (BigDecimal) total.get("closing_credit"));
        // Financial paper retains original account ordering; filtering precedes SQL paging.
        args.add(full ? 1001 : q.getSize() + 1);
        args.add(full ? 0 : q.offset());
        var rows =
                db.query(
                        sql
                                + "select"
                                + " a.id,a.code,a.name,a.classification,coalesce(t.opening,0),coalesce(t.debit,0),coalesce(t.credit,0),greatest(coalesce(t.closing,0),0),greatest(-coalesce(t.closing,0),0)"
                                + membership
                                + " order by a.code,a.id limit ? offset ?",
                        (r, n) ->
                                new StatementService.TrialRow(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getString(4),
                                        r.getBigDecimal(5),
                                        r.getBigDecimal(6),
                                        r.getBigDecimal(7),
                                        r.getBigDecimal(8),
                                        r.getBigDecimal(9)),
                        args.toArray());
        if (full && rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return new StatementService.Trial(
                report,
                full
                        ? new RegisterPage<>(List.copyOf(rows), 0, 1000, false)
                        : RegisterPage.of(rows, q),
                totals);
    }

    public record Export(
            StatementService.TrialExport trial,
            String label,
            boolean labelKey,
            BigDecimal contribution) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Export export(String type, String line, RegisterQuery q) {
        var actor = access.require("accounting.report");
        if (!limits.allow("gl-drill-export:" + actor.id(), 60))
            throw new StatementService.ReportLimitException();
        var report = statements.statements(q.getFrom(), q.getTo());
        q.validate();
        var p = plan(type, line);
        var trial = trial(p, q, report, true);
        var args = parameters(p, q);
        args.add(q.pattern());
        var sum =
                db.queryForObject(
                        contributions(p)
                                + "select coalesce(sum(amount),0) from contributions where"
                                + " lower(code||' '||name) like ? escape '!'",
                        BigDecimal.class,
                        args.toArray());
        return new Export(
                new StatementService.TrialExport(report, trial.records().rows(), q.getSearch()),
                p.label() == null ? line : p.label(),
                p.labelKey(),
                sum);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Entries entries(String type, String line, long account, RegisterQuery q) {
        access.require("accounting.report");
        var report = statements.statements(q.getFrom(), q.getTo());
        q.validate();
        var p = plan(type, line);
        var sql = contributions(p);
        var args = parameters(p, q);
        args.add(account);
        var accountRows =
                db.query(
                        sql
                                + "select account_id id,code,name,sum(amount) amount from"
                                + " contributions where account_id=? group by account_id,code,name",
                        (r, n) ->
                                new AccountContribution(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getBigDecimal(4)),
                        args.toArray());
        var selected =
                accountRows.stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("error.missing"));
        var totals = new ArrayList<>(args);
        totals.add(q.pattern());
        BigDecimal filtered =
                db.queryForObject(
                        sql
                                + "select coalesce(sum(amount),0) from contributions where"
                                + " account_id=? and lower(reference||' '||description) like ?"
                                + " escape '!'",
                        BigDecimal.class,
                        totals.toArray());
        var list = new ArrayList<>(totals);
        list.add(q.getSize() + 1);
        list.add(q.offset());
        var rows =
                db.query(
                        sql
                                + "select * from contributions where account_id=? and"
                                + " lower(reference||' '||description) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "id",
                                                "date",
                                                "effective_date",
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
                                                "amount",
                                                "amount"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Contribution(
                                        r.getLong("id"),
                                        r.getLong("journal_id"),
                                        r.getString("reference"),
                                        r.getString("kind"),
                                        r.getObject("effective_date", LocalDate.class),
                                        r.getString("description"),
                                        r.getBigDecimal("debit"),
                                        r.getBigDecimal("credit"),
                                        r.getBigDecimal("amount")),
                        list.toArray());
        return new Entries(
                type,
                line,
                report,
                selected,
                RegisterPage.of(rows, q),
                filtered,
                p.label() == null ? line : p.label(),
                p.labelKey(),
                accountLedger(account, q));
    }

    private StatementService.AccountLedger accountLedger(long account, RegisterQuery query) {
        var q = new com.foresight.fcms.accounting.dto.LedgerQuery();
        q.setFrom(query.getFrom());
        q.setTo(query.getTo());
        q.setSize(query.getSize());
        q.setPage(query.getPage());
        q.setSearch(query.getSearch());
        return statements.ledger(account, q);
    }

    public record AccountContribution(long id, String code, String name, BigDecimal amount) {}

    public record Contribution(
            long id,
            long journalId,
            String reference,
            String kind,
            LocalDate date,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal amount) {}

    public record Breakdown(
            String type,
            String line,
            StatementService.Statements statement,
            RegisterPage<AccountContribution> records,
            BigDecimal total,
            BigDecimal filteredTotal,
            String label,
            boolean labelKey,
            StatementService.Trial trial) {}

    public record Entries(
            String type,
            String line,
            StatementService.Statements statement,
            AccountContribution account,
            RegisterPage<Contribution> records,
            BigDecimal filteredTotal,
            String label,
            boolean labelKey,
            StatementService.AccountLedger ledger) {}
}
