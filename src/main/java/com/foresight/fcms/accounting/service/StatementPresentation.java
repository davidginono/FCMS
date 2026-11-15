package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.StatementDocument;
import com.foresight.fcms.accounting.dto.StatementDocument.*;
import com.foresight.fcms.accounting.service.StatementService.*;
import com.foresight.fcms.shared.view.ApplicationTimestamps;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** FCMS accounting figures prepared for the original Microfinance statement surfaces. */
@Service
public class StatementPresentation {
    private final MessageSource messages;

    public StatementPresentation(MessageSource messages) {
        this.messages = messages;
    }

    private String label(String key, Locale locale) {
        return messages.getMessage(key, null, locale).replace(" (TZS)", "");
    }

    private String money(BigDecimal amount, Locale locale) {
        return amount == null
                ? label("gl.presentation.unavailable", locale)
                : new DecimalFormat(
                                "#,##0.00;(#,##0.00)", DecimalFormatSymbols.getInstance(Locale.UK))
                        .format(amount);
    }

    private String range(Statements report) {
        return ApplicationTimestamps.date(report.from())
                + " — "
                + ApplicationTimestamps.date(report.to());
    }

    private String generated(java.time.Instant at, Locale locale) {
        return label("gl.presentation.generated", locale)
                + ": "
                + DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm z", locale)
                        .withZone(ZoneId.of("Africa/Dar_es_Salaam"))
                        .format(at);
    }

    private List<String> provenance(Statements report, Locale locale) {
        var coverage = report.sourceCoverage();
        return List.of(
                range(report),
                label("common.status", locale) + ": " + label("status.DRAFT", locale),
                label("gl.policy", locale) + ": " + report.policy(),
                label("gl.unposted", locale) + ": " + report.unposted(),
                label("gl.coverage.through", locale)
                        + ": "
                        + ApplicationTimestamps.date(coverage.through()),
                label("gl.coverage.unposted", locale) + ": " + coverage.unposted(),
                label("gl.coverage.unverified", locale) + ": " + coverage.unverified(),
                label("gl.coverage.posted", locale) + ": " + coverage.posted(),
                label("gl.coverage.opening", locale) + ": " + coverage.openingCovered(),
                label("gl.coverage.amount", locale)
                        + ": "
                        + money(coverage.unresolvedAmount(), locale),
                label("gl.coverage", locale)
                        + ": "
                        + label(
                                coverage.complete()
                                        ? "gl.coverage.complete"
                                        : "gl.coverage.incomplete",
                                locale),
                label("gl.reconciliation", locale)
                        + ": "
                        + label(
                                report.balanceSheetMatches()
                                                && report.cashFlowMatches()
                                                && report.equityMatches()
                                        ? "gl.reconciled"
                                        : "gl.errorReconciliation",
                                locale));
    }

    public StatementDocument statement(String type, Comparison source, Locale locale) {
        var current = source.current();
        var prior = source.previous();
        var header =
                new Header(
                        "FCMS",
                        label("gl.presentation.companyScope", locale),
                        (type.equals("balance-sheet")
                                        ? label("gl.presentation.asAt", locale)
                                                + ": "
                                                + ApplicationTimestamps.date(current.to())
                                        : label("gl.presentation.period", locale)
                                                + ": "
                                                + range(current))
                                + " | TZS",
                        label("gl.presentation.previous", locale)
                                + ": "
                                + ApplicationTimestamps.date(source.previousFrom())
                                + " — "
                                + ApplicationTimestamps.date(source.previousTo()),
                        generated(source.generatedAt(), locale),
                        List.of());
        var evidence = new ArrayList<>(provenance(current, locale));
        if (source.previousAvailable()) {
            evidence.add(label("gl.presentation.previous", locale));
            evidence.addAll(provenance(prior, locale));
        }
        if (!source.accounts().isEmpty()) {
            evidence.add(
                    label("gl.reporting.unclassified", locale) + ": " + source.unclassifiedCount());
            var ids = source.reportingVersionIds();
            evidence.add(label("gl.reporting.revisionCount", locale) + ": " + ids.size());
            for (int offset = 0; offset < ids.size(); offset += 4)
                evidence.add(
                        label("gl.reporting.references", locale)
                                + ": "
                                + ids.subList(offset, Math.min(offset + 4, ids.size())).stream()
                                        .map(id -> "#" + id)
                                        .collect(java.util.stream.Collectors.joining(", ")));
        }
        String title = label("gl.presentation.title." + type, locale);
        if (type.equals("balance-sheet")) title += " " + ApplicationTimestamps.date(current.to());
        if (type.equals("changes-in-equity")) {
            evidence.add(header.previous());
            evidence.add(header.generated());
            return equity(
                    label("gl.equity.referenceTitle", locale), source, header, evidence, locale);
        }
        var columns =
                List.of(
                        new Column(label("gl.presentation.account", locale), false, .12f),
                        new Column(label("common.description", locale), false, .40f),
                        new Column(label("gl.presentation.notes", locale), false, .08f),
                        new Column(label("gl.presentation.current", locale), true, .20f),
                        new Column(label("gl.presentation.previous", locale), true, .20f));
        var rows = new ArrayList<Row>();
        switch (type) {
            case "income-statement" -> {
                group(
                        type,
                        "INCOME",
                        "gl.income",
                        "gl.presentation.totalIncome",
                        current.income(),
                        prior == null ? null : prior.income(),
                        source,
                        rows,
                        locale);
                group(
                        type,
                        "EXPENSE",
                        "gl.expenses",
                        "gl.presentation.totalExpenses",
                        current.expenses(),
                        prior == null ? null : prior.expenses(),
                        source,
                        rows,
                        locale);
                rows.add(
                        summary(
                                "gl.presentation.surplus",
                                "report.profit",
                                current.profit(),
                                prior == null ? null : prior.profit(),
                                true,
                                locale));
            }
            case "balance-sheet" -> position(source, rows, locale);
            case "notes" -> notes(source, rows, locale);
            case "cash-flow" -> {
                boolean ambiguous =
                        source.cashCounterparts().stream().anyMatch(CashCounterpart::ambiguous);
                for (String section :
                        List.of("OPERATING", "INVESTING", "FINANCING", "UNCLASSIFIED")) {
                    var members =
                            source.cashCounterparts().stream()
                                    .filter(r -> r.flow().equals(section))
                                    .filter(
                                            r ->
                                                    r.current().signum() != 0
                                                            || r.previous() != null
                                                                    && r.previous().signum() != 0
                                                            || r.ambiguous())
                                    .toList();
                    if (members.isEmpty() && !section.equals("OPERATING")) continue;
                    rows.add(heading("gl.presentation.cashSection." + section, locale));
                    BigDecimal c = BigDecimal.ZERO, p = BigDecimal.ZERO;
                    if (section.equals("OPERATING")) {
                        rows.add(
                                summary(
                                        "gl.presentation.surplus",
                                        "report.profit",
                                        current.profit(),
                                        prior == null ? null : prior.profit(),
                                        false,
                                        locale));
                        var adjustments = new TreeMap<Long, BigDecimal[]>();
                        var names = new HashMap<Long, String>();
                        for (var account : source.accounts())
                            if (Set.of("INCOME", "EXPENSE")
                                    .contains(account.current().classification())) {
                                long id =
                                        account.mapping() == null
                                                ? account.current().id()
                                                : account.mapping().groupId();
                                names.put(
                                        id,
                                        account.mapping() == null
                                                ? account.current().name()
                                                : account.mapping().groupName());
                                var amounts =
                                        adjustments.computeIfAbsent(
                                                id,
                                                k ->
                                                        new BigDecimal[] {
                                                            BigDecimal.ZERO, BigDecimal.ZERO
                                                        });
                                amounts[0] =
                                        amounts[0].add(
                                                account.current()
                                                        .debit()
                                                        .subtract(account.current().credit()));
                                if (account.previous() != null)
                                    amounts[1] =
                                            amounts[1].add(
                                                    account.previous()
                                                            .debit()
                                                            .subtract(account.previous().credit()));
                            }
                        for (var member : members) {
                            names.put(
                                    member.groupId(),
                                    member.ambiguous()
                                            ? label("gl.cash.ambiguous", locale)
                                            : member.label());
                            var amounts =
                                    adjustments.computeIfAbsent(
                                            member.groupId(),
                                            k ->
                                                    new BigDecimal[] {
                                                        BigDecimal.ZERO, BigDecimal.ZERO
                                                    });
                            amounts[0] = amounts[0].add(member.current());
                            if (member.previous() != null)
                                amounts[1] = amounts[1].add(member.previous());
                        }
                        for (var entry : adjustments.entrySet())
                            if (entry.getValue()[0].signum() != 0
                                    || entry.getValue()[1].signum() != 0) {
                                String name =
                                        label("gl.cash.adjustment", locale)
                                                + " — "
                                                + names.get(entry.getKey());
                                rows.add(
                                        new Row(
                                                List.of(
                                                        "",
                                                        name,
                                                        "",
                                                        money(entry.getValue()[0], locale),
                                                        money(
                                                                prior == null
                                                                        ? null
                                                                        : entry.getValue()[1],
                                                                locale)),
                                                false,
                                                false,
                                                name,
                                                "cash-adj-" + entry.getKey(),
                                                null));
                            }
                        c = current.operatingCash();
                        p = prior == null ? BigDecimal.ZERO : prior.operatingCash();
                    } else
                        for (var member : members) {
                            String name =
                                    member.ambiguous()
                                            ? label("gl.cash.ambiguous", locale)
                                            : member.label();
                            rows.add(
                                    new Row(
                                            List.of(
                                                    "",
                                                    name,
                                                    "",
                                                    money(member.current(), locale),
                                                    money(member.previous(), locale)),
                                            false,
                                            false,
                                            name,
                                            member.line(),
                                            null));
                            c = c.add(member.current());
                            if (member.previous() != null) p = p.add(member.previous());
                        }
                    if (!section.equals("UNCLASSIFIED"))
                        rows.add(
                                summary(
                                        "common.total",
                                        null,
                                        c,
                                        prior == null ? null : p,
                                        true,
                                        locale));
                }
                rows.add(
                        summary(
                                "gl.presentation.cashMovement",
                                null,
                                ambiguous ? null : current.cashMovement(),
                                prior == null || ambiguous ? null : prior.cashMovement(),
                                true,
                                locale));
                rows.add(
                        summary(
                                "gl.presentation.cashOpening",
                                "gl.openingBalance",
                                current.cashOpening(),
                                prior == null ? null : prior.cashOpening(),
                                false,
                                locale));
                rows.add(
                        summary(
                                "gl.presentation.cashClosing",
                                "gl.closingBalance",
                                current.cashClosing(),
                                prior == null ? null : prior.cashClosing(),
                                true,
                                locale));
            }
            default -> throw new IllegalArgumentException("error.invalid");
        }
        return new StatementDocument(
                title,
                "statement",
                false,
                3,
                header,
                columns,
                rows,
                label("gl.presentation.outputNotice", locale),
                evidence);
    }

    private Row heading(String key, Locale locale) {
        String text = label(key, locale);
        return new Row(List.of("", text, "", "", ""), false, true, text, null, null);
    }

    private record Group(
            String code,
            String name,
            String section,
            int note,
            Long groupId,
            Long accountId,
            BigDecimal current,
            BigDecimal previous) {}

    private List<Group> groups(String type, Comparison source) {
        var memberships = new LinkedHashMap<String, List<ComparedAccount>>();
        for (var pair : source.accounts()) {
            var a = pair.current();
            var m = pair.mapping();
            String section =
                    m == null
                            ? (Set.of("ASSET", "LIABILITY").contains(a.classification())
                                    ? "UNCLASSIFIED"
                                    : a.classification())
                            : m.section();
            String key =
                    section
                            + ":"
                            + (m == null || m.groupId() == null
                                    ? "account:" + a.id()
                                    : "group:" + m.groupId());
            memberships.computeIfAbsent(key, k -> new ArrayList<>()).add(pair);
        }
        var result = new ArrayList<Group>();
        for (var entry : memberships.entrySet()) {
            var members = entry.getValue();
            var first = members.getFirst();
            var a = first.current();
            var m = first.mapping();
            BigDecimal current = BigDecimal.ZERO,
                    previous = source.previousAvailable() ? BigDecimal.ZERO : null;
            var noteNumbers = new HashSet<Integer>();
            for (var pair : members) {
                current = current.add(accountAmount(type, pair.current()));
                if (source.previousAvailable()) {
                    if (pair.previous() == null)
                        throw new IllegalStateException(
                                "Comparative account snapshot is incomplete");
                    previous = previous.add(accountAmount(type, pair.previous()));
                }
                noteNumbers.add(pair.mapping() == null ? 0 : pair.mapping().noteNumber());
            }
            if (current.signum() == 0 && (previous == null || previous.signum() == 0)) continue;
            String section = entry.getKey().substring(0, entry.getKey().indexOf(':'));
            result.add(
                    new Group(
                            m == null || m.groupId() == null ? a.code() : m.groupCode(),
                            m == null || m.groupId() == null ? a.name() : m.groupName(),
                            section,
                            noteNumbers.size() == 1 ? noteNumbers.iterator().next() : 0,
                            m == null ? null : m.groupId(),
                            m == null || m.groupId() == null ? a.id() : null,
                            current,
                            previous));
        }
        return List.copyOf(result);
    }

    private BigDecimal accountAmount(String type, TrialRow row) {
        if (type.equals("income-statement"))
            return row.classification().equals("INCOME")
                    ? row.credit().subtract(row.debit())
                    : row.debit().subtract(row.credit());
        var closing = row.closingDebit().subtract(row.closingCredit());
        return row.classification().equals("ASSET") ? closing : closing.negate();
    }

    private void group(
            String type,
            String classification,
            String section,
            String totalLabel,
            BigDecimal current,
            BigDecimal previous,
            Comparison source,
            List<Row> rows,
            Locale locale) {
        rows.add(heading(section, locale));
        for (var line : groups(type, source))
            if (line.section().equals(classification)) rows.add(groupRow(line, locale));
        rows.add(summary(totalLabel, section, current, previous, true, locale));
    }

    private Row groupRow(Group line, Locale locale) {
        return new Row(
                List.of(
                        line.code(),
                        line.name(),
                        line.note() == 0 ? "" : Integer.toString(line.note()),
                        money(line.current(), locale),
                        money(line.previous(), locale)),
                false,
                false,
                line.name(),
                line.groupId() == null ? null : "group-" + line.groupId(),
                line.accountId());
    }

    private void position(Comparison source, List<Row> rows, Locale locale) {
        var lines = groups("balance-sheet", source);
        var current = source.current();
        var prior = source.previous();
        for (String section :
                List.of(
                        "CURRENT_ASSETS",
                        "NON_CURRENT_ASSETS",
                        "CURRENT_LIABILITIES",
                        "NON_CURRENT_LIABILITIES",
                        "EQUITY",
                        "UNCLASSIFIED")) {
            if (section.equals("CURRENT_LIABILITIES"))
                rows.add(
                        summary(
                                "gl.presentation.totalAssets",
                                "gl.class.ASSET",
                                current.assets(),
                                prior == null ? null : prior.assets(),
                                true,
                                locale));
            var members = lines.stream().filter(g -> g.section().equals(section)).toList();
            if (members.isEmpty() && !section.equals("EQUITY")) continue;
            rows.add(heading("gl.presentation.section." + section, locale));
            for (var member : members) rows.add(groupRow(member, locale));
            if (section.equals("EQUITY")) {
                rows.add(
                        summary(
                                "gl.presentation.retainedEarnings",
                                "gl.accumulatedEarnings",
                                current.accumulatedEarnings(),
                                prior == null ? null : prior.accumulatedEarnings(),
                                false,
                                locale));
                rows.add(
                        summary(
                                "common.total",
                                "gl.totalEquity",
                                current.totalEquity(),
                                prior == null ? null : prior.totalEquity(),
                                true,
                                locale));
            } else if (!section.equals("UNCLASSIFIED")) {
                var now =
                        members.stream()
                                .map(Group::current)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                var before =
                        source.previousAvailable()
                                ? members.stream()
                                        .map(Group::previous)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                : null;
                rows.add(summary("common.total", "section-" + section, now, before, true, locale));
            }
        }
        rows.add(
                summary(
                        "gl.liabilitiesAndEquity",
                        "gl.liabilitiesAndEquity",
                        current.liabilitiesAndEquity(),
                        prior == null ? null : prior.liabilitiesAndEquity(),
                        true,
                        locale));
    }

    private void notes(Comparison source, List<Row> rows, Locale locale) {
        var grouped = new TreeMap<Integer, List<ComparedAccount>>();
        for (var a : source.accounts())
            if (a.mapping() != null && a.mapping().noteNumber() > 0)
                grouped.computeIfAbsent(a.mapping().noteNumber(), n -> new ArrayList<>()).add(a);
        for (var entry : grouped.entrySet()) {
            String heading = label("gl.presentation.note", locale) + " " + entry.getKey();
            rows.add(new Row(List.of("", heading, "", "", ""), false, true, heading, null, null));
            BigDecimal current = BigDecimal.ZERO,
                    previous = source.previousAvailable() ? BigDecimal.ZERO : null;
            var texts = new LinkedHashSet<String>();
            for (var pair : entry.getValue()) {
                String type =
                        Set.of("INCOME", "EXPENSE").contains(pair.current().classification())
                                ? "income-statement"
                                : "balance-sheet";
                var amount = accountAmount(type, pair.current());
                if (source.previousAvailable() && pair.previous() == null)
                    throw new IllegalStateException("Comparative account snapshot is incomplete");
                var before =
                        source.previousAvailable() ? accountAmount(type, pair.previous()) : null;
                rows.add(
                        new Row(
                                List.of(
                                        pair.current().code(),
                                        pair.current().name(),
                                        "",
                                        money(amount, locale),
                                        money(before, locale)),
                                false,
                                false,
                                pair.current().name(),
                                null,
                                pair.current().id()));
                current = current.add(amount);
                if (previous != null) previous = previous.add(before);
                if (!pair.mapping().noteText().isBlank()) texts.add(pair.mapping().noteText());
            }
            for (var text : texts)
                for (int offset = 0; offset < text.length(); offset += 500)
                    rows.add(
                            new Row(
                                    List.of(
                                            "",
                                            text.substring(
                                                    offset, Math.min(offset + 500, text.length())),
                                            "",
                                            "",
                                            ""),
                                    false,
                                    false,
                                    "",
                                    null,
                                    null));
            rows.add(summary("common.total", null, current, previous, true, locale));
        }
    }

    private Row summary(
            String key,
            String drill,
            BigDecimal current,
            BigDecimal previous,
            boolean total,
            Locale locale) {
        String text = label(key, locale);
        return new Row(
                List.of("", text, "", money(current, locale), money(previous, locale)),
                total,
                false,
                text,
                drill,
                null);
    }

    private StatementDocument equity(
            String title, Comparison source, Header header, List<String> evidence, Locale locale) {
        var matrix = source.equityMatrix();
        if (matrix == null)
            matrix =
                    EquityMatrixCalculator.calculate(
                            source.accounts(),
                            List.of(),
                            com.foresight.fcms.accounting.dto.EquityReporting.Setup.empty(),
                            source.previousAvailable(),
                            List.of());
        var columns = new ArrayList<Column>();
        columns.add(new Column("", false, .235f));
        float width = .765f / (matrix.columns().size() + 1);
        for (String key : matrix.columns()) {
            String name =
                    key.startsWith("component:")
                            ? componentName(
                                    matrix.setup()
                                            .components()
                                            .get(Integer.parseInt(key.substring(10))),
                                    locale)
                            : label(
                                    key.equals("retained")
                                            ? "gl.presentation.retainedEarnings"
                                            : "gl.equity.unclassified",
                                    locale);
            columns.add(new Column(name.replace(" / ", " /\n"), true, width));
        }
        columns.add(new Column(label("gl.equity.total", locale), true, width));
        var rows = new ArrayList<Row>();
        equityRows(
                matrix.current(),
                matrix.columns(),
                source.current().from(),
                source.current().to(),
                rows,
                locale);
        if (matrix.previous() != null) {
            String period =
                    label("gl.presentation.period", locale)
                            + " "
                            + ApplicationTimestamps.date(source.previousFrom())
                            + " – "
                            + ApplicationTimestamps.date(source.previousTo());
            var cells = new ArrayList<String>();
            cells.add(period);
            while (cells.size() < columns.size()) cells.add("");
            rows.add(new Row(cells, false, true, period, null, null));
            equityRows(
                    matrix.previous(),
                    matrix.columns(),
                    source.previousFrom(),
                    source.previousTo(),
                    rows,
                    locale);
        }
        evidence.add(
                label("gl.equity.checks", locale)
                        + ": "
                        + (matrix.checks().isEmpty()
                                ? label("gl.reconciled", locale)
                                : matrix.checks().stream()
                                        .map(
                                                c ->
                                                        messages.getMessage(
                                                                "gl.equity.check." + c,
                                                                null,
                                                                c,
                                                                locale))
                                        .collect(java.util.stream.Collectors.joining("; "))));
        for (long id : matrix.revisionIds())
            evidence.add(label("gl.reporting.references", locale) + ": #" + id);
        String period =
                label("gl.equity.forPeriod", locale)
                        + "       "
                        + source.current()
                                .to()
                                .format(DateTimeFormatter.ofPattern("dd-MMM-uuuu", locale))
                                .toUpperCase(locale);
        var equityHeader =
                new Header(
                        header.identity(),
                        header.scope(),
                        period,
                        header.previous(),
                        header.generated(),
                        List.of());
        return new StatementDocument(
                title,
                "equity",
                true,
                -1,
                equityHeader,
                columns,
                rows,
                label("gl.presentation.outputNotice", locale),
                evidence);
    }

    private String componentName(
            com.foresight.fcms.accounting.dto.EquityReporting.Component c, Locale locale) {
        return locale.getLanguage().equals("sw") && !c.nameSw().isBlank() ? c.nameSw() : c.name();
    }

    private void equityRows(
            List<com.foresight.fcms.accounting.dto.EquityReporting.MatrixRow> source,
            List<String> columns,
            java.time.LocalDate from,
            java.time.LocalDate to,
            List<Row> rows,
            Locale locale) {
        for (var row : source) {
            String text =
                    switch (row.key()) {
                        case "opening", "closing" ->
                                label("gl.equity.balanceAt", locale)
                                        + " "
                                        + (row.key().equals("opening") ? from : to)
                                                .format(
                                                        DateTimeFormatter.ofPattern(
                                                                "d MMMM uuuu", locale));
                        case "profit" -> label("gl.equity.profit", locale);
                        default -> label("gl.equity.movement." + row.key(), locale);
                    };
            var cells = new ArrayList<String>();
            cells.add(text);
            for (String key : columns) cells.add(equityAmount(row.values().get(key), locale));
            cells.add(equityAmount(row.total(), locale));
            var drills = new HashMap<Integer, String>();
            for (int i = 0; i < columns.size(); i++)
                if (!cells.get(i + 1).isBlank())
                    drills.put(i + 1, equityDrill(row.key(), columns.get(i), from, to));
            if (!cells.getLast().isBlank())
                drills.put(cells.size() - 1, equityDrill(row.key(), "all", from, to));
            rows.add(
                    new Row(
                            cells,
                            row.key().equals("closing"),
                            false,
                            text,
                            null,
                            null,
                            null,
                            drills));
        }
    }

    private String equityDrill(
            String key, String component, java.time.LocalDate from, java.time.LocalDate to) {
        return "/accounting/statements/changes-in-equity/lines/matrix-"
                + key
                + "-"
                + component.replace(':', '-')
                + "?from="
                + from
                + "&to="
                + to;
    }

    private String equityAmount(BigDecimal amount, Locale locale) {
        return amount != null && amount.signum() == 0 ? "" : money(amount, locale);
    }

    public StatementDocument trial(TrialExport source, Locale locale) {
        return trialDocument(
                source.statement(),
                source.rows(),
                source.search(),
                trialTotals(source.rows()),
                locale);
    }

    public StatementDocument contributionTrial(StatementDrillService.Export source, Locale locale) {
        var d = trial(source.trial(), locale);
        var evidence = new ArrayList<>(d.provenance());
        String name = source.labelKey() ? label(source.label(), locale) : source.label();
        evidence.add(name + ": " + money(source.contribution(), locale) + " TZS");
        return new StatementDocument(
                d.title(),
                d.style(),
                d.landscape(),
                d.detailsBefore(),
                d.header(),
                d.columns(),
                d.rows(),
                d.notice(),
                evidence);
    }

    public StatementDocument trialPage(Trial source, String search, Locale locale) {
        return trialDocument(
                source.statement(), source.records().rows(), search, source.totals(), locale);
    }

    private TrialTotals trialTotals(List<TrialRow> rows) {
        BigDecimal opening = BigDecimal.ZERO,
                debit = BigDecimal.ZERO,
                credit = BigDecimal.ZERO,
                closingDebit = BigDecimal.ZERO,
                closingCredit = BigDecimal.ZERO;
        for (var row : rows) {
            opening = opening.add(row.opening());
            debit = debit.add(row.debit());
            credit = credit.add(row.credit());
            closingDebit = closingDebit.add(row.closingDebit());
            closingCredit = closingCredit.add(row.closingCredit());
        }
        return new TrialTotals(rows.size(), opening, debit, credit, closingDebit, closingCredit);
    }

    private StatementDocument trialDocument(
            Statements report,
            List<TrialRow> sourceRows,
            String search,
            TrialTotals matchingTotals,
            Locale locale) {
        var header =
                new Header(
                        "FCMS",
                        label("gl.presentation.companyScope", locale),
                        label("gl.presentation.asAt", locale)
                                + ": "
                                + ApplicationTimestamps.date(report.to())
                                + " | TZS",
                        label("gl.presentation.previous", locale)
                                + ": "
                                + ApplicationTimestamps.date(report.from().minusYears(1))
                                + " — "
                                + ApplicationTimestamps.date(report.to().minusYears(1)),
                        generated(java.time.Instant.now(), locale),
                        search.isBlank()
                                ? List.of()
                                : List.of(label("common.search", locale) + ": " + search));
        var columns =
                List.of(
                        new Column(label("gl.presentation.account", locale), false, .12f),
                        new Column(label("common.description", locale), false, .26f),
                        new Column(label("gl.presentation.bfBalance", locale), true, .124f),
                        new Column(label("gl.presentation.debits", locale), true, .124f),
                        new Column(label("gl.presentation.credits", locale), true, .124f),
                        new Column(label("gl.presentation.netChange", locale), true, .124f),
                        new Column(label("gl.presentation.endingBalance", locale), true, .124f));
        var rows = new ArrayList<Row>();
        String totalLabel =
                label(search.isBlank() ? "common.total" : "gl.presentation.filteredTotal", locale);
        var totals =
                List.of(
                        matchingTotals.opening(),
                        matchingTotals.debit(),
                        matchingTotals.credit(),
                        matchingTotals.debit().subtract(matchingTotals.credit()),
                        matchingTotals.closingDebit().subtract(matchingTotals.closingCredit()));
        for (var account : sourceRows) {
            var amounts =
                    List.of(
                            account.opening(),
                            account.debit(),
                            account.credit(),
                            account.debit().subtract(account.credit()),
                            account.closingDebit().subtract(account.closingCredit()));
            var cells = new ArrayList<String>(List.of(account.code(), account.name()));
            for (var amount : amounts) cells.add(money(amount, locale));
            rows.add(new Row(cells, false, false, account.name(), null, account.id()));
        }
        var totalCells = new ArrayList<String>(List.of(totalLabel, ""));
        for (var total : totals) totalCells.add(money(total, locale));
        rows.add(new Row(totalCells, true, false, totalLabel, null, null));
        var evidence = new ArrayList<>(provenance(report, locale));
        evidence.add(
                label("gl.closingDebit", locale)
                        + ": "
                        + money(matchingTotals.closingDebit(), locale));
        evidence.add(
                label("gl.closingCredit", locale)
                        + ": "
                        + money(matchingTotals.closingCredit(), locale));
        return new StatementDocument(
                label("gl.presentation.trialTitle", locale)
                        + " "
                        + ApplicationTimestamps.date(report.to()),
                "trial",
                true,
                2,
                header,
                columns,
                rows,
                label("gl.presentation.outputNotice", locale),
                evidence);
    }

    public StatementDocument ledger(AccountLedger source, Locale locale) {
        String period =
                ApplicationTimestamps.date(source.from())
                        + " — "
                        + ApplicationTimestamps.date(source.to());
        var header =
                new Header(
                        "FCMS",
                        label("gl.presentation.companyScope", locale),
                        label("gl.presentation.period", locale) + ": " + period + " | TZS",
                        label("gl.presentation.previous", locale)
                                + ": "
                                + ApplicationTimestamps.date(source.from().minusYears(1))
                                + " — "
                                + ApplicationTimestamps.date(source.to().minusYears(1)),
                        generated(source.generatedAt(), locale),
                        source.search().isBlank()
                                ? List.of()
                                : List.of(label("common.search", locale) + ": " + source.search()));
        var columns =
                List.of(
                        new Column(label("gl.presentation.transactionDate", locale), false, .12f),
                        new Column(label("gl.presentation.accountNumber", locale), false, .16f),
                        new Column(label("common.description", locale), false, .32f),
                        new Column(label("gl.presentation.debits", locale), true, .13f),
                        new Column(label("gl.presentation.credits", locale), true, .13f),
                        new Column(label("gl.presentation.runningBalance", locale), true, .14f));
        var rows = new ArrayList<Row>();
        String bf = label("gl.presentation.bfBalance", locale),
                total = label("gl.presentation.fullPeriodTotal", locale);
        rows.add(
                new Row(
                        List.of("", "", bf, "", "", money(source.opening(), locale)),
                        true,
                        false,
                        bf,
                        null,
                        null));
        for (var entry : source.records().rows()) {
            String description =
                    entry.kind().equals("REVERSAL")
                            ? label("gl.kind.REVERSAL", locale)
                                    + " — "
                                    + entry.reference()
                                    + (entry.description().isBlank()
                                            ? ""
                                            : " — " + entry.description())
                            : entry.description();
            rows.add(
                    new Row(
                            List.of(
                                    ApplicationTimestamps.date(entry.date()),
                                    label("gl.presentation.unavailable", locale),
                                    description,
                                    money(entry.debit(), locale),
                                    money(entry.credit(), locale),
                                    money(entry.runningBalance(), locale)),
                            false,
                            false,
                            entry.reference(),
                            null,
                            null,
                            entry.journalId()));
        }
        rows.add(
                new Row(
                        List.of(
                                "",
                                "",
                                total,
                                money(source.debit(), locale),
                                money(source.credit(), locale),
                                money(source.closing(), locale)),
                        true,
                        false,
                        total,
                        null,
                        null));
        var c = source.sourceCoverage();
        var evidence =
                List.of(
                        period,
                        label("common.status", locale) + ": " + label("status.DRAFT", locale),
                        label("gl.policy", locale) + ": " + source.policy(),
                        label("gl.account", locale)
                                + ": "
                                + source.account().code()
                                + " "
                                + source.account().name(),
                        label("gl.presentation.accountNumber", locale)
                                + ": "
                                + label("gl.presentation.unavailable", locale),
                        label("gl.presentation.matchingTransactions", locale)
                                + ": "
                                + source.matchingCount(),
                        label("gl.coverage.through", locale)
                                + ": "
                                + ApplicationTimestamps.date(c.through()),
                        label("gl.coverage.unposted", locale) + ": " + c.unposted(),
                        label("gl.coverage.unverified", locale) + ": " + c.unverified(),
                        label("gl.coverage.posted", locale) + ": " + c.posted(),
                        label("gl.coverage.opening", locale) + ": " + c.openingCovered(),
                        label("gl.coverage.amount", locale)
                                + ": "
                                + money(c.unresolvedAmount(), locale),
                        label("gl.coverage", locale)
                                + ": "
                                + label(
                                        c.complete()
                                                ? "gl.coverage.complete"
                                                : "gl.coverage.incomplete",
                                        locale),
                        label("gl.reconciliation", locale)
                                + ": "
                                + label(
                                        source.opening()
                                                                .add(source.debit())
                                                                .subtract(source.credit())
                                                                .compareTo(source.closing())
                                                        == 0
                                                ? "gl.reconciled"
                                                : "gl.errorReconciliation",
                                        locale));
        return new StatementDocument(
                label("gl.presentation.ledgerTitle", locale)
                        + " — "
                        + source.account().code()
                        + " "
                        + source.account().name(),
                "trial",
                true,
                -1,
                header,
                columns,
                rows,
                label("gl.presentation.outputNotice", locale),
                evidence);
    }
}
