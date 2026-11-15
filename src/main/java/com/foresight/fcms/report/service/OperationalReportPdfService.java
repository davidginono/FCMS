package com.foresight.fcms.report.service;

import com.foresight.fcms.accounting.view.VoucherStationery;
import com.foresight.fcms.accounting.view.VoucherStationery.Field;
import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.finance.expense.dto.ExpenseView;
import com.foresight.fcms.finance.expense.service.ExpenseCategoryService;
import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.report.dto.OperationalDocument;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.shared.view.ApplicationTimestamps;
import com.foresight.fcms.workforce.dto.WorkView;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Authorized, bounded snapshots are read before PDF generation, outside a database transaction. */
@Service
public class OperationalReportPdfService {
    private final WorkService work;
    private final ExpenseService expenses;
    private final LedgerService ledgers;
    private final MessageSource messages;
    private final ReportPdfRenderer renderer;
    private final String company;
    private final ExpenseCategoryService expenseCategories;

    public OperationalReportPdfService(
            WorkService work,
            ExpenseService expenses,
            LedgerService ledgers,
            MessageSource messages,
            ReportPdfRenderer renderer,
            @org.springframework.beans.factory.annotation.Value("${fcms.company.name:FCMS}")
                    String company,
            ExpenseCategoryService expenseCategories) {
        this.work = work;
        this.expenses = expenses;
        this.ledgers = ledgers;
        this.messages = messages;
        this.renderer = renderer;
        this.company = company;
        this.expenseCategories = expenseCategories;
    }

    private String label(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private String status(String value, Locale locale) {
        return messages.getMessage("status." + value, null, value, locale);
    }

    private String money(BigDecimal value, Locale locale) {
        var format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private List<String> labels(Locale locale, String... keys) {
        return java.util.Arrays.stream(keys).map(key -> label(key, locale)).toList();
    }

    private String context(RegisterQuery query, Locale locale, boolean dates, boolean owners) {
        var values = new ArrayList<String>();
        if (query instanceof com.foresight.fcms.workforce.dto.WorkQuery workQuery
                && workQuery.getDateBasis().equals("CREATED"))
            values.add(label("work.dateBasis", locale) + ": " + label("work.createdDate", locale));
        if (query instanceof ExpenseQuery expense) {
            if (!expense.getYear().isBlank())
                values.add(label("common.year", locale) + ": " + expense.getYear());
            if (!expense.getCategory().isBlank())
                values.add(label("ledger.category", locale) + ": " + expense.getCategory());
        }
        if (dates && query.getFrom() != null)
            values.add(
                    label("common.from", locale)
                            + ": "
                            + ApplicationTimestamps.date(query.getFrom()));
        if (dates && query.getTo() != null)
            values.add(
                    label("common.to", locale) + ": " + ApplicationTimestamps.date(query.getTo()));
        if (!query.getSearch().isBlank())
            values.add(label("common.search", locale) + ": " + query.getSearch());
        if (!query.getStatus().isBlank())
            values.add(label("common.status", locale) + ": " + status(query.getStatus(), locale));
        if (owners && query.getOwnerId() != null)
            values.add(label("common.staff", locale) + ": #" + query.getOwnerId());
        if (owners && query.getClientId() != null)
            values.add(label("common.client", locale) + ": #" + query.getClientId());
        return values.isEmpty() ? label("common.all", locale) : String.join(" | ", values);
    }

    public byte[] work(WorkKind kind, RegisterQuery query, Locale locale) {
        return table(workDocument(kind, work.report(kind, query), query, locale));
    }

    public byte[] expenses(ExpenseQuery query, Locale locale) {
        return table(expenseDocument(expenses.report(query), query, locale));
    }

    public byte[] expenseCategories(ExpenseQuery query, Locale locale) {
        return table(expenseCategoryDocument(expenseCategories.export(query), query, locale));
    }

    OperationalDocument expenseCategoryDocument(
            ExpenseCategoryService.Export source, ExpenseQuery query, Locale locale) {
        var rows = new ArrayList<List<String>>();
        for (var row : source.rows())
            rows.add(
                    List.of(
                            String.valueOf(rows.size() + 1),
                            row.category(),
                            money(row.amount(), locale)));
        String title = label("expense.categories.reportYear", locale) + " " + query.getYear();
        String total =
                label("expense.categories.totalAmount", locale)
                        + ": "
                        + money(source.total(), locale);
        var filterQuery = new RegisterQuery();
        filterQuery.setSearch(query.getSearch());
        filterQuery.setStatus(query.getStatus());
        filterQuery.setOwnerId(query.getOwnerId());
        filterQuery.setFrom(query.getFrom());
        filterQuery.setTo(query.getTo());
        String filters = context(filterQuery, locale, true, true);
        return new OperationalDocument(
                "EXPENSE_CATEGORIES",
                title,
                "",
                label("legacyReport.page", locale),
                labels(
                        locale,
                        "legacyReport.no",
                        "expense.categories.name",
                        "expense.categories.amount"),
                List.of(20f, 120f, 40f),
                List.of("C", "L", "R"),
                rows,
                List.of(),
                filters.equals(label("common.all", locale)) ? List.of() : List.of(filters),
                List.of(
                        new OperationalDocument.Heading(title, 16, 10, 10, true, false),
                        new OperationalDocument.Heading(total, 12, 10, 10, false, false)),
                new OperationalDocument.Footer(false, 0),
                "");
    }

    OperationalDocument workDocument(
            WorkKind kind, List<WorkView> source, RegisterQuery query, Locale locale) {
        var rows = new ArrayList<List<String>>();
        var notes = new ArrayList<String>();
        BigDecimal allowance = BigDecimal.ZERO, amount = BigDecimal.ZERO;
        var noted = new java.util.HashSet<Long>();
        for (var row : source) {
            String n = Integer.toString(rows.size() + 1);
            if (kind == WorkKind.TASK) {
                rows.add(
                        List.of(
                                n,
                                row.owner(),
                                row.workType(),
                                row.client(),
                                ApplicationTimestamps.display(row.startAt()),
                                ApplicationTimestamps.display(row.endAt()),
                                row.description(),
                                money(row.allowance(), locale),
                                money(row.amount(), locale),
                                money(row.amount().subtract(row.allowance()), locale)));
                allowance = allowance.add(row.allowance());
                amount = amount.add(row.amount());
            } else {
                rows.add(
                        List.of(
                                n,
                                kind == WorkKind.REPORT ? row.activityStaff() : row.owner(),
                                kind == WorkKind.LEAVE ? row.workType() : row.client(),
                                ApplicationTimestamps.display(row.startAt()),
                                ApplicationTimestamps.display(row.endAt()),
                                status(row.status(), locale),
                                row.description()));
            }
            String details =
                    "#" + row.id() + " | " + row.subject() + " | " + status(row.status(), locale);
            if (!row.outcome().isBlank())
                details +=
                        " | "
                                + label("work.outcome", locale)
                                + ": "
                                + status(row.outcome(), locale);
            if (!row.explanation().isBlank())
                details += " | " + label("work.explanation", locale) + ": " + row.explanation();
            if (!row.reviewComment().isBlank())
                details += " | " + label("work.review", locale) + ": " + row.reviewComment();
            if (kind != WorkKind.REPORT || noted.add(row.id())) notes.add(details);
        }
        if (kind == WorkKind.REPORT) return staffReportDocument(source, rows, notes, query, locale);
        boolean task = kind == WorkKind.TASK;
        var widths =
                task
                        ? List.of(10f, 25f, 30f, 60f, 25f, 25f, 30f, 20f, 20f, 20f)
                        : List.of(10f, 20f, 50f, 20f, 20f, 20f, 50f);
        return new OperationalDocument(
                kind.name(),
                label(kind.title, locale),
                context(query, locale, true, true),
                label("legacyReport.page", locale),
                task
                        ? labels(
                                locale,
                                "legacyReport.no",
                                "common.staff",
                                "work.type",
                                "common.client",
                                "work.start",
                                "work.end",
                                "operational.activities",
                                "work.allowance",
                                "common.amount",
                                "operational.profit")
                        : labels(
                                locale,
                                "legacyReport.no",
                                "common.staff",
                                kind == WorkKind.LEAVE ? "work.type" : "common.client",
                                "work.start",
                                "work.end",
                                "common.status",
                                "common.description"),
                widths,
                task
                        ? List.of("C", "L", "L", "L", "C", "C", "L", "C", "C", "C")
                        : List.of("C", "C", "L", "C", "C", "C", "L"),
                rows,
                task
                        ? List.of(
                                label("common.total", locale),
                                money(allowance, locale),
                                money(amount, locale),
                                money(amount.subtract(allowance), locale))
                        : List.of(),
                notes);
    }

    private OperationalDocument staffReportDocument(
            List<WorkView> source,
            List<List<String>> rows,
            List<String> notes,
            RegisterQuery query,
            Locale locale) {
        boolean byStaff = query.getOwnerId() != null;
        String period = label("legacyReport.range", locale) + ": " + dateRange(query, locale);
        var headings = new ArrayList<OperationalDocument.Heading>();
        if (byStaff) {
            String owner =
                    source.isEmpty() ? "#" + query.getOwnerId() : source.getFirst().activityStaff();
            headings.add(new OperationalDocument.Heading(company, 20, 10, 10, true, false));
            headings.add(
                    new OperationalDocument.Heading(
                            label("operational.activitySummary", locale).toUpperCase(locale),
                            12,
                            10,
                            0,
                            true,
                            false));
            headings.add(
                    new OperationalDocument.Heading(
                            label("operational.staffName", locale).toUpperCase(locale)
                                    + ": "
                                    + owner,
                            11,
                            10,
                            0,
                            true,
                            false));
            headings.add(new OperationalDocument.Heading(period, 10, 8, 0, true, true));
            rows =
                    rows.stream()
                            .map(row ->
                                    List.of(
                                            row.get(0),
                                            row.get(2),
                                            row.get(3),
                                            row.get(4),
                                            row.get(5),
                                            row.get(6)))
                            .toList();
        } else {
            String client =
                    query.getClientId() == null
                            ? label("operational.allClients", locale)
                            : source.isEmpty()
                                    ? "#" + query.getClientId()
                                    : source.getFirst().client();
            headings.add(
                    new OperationalDocument.Heading(
                            label("operational.clientName", locale) + ": " + client,
                            12,
                            10,
                            0,
                            true,
                            true));
            headings.add(
                    new OperationalDocument.Heading(
                            label("operational.activitySummary", locale).toUpperCase(locale),
                            8,
                            10,
                            0,
                            false,
                            true));
            headings.add(new OperationalDocument.Heading(period, 9, 7, 5, true, true));
            headings.add(new OperationalDocument.Heading(company, 20, 10, 10, true, false));
        }
        String context = context(query, locale, true, true);
        if (!context.equals(label("common.all", locale))) notes.addFirst(context);
        return new OperationalDocument(
                byStaff ? "REPORT_STAFF" : "REPORT_CLIENT",
                label("nav.staffReports", locale),
                period,
                label("legacyReport.page", locale),
                byStaff
                        ? labels(
                                locale,
                                "legacyReport.no",
                                "operational.clientName",
                                "operational.startDate",
                                "operational.endDate",
                                "common.status",
                                "operational.activities")
                        : labels(
                                locale,
                                "legacyReport.no",
                                "operational.staffName",
                                "operational.clientName",
                                "operational.startDate",
                                "operational.endDate",
                                "common.status",
                                "operational.activities"),
                byStaff
                        ? List.of(10f, 50f, 20f, 20f, 20f, 70f)
                        : List.of(10f, 20f, 50f, 20f, 20f, 20f, 50f),
                byStaff
                        ? List.of("C", "L", "C", "C", "C", "L")
                        : List.of("C", "C", "L", "C", "C", "C", "L"),
                rows,
                List.of(),
                notes,
                headings,
                new OperationalDocument.Footer(false, 8),
                "");
    }

    private String dateRange(RegisterQuery query, Locale locale) {
        if (query.getFrom() == null && query.getTo() == null)
            return label("operational.allDates", locale);
        if (query.getFrom() == null)
            return label("common.to", locale) + " " + ApplicationTimestamps.date(query.getTo());
        if (query.getTo() == null)
            return label("common.from", locale) + " " + ApplicationTimestamps.date(query.getFrom());
        return ApplicationTimestamps.date(query.getFrom())
                + " - "
                + ApplicationTimestamps.date(query.getTo());
    }

    OperationalDocument expenseDocument(
            List<ExpenseView> source, RegisterQuery query, Locale locale) {
        var rows = new ArrayList<List<String>>();
        var notes = new ArrayList<String>();
        BigDecimal amount = BigDecimal.ZERO;
        for (var row : source) {
            rows.add(
                    List.of(
                            Integer.toString(rows.size() + 1),
                            row.category(),
                            ApplicationTimestamps.date(row.date()),
                            row.description(),
                            money(row.amount(), locale)));
            amount = amount.add(row.amount());
            notes.add(
                    "#"
                            + row.id()
                            + " | "
                            + row.owner()
                            + " | "
                            + row.payee()
                            + " | "
                            + status(row.status(), locale)
                            + (row.paymentReference().isBlank()
                                    ? ""
                                    : " | " + row.paymentReference()));
        }
        return new OperationalDocument(
                "EXPENSE",
                label("nav.expenses", locale),
                context(query, locale, true, true),
                label("legacyReport.page", locale),
                labels(
                        locale,
                        "legacyReport.no",
                        "ledger.category",
                        "common.date",
                        "common.description",
                        "common.amount"),
                List.of(10f, 50f, 25f, 80f, 25f),
                List.of("C", "C", "C", "L", "R"),
                rows,
                List.of(label("common.total", locale), money(amount, locale)),
                notes);
    }

    public byte[] billing(
            int year,
            List<com.foresight.fcms.client.service.CloudBillingService.Billing> source,
            RegisterQuery query,
            Locale locale) {
        var rows = new ArrayList<List<String>>();

        BigDecimal fee = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (var row : source) {
            rows.add(
                    List.of(
                            Integer.toString(rows.size() + 1),
                            row.name(),
                            row.fee().signum() == 0 ? "" : money(row.fee(), locale),
                            row.cost().signum() == 0 ? "" : money(row.cost(), locale),
                            Integer.toString(year)));
            fee = fee.add(row.fee());
            cost = cost.add(row.cost());
        }
        String filters = context(query, locale, false, false);
        String title =
                label("cloud.billing", locale)
                        + " - "
                        + year
                        + (filters.equals(label("common.all", locale)) ? "" : " - " + filters);
        String printed =
                "   |   "
                        + label("legacyReport.printedShort", locale)
                        + ": "
                        + java.time.format.DateTimeFormatter.ofPattern(
                                        "dd MMM uuuu HH:mm:ss", Locale.ENGLISH)
                                .withZone(java.time.ZoneId.of("Africa/Dar_es_Salaam"))
                                .format(java.time.Instant.now());
        return table(
                new OperationalDocument(
                        "BILLING",
                        title,
                        "",
                        label("legacyReport.page", locale),
                        labels(
                                locale,
                                "legacyReport.no",
                                "common.client",
                                "cloud.fee",
                                "cloud.cost",
                                "report.year"),
                        List.of(12f, 110f, 45f, 45f, 30f),
                        List.of("C", "L", "C", "C", "C"),
                        rows,
                        List.of(
                                label("common.total", locale),
                                "",
                                money(fee, locale),
                                money(cost, locale),
                                ""),
                        List.of(),
                        List.of(),
                        new OperationalDocument.Footer(true, 9, printed),
                        ""));
    }

    private byte[] table(OperationalDocument source) {
        return renderer.table(source);
    }

    private Field field(String key, String value, Locale locale) {
        return new Field(label(key, locale), value);
    }

    /** An operational entry is not evidence of a GL posting or independently approved payment. */
    public byte[] receipt(LedgerKind kind, long id, Locale locale) {
        var r = ledgers.find(kind, id);
        var posting = new ArrayList<Field>();
        posting.add(field("ledger.recordedBy", r.actor(), locale));
        if (r.reversalOf() != null)
            posting.add(field("ledger.reversalOf", "#" + r.reversalOf(), locale));
        if (r.reversedBy() != null)
            posting.add(field("ledger.reversedBy", "#" + r.reversedBy(), locale));
        String state =
                label(r.reversedBy() != null ? "status.REVERSED" : "operational.recorded", locale);
        var source =
                new VoucherStationery.Source(
                        label("operational.entry", locale),
                        "FCMS",
                        "",
                        "#" + r.id(),
                        state,
                        List.of(
                                field("common.date", ApplicationTimestamps.date(r.date()), locale),
                                field("common.reference", r.reference(), locale)),
                        label("common.client", locale),
                        List.of(field("common.name", r.client(), locale)),
                        label(kind.title, locale),
                        List.of(
                                field("ledger.category", r.category(), locale),
                                field("ledger.paymentMode", r.paymentMode(), locale)),
                        labels(locale, "legacyReport.no", "common.description", "common.amount"),
                        new float[] {.06f, .74f, .20f},
                        List.of(List.of("1", r.description(), money(r.amount(), locale))),
                        label("gl.document.amountWords", locale),
                        com.foresight.fcms.accounting.view.AmountInWords.amountInWords(
                                r.amount(), locale),
                        List.of(
                                field("common.amount", money(r.amount(), locale) + " TZS", locale),
                                field("ledger.units", Long.toString(r.units()), locale)),
                        label("common.description", locale),
                        r.description(),
                        label("operational.record", locale),
                        posting,
                        label("legacyReport.page", locale),
                        label("gl.document.continued", locale),
                        null,
                        "",
                        List.of());
        return renderer.voucher(source);
    }
}
