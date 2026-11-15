package com.foresight.fcms.report.service;

import com.foresight.fcms.accounting.dto.StatementDocument;
import com.foresight.fcms.accounting.dto.StatementDocument.Column;
import com.foresight.fcms.accounting.dto.StatementDocument.Header;
import com.foresight.fcms.accounting.dto.StatementDocument.Row;
import com.foresight.fcms.accounting.service.AccountingDocumentPdfService;
import com.foresight.fcms.finance.report.service.FinancialReportService.Statement;
import com.foresight.fcms.shared.view.ApplicationTimestamps;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Operational fee figures on the original Microfinance income-statement stationery. */
@Service
public class ConsolidatedStatementPdfService {
    private final MessageSource messages;
    private final AccountingDocumentPdfService pdf;

    public ConsolidatedStatementPdfService(
            MessageSource messages, AccountingDocumentPdfService pdf) {
        this.messages = messages;
        this.pdf = pdf;
    }

    public StatementDocument document(Statement statement, Locale locale) {
        return document(statement, locale, Instant.now());
    }

    public byte[] render(Statement statement, Locale locale) {
        return render(statement, locale, Instant.now());
    }

    byte[] render(Statement statement, Locale locale, Instant generated) {
        return pdf.statement(document(statement, locale, generated), locale);
    }

    StatementDocument document(Statement s, Locale locale, Instant generated) {
        var rows = new ArrayList<Row>();
        rows.add(heading("gl.income", locale));
        for (var client : s.clients())
            if (client.received().signum() != 0)
                rows.add(
                        amount(
                                label("client.category." + client.category(), locale),
                                client.received(),
                                false,
                                locale));
        if (s.otherIncome().signum() != 0)
            rows.add(amount(label("nav.income", locale), s.otherIncome(), false, locale));
        rows.add(
                amount(
                        label("gl.presentation.totalIncome", locale),
                        s.totalIncome(),
                        true,
                        locale));
        rows.add(heading("gl.expenses", locale));
        for (var client : s.clients())
            if (client.costs().signum() != 0)
                rows.add(
                        amount(
                                label("client.category." + client.category(), locale),
                                client.costs(),
                                false,
                                locale));
        if (s.expenses().signum() != 0)
            rows.add(amount(label("report.paidExpenses", locale), s.expenses(), false, locale));
        rows.add(
                amount(
                        label("gl.presentation.totalExpenses", locale),
                        s.totalExpenses(),
                        true,
                        locale));
        rows.add(amount(label("gl.presentation.surplus", locale), s.netProfit(), true, locale));
        var details = new ArrayList<String>();
        details.add(label("report.clients", locale));
        for (var client : s.clients())
            details.add(
                    label("client.category." + client.category(), locale)
                            + ": "
                            + label("report.revenue", locale)
                            + " "
                            + money(client.received())
                            + " | "
                            + label("report.costs", locale)
                            + " "
                            + money(client.costs())
                            + " | "
                            + label("report.profit", locale)
                            + " "
                            + money(client.profit()));
        details.add(label("nav.income", locale) + ": " + money(s.otherIncome()));
        details.add(label("report.paidExpenses", locale) + ": " + money(s.expenses()));
        details.add(label("gl.presentation.surplus", locale) + ": " + money(s.netProfit()));
        return new StatementDocument(
                label("gl.presentation.title.income-statement", locale),
                "statement",
                false,
                3,
                new Header(
                        "FCMS",
                        label("report.statement", locale),
                        label("gl.presentation.period", locale)
                                + ": "
                                + ApplicationTimestamps.date(LocalDate.of(s.year(), 1, 1))
                                + " — "
                                + ApplicationTimestamps.date(LocalDate.of(s.year(), 12, 31))
                                + " | TZS",
                        label("gl.presentation.previous", locale)
                                + ": "
                                + ApplicationTimestamps.date(LocalDate.of(s.year() - 1, 1, 1))
                                + " — "
                                + ApplicationTimestamps.date(LocalDate.of(s.year() - 1, 12, 31)),
                        label("gl.presentation.generated", locale)
                                + ": "
                                + DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm z", locale)
                                        .withZone(ZoneId.of("Africa/Dar_es_Salaam"))
                                        .format(generated),
                        List.of()),
                List.of(
                        new Column(label("gl.presentation.account", locale), false, .12f),
                        new Column(label("common.description", locale), false, .40f),
                        new Column(label("gl.presentation.notes", locale), false, .08f),
                        new Column(label("gl.presentation.current", locale), true, .20f),
                        new Column(label("gl.presentation.previous", locale), true, .20f)),
                rows,
                label("gl.presentation.outputNotice", locale),
                details);
    }

    private Row heading(String key, Locale locale) {
        String value = label(key, locale);
        return new Row(List.of("", value, "", "", ""), false, true, value, null, null);
    }

    private Row amount(String label, BigDecimal value, boolean total, Locale locale) {
        return new Row(
                List.of("", label, "", money(value), label("gl.presentation.unavailable", locale)),
                total,
                false,
                label,
                null,
                null);
    }

    private String money(BigDecimal value) {
        return new DecimalFormat("#,##0.00;(#,##0.00)", DecimalFormatSymbols.getInstance(Locale.UK))
                .format(value);
    }

    private String label(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }
}
