package com.foresight.fcms.report.service;

import com.foresight.fcms.finance.ledger.dto.*;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.report.dto.LegacyLedgerDocument;
import com.foresight.fcms.report.dto.LegacyLedgerDocument.*;
import com.foresight.fcms.report.view.LegacyLedgerStationery;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LedgerReportPdfService {
    private final LedgerService ledgers;
    private final MessageSource messages;
    private final LegacyLedgerStationery pdf;

    public LedgerReportPdfService(
            LedgerService ledgers, MessageSource messages, LegacyLedgerStationery pdf) {
        this.ledgers = ledgers;
        this.messages = messages;
        this.pdf = pdf;
    }

    private String label(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private String money(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.UK))
                .format(value);
    }

    private String units(long value) {
        return NumberFormat.getIntegerInstance(Locale.UK).format(value);
    }

    public byte[] render(LedgerKind kind, RegisterQuery query, Locale locale) {
        return pdf.render(document(kind, ledgers.reportSnapshot(kind, query), query, locale));
    }

    public LegacyLedgerDocument document(
            LedgerKind kind, LedgerReport source, RegisterQuery query, Locale locale) {
        var preface = new ArrayList<Preface>();
        if (kind == LedgerKind.BULK) {
            preface.add(
                    new Preface(
                            label("legacyReport.currentUnits", locale)
                                    + ": "
                                    + units(source.currentUnits()),
                            true,
                            10));
            preface.add(
                    new Preface(
                            label("legacyReport.bulkProfit", locale)
                                    + ": "
                                    + money(source.movement()),
                            true,
                            10));
        }
        if (query.getFrom() != null || query.getTo() != null)
            preface.add(
                    new Preface(
                            label("legacyReport.range", locale)
                                    + ": "
                                    + (query.getFrom() == null ? "" : query.getFrom())
                                    + " - "
                                    + (query.getTo() == null ? "" : query.getTo()),
                            false,
                            6));
        if (!query.getSearch().isBlank())
            preface.add(
                    new Preface(
                            label("common.search", locale) + ": " + query.getSearch(), false, 6));
        boolean filtered =
                query.getFrom() != null || query.getTo() != null || !query.getSearch().isBlank();
        var headers = new ArrayList<String>();
        List<Float> widths;
        List<String> aligns;
        String[] keys;
        switch (kind) {
            case PETTY -> {
                keys = new String[] {"no", "date", "type", "category", "description", "in", "out"};
                widths = List.of(10f, 25f, 30f, 25f, 140f, 22f, 25f);
                aligns = List.of("C", "L", "L", "L", "L", "R", "R");
            }
            case BULK -> {
                keys =
                        new String[] {
                            "no",
                            "actor",
                            "date",
                            "category",
                            "description",
                            "in",
                            "out",
                            "unitsIn",
                            "unitsOut",
                            "unitsBefore",
                            "unitsAfter"
                        };
                widths = List.of(8f, 24f, 24f, 30f, 45f, 25f, 25f, 17f, 17f, 31f, 31f);
                aligns = List.of("C", "L", "L", "L", "L", "R", "R", "R", "R", "R", "R");
            }
            case CLOUD -> {
                keys = new String[] {"no", "date", "in", "out"};
                widths = List.of(10f, 40f, 70f, 70f);
                aligns = List.of("C", "L", "R", "R");
            }
            default -> {
                keys = new String[] {"no", "actor", "date", "category", "description", "in", "out"};
                widths = List.of(10f, 30f, 25f, 30f, 132f, 25f, 25f);
                aligns = List.of("C", "L", "L", "L", "L", "R", "R");
            }
        }
        for (String key : keys) headers.add(label("legacyReport." + key, locale));
        var rows = new ArrayList<List<String>>();
        var notes = new ArrayList<String>();
        for (var row : source.rows()) {
            String number = Integer.toString(rows.size() + 1);
            String correction =
                    row.reversalOf() != null
                            ? label("ledger.reversalOf", locale) + " #" + row.reversalOf()
                            : row.reversedBy() != null
                                    ? label("ledger.reversedBy", locale) + " #" + row.reversedBy()
                                    : "";
            String description =
                    row.description()
                            + "\n"
                            + label("common.reference", locale)
                            + ": "
                            + row.reference()
                            + (correction.isBlank() ? "" : "\n" + correction);
            String in = money(row.amount().max(BigDecimal.ZERO)),
                    out = money(row.amount().min(BigDecimal.ZERO).negate());
            String date = row.date().toString();
            rows.add(
                    switch (kind) {
                        case PETTY ->
                                List.of(
                                        number,
                                        date,
                                        label(
                                                row.amount().signum() < 0
                                                        ? "legacyReport.withdrawal"
                                                        : "legacyReport.deposit",
                                                locale),
                                        row.category(),
                                        description,
                                        in,
                                        out);
                        case BULK ->
                                List.of(
                                        number,
                                        row.actor(),
                                        date,
                                        row.category(),
                                        description,
                                        in,
                                        out,
                                        units(Math.max(0, row.units())),
                                        units(row.units() < 0 ? Math.negateExact(row.units()) : 0),
                                        units(Math.subtractExact(row.unitBalance(), row.units())),
                                        units(row.unitBalance()));
                        case CLOUD -> List.of(number, date, in, out);
                        default ->
                                List.of(
                                        number,
                                        row.actor(),
                                        date,
                                        row.category(),
                                        description,
                                        in,
                                        out);
                    });
            if (kind == LedgerKind.CLOUD && !correction.isBlank())
                notes.add(
                        label("legacyReport.no", locale)
                                + " "
                                + number
                                + " | #"
                                + row.id()
                                + " | "
                                + row.reference()
                                + " | "
                                + correction);
        }
        var totals = new ArrayList<Total>();
        if (kind == LedgerKind.BULK)
            totals.add(
                    new Total(
                            List.of(
                                    label("legacyReport.total", locale),
                                    money(source.amountIn()),
                                    money(source.amountOut()),
                                    units(source.unitsIn()),
                                    units(source.unitsOut()),
                                    ""),
                            List.of(131f, 25f, 25f, 17f, 17f, 62f),
                            0));
        else if (kind == LedgerKind.CLOUD)
            totals.add(
                    new Total(
                            List.of(
                                    label("legacyReport.totalBalance", locale),
                                    money(source.movement())),
                            List.of(120f, 70f),
                            5));
        else if (kind == LedgerKind.PETTY) {
            totals.add(
                    new Total(
                            List.of(
                                    label(
                                            filtered
                                                    ? "legacyReport.currentBalance"
                                                    : "legacyReport.totalBalance",
                                            locale),
                                    money(source.currentBalance())),
                            List.of(252f, 25f),
                            5));
            if (filtered)
                totals.add(
                        new Total(
                                List.of(
                                        label("legacyReport.selectedMovement", locale),
                                        money(source.movement())),
                                List.of(252f, 25f),
                                0));
        } else {
            totals.add(
                    new Total(
                            List.of(
                                    label("legacyReport.total", locale),
                                    money(source.amountIn()),
                                    money(source.amountOut())),
                            List.of(227f, 25f, 25f),
                            0));
            totals.add(
                    new Total(
                            List.of(
                                    label("legacyReport.selectedMovement", locale),
                                    money(source.movement())),
                            List.of(252f, 25f),
                            5));
        }
        return new LegacyLedgerDocument(
                kind.name(),
                label("legacyReport.title." + kind, locale),
                label("legacyReport.printed", locale)
                        + ": "
                        + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                                .withZone(ZoneId.of("Africa/Dar_es_Salaam"))
                                .format(source.generatedAt())
                        + " EAT | TZS",
                label("legacyReport.page", locale),
                preface,
                headers,
                widths,
                aligns,
                rows,
                totals,
                notes);
    }
}
