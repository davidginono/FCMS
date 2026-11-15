package com.foresight.fcms.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.finance.expense.dto.ExpenseView;
import com.foresight.fcms.finance.expense.service.ExpenseCategoryService;
import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.finance.ledger.dto.LedgerView;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.dto.WorkView;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class OperationalReportPdfTests {
    final WorkService work = mock(WorkService.class);
    final ExpenseService expenses = mock(ExpenseService.class);
    final LedgerService ledgers = mock(LedgerService.class);
    final ExpenseCategoryService categories = mock(ExpenseCategoryService.class);
    final OperationalReportPdfService pdf;

    OperationalReportPdfTests() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        pdf =
                new OperationalReportPdfService(
                        work,
                        expenses,
                        ledgers,
                        messages,
                        new ReportPdfRenderer(),
                        "FCMS",
                        categories);
    }

    WorkView row(String description) {
        return new WorkView(
                7,
                "TASK",
                3,
                "Synthetic Staff",
                8L,
                "Synthetic Client",
                "Synthetic subject",
                description,
                LocalDateTime.of(2026, 11, 1, 10, 30),
                LocalDateTime.of(2026, 11, 2, 17, 15),
                "Support",
                "COMPLETED",
                "Evidence retained",
                new BigDecimal("33.37"),
                new BigDecimal("100.11"),
                "APPROVED",
                "Independent review",
                0,
                3,
                "Synthetic creator",
                null,
                "",
                null);
    }

    String text(byte[] bytes, String file) throws Exception {
        Path dir = Path.of(".verification/operational-exports");
        Files.createDirectories(dir);
        Files.write(dir.resolve(file + ".pdf"), bytes);
        try (var reader = new PdfReader(bytes)) {
            StringBuilder result = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                var size = reader.getPageSize(page);
                assertTrue(size.getWidth() > 590 && size.getWidth() < 850);
                result.append(new PdfTextExtractor(reader).getTextFromPage(page));
            }
            return result.toString();
        }
    }

    @Test
    void taskTotalsAreExactAndScopeIsRetainedInBothLanguages() throws Exception {
        var q = new RegisterQuery();
        q.setOwnerId(3L);
        q.setClientId(8L);
        q.setSearch("literal_%");
        q.setFrom(LocalDate.of(2026, 11, 1));
        q.setPage(4);
        q.setSize(10);
        when(work.report(WorkKind.TASK, q))
                .thenReturn(List.of(row("Activities"), row("Follow-up")));
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var doc =
                    pdf.workDocument(
                            WorkKind.TASK, List.of(row("Activities"), row("Follow-up")), q, locale);
            assertEquals(10, doc.columns().size());
            assertTrue(doc.period().contains("#3"));
            assertTrue(doc.period().contains("#8"));
            assertTrue(doc.period().contains("literal_%"));
            assertEquals(List.of("66.74", "200.22", "133.48"), doc.totals().subList(1, 4));
            String result = text(pdf.work(WorkKind.TASK, q, locale), "task-" + locale);
            for (String expected :
                    List.of("66.74", "200.22", "133.48", "Evidence retained", "Independent review"))
                assertTrue(result.contains(expected), result);
            assertEquals(4, q.getPage());
            assertEquals(10, q.getSize());
        }
        verify(work, times(2)).report(WorkKind.TASK, q);
    }

    @Test
    void longActivityTextContinuesWithoutLosingTheFinalWords() throws Exception {
        var q = new RegisterQuery();
        when(work.report(WorkKind.REPORT, q))
                .thenReturn(List.of(row("Activities ".repeat(1600) + "FINAL-ACTIVITY-731")));
        byte[] bytes = pdf.work(WorkKind.REPORT, q, Locale.ENGLISH);
        try (var reader = new PdfReader(bytes)) {
            assertTrue(reader.getNumberOfPages() > 2);
        }
        assertTrue(text(bytes, "long-staff-report").contains("FINAL-ACTIVITY-731"));
    }

    @Test
    void expensesRetainTotalReviewStateAndPaymentEvidence() throws Exception {
        var q = new ExpenseQuery();
        when(expenses.report(q))
                .thenReturn(
                        List.of(
                                new ExpenseView(
                                        2,
                                        LocalDate.of(2026, 11, 1),
                                        3,
                                        "Synthetic Staff",
                                        "Travel",
                                        "Synthetic Expense",
                                        "Synthetic Payee",
                                        new BigDecimal("99.09"),
                                        "2026",
                                        "PAID",
                                        "",
                                        "PAYMENT-97",
                                        0,
                                        null,
                                        "",
                                        null,
                                        "")));
        String result = text(pdf.expenses(q, Locale.forLanguageTag("sw")), "expenses-sw");
        for (String value : List.of("99.09", "Synthetic Payee", "PAYMENT-97", "Imelipwa"))
            assertTrue(result.contains(value), result);
    }

    @Test
    void leaveAndEmptyReportsRemainValidLocalizedDocuments() throws Exception {
        var q = new RegisterQuery();
        for (var kind : WorkKind.values()) {
            when(work.report(kind, q)).thenReturn(List.of());
            assertFalse(
                    text(pdf.work(kind, q, Locale.forLanguageTag("sw")), "empty-" + kind)
                            .isBlank());
        }
    }

    @Test
    void receiptRetainsSignedAmountUnitsAndBothReversalLinks() throws Exception {
        when(ledgers.find(LedgerKind.BULK, 9))
                .thenReturn(
                        new LedgerView(
                                9,
                                "BULK",
                                LocalDate.of(2026, 11, 1),
                                "Synthetic correction",
                                "SMS",
                                "REF-9",
                                "BANK",
                                new BigDecimal("-100.11"),
                                -13,
                                new BigDecimal("30.00"),
                                20,
                                3,
                                "Synthetic Staff",
                                5L,
                                10L,
                                "Synthetic Client"));
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            String result = text(pdf.receipt(LedgerKind.BULK, 9, locale), "receipt-" + locale);
            for (String expected :
                    List.of("-100.11", "-13", "#5", "#10", "REF-9", "Synthetic Staff"))
                assertTrue(result.contains(expected), result);
            assertFalse(result.contains("Field"));
        }
    }

    @Test
    void deniedSourcesCannotRenderDocuments() {
        var q = new RegisterQuery();
        when(work.report(WorkKind.TASK, q)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> pdf.work(WorkKind.TASK, q, Locale.ENGLISH));
        when(ledgers.find(LedgerKind.INCOME, 7)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(
                AccessDeniedException.class,
                () -> pdf.receipt(LedgerKind.INCOME, 7, Locale.ENGLISH));
    }

    @Test
    void overLimitReportsAreRejectedBeforeRendering() {
        var q = new RegisterQuery();
        when(work.report(WorkKind.TASK, q))
                .thenReturn(Collections.nCopies(1001, row("Activities")));
        assertThrows(
                IllegalArgumentException.class, () -> pdf.work(WorkKind.TASK, q, Locale.ENGLISH));
    }

    @Test
    void cloudBillingRetainsActualFeesAndCostsWithoutInventedUnpaidCharges() throws Exception {
        var q = new RegisterQuery();
        q.setStatus("UNPAID");
        byte[] bytes =
                pdf.billing(
                        2026,
                        List.of(
                                new com.foresight.fcms.client.service.CloudBillingService.Billing(
                                        1,
                                        "Synthetic Cloud",
                                        BigDecimal.ZERO,
                                        new BigDecimal("12.34"),
                                        "UNPAID")),
                        q,
                        Locale.ENGLISH);
        String result = text(bytes, "cloud-billing");
        assertTrue(result.contains("12.34"));
        assertTrue(result.contains("2026"));
        assertFalse(result.contains("120,000"));
    }

    @Test
    void cloudBillingOriginalFixturesRetainFontsTotalsPrintedTimeAndRecordedUnpaidCosts()
            throws Exception {
        class Capture extends ReportPdfRenderer {
            com.foresight.fcms.report.dto.OperationalDocument source;

            @Override
            public byte[] table(com.foresight.fcms.report.dto.OperationalDocument value) {
                source = value;
                return super.table(value);
            }
        }
        var renderer = new Capture();
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        var reports =
                new OperationalReportPdfService(
                        work, expenses, ledgers, messages, renderer, "FCMS", categories);
        var root = Path.of(".verification/cloud-billing-exports");
        Files.createDirectories(root);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            for (String state : List.of("PAID", "UNPAID", "ALL", "EMPTY")) {
                var query = new RegisterQuery();
                if (List.of("PAID", "UNPAID").contains(state)) query.setStatus(state);
                var rows =
                        state.equals("EMPTY")
                                ? List
                                        .<com.foresight.fcms.client.service.CloudBillingService
                                                        .Billing>
                                                of()
                                : List.of(
                                        new com.foresight.fcms.client.service.CloudBillingService
                                                .Billing(
                                                1,
                                                "Client",
                                                state.equals("UNPAID")
                                                        ? BigDecimal.ZERO
                                                        : new BigDecimal("123.45"),
                                                new BigDecimal("12.34"),
                                                state.equals("UNPAID") ? "UNPAID" : "PAID"));
                byte[] bytes = reports.billing(2026, rows, query, locale);
                String name = "BILLING-" + state + "-" + locale.getLanguage();
                Files.write(root.resolve("spring-original-" + name + ".pdf"), bytes);
                Files.writeString(
                        root.resolve("source-" + name + ".json"),
                        new tools.jackson.databind.json.JsonMapper()
                                .writeValueAsString(renderer.source));
                assertEquals(9, renderer.source.footer().size());
                assertTrue(renderer.source.footer().totalPages());
                assertTrue(
                        renderer.source
                                .footer()
                                .suffix()
                                .matches(
                                        ".*[0-9]{2} [A-Za-z]{3} [0-9]{4}"
                                                + " [0-9]{2}:[0-9]{2}:[0-9]{2}"));
                assertTrue(renderer.source.notes().isEmpty());
                assertEquals(5, renderer.source.totals().size());
                String content = text(bytes, "cloud-billing-fixture");
                assertFalse(content.contains("120,000"));
                assertTrue(content.contains("2026"));
                if (!state.equals("EMPTY")) assertTrue(content.contains("12.34"));
            }
        }
    }

    @Test
    void originalRendererComparisonFixtures() throws Exception {
        var task =
                new com.foresight.fcms.report.dto.OperationalDocument(
                        "TASK",
                        "Task Report",
                        "Date Range: 2026-11-01 to 2026-11-02",
                        "Page",
                        List.of(
                                "No",
                                "Users",
                                "Task Type",
                                "Client",
                                "Start Date",
                                "End Date",
                                "Activities",
                                "Allowance",
                                "Amount",
                                "Profit"),
                        List.of(10f, 25f, 30f, 60f, 25f, 25f, 30f, 20f, 20f, 20f),
                        List.of("C", "L", "L", "L", "C", "C", "L", "C", "C", "C"),
                        List.of(
                                List.of(
                                        "1",
                                        "Staff",
                                        "Support",
                                        "Client",
                                        "2026-11-01",
                                        "2026-11-02",
                                        "Training",
                                        "33.37",
                                        "100.11",
                                        "66.74")),
                        List.of("TOTAL", "33.37", "100.11", "66.74"),
                        List.of());
        var expense =
                new com.foresight.fcms.report.dto.OperationalDocument(
                        "EXPENSE",
                        "Expense Report",
                        "From: Nov 01, 2026 To: Nov 02, 2026",
                        "Page",
                        List.of("No", "Category", "Date", "Description", "Amount"),
                        List.of(10f, 50f, 25f, 80f, 25f),
                        List.of("C", "C", "C", "L", "R"),
                        List.of(
                                List.of(
                                        "1",
                                        "Travel",
                                        "Nov 01, 2026",
                                        "Synthetic expense",
                                        "99.09")),
                        List.of("Total Amount", "99.09"),
                        List.of());
        var dir = Path.of(".verification/operational-exports");
        Files.createDirectories(dir);
        for (var doc : List.of(task, expense)) {
            Files.writeString(
                    dir.resolve("source-" + doc.kind() + ".json"),
                    new tools.jackson.databind.json.JsonMapper().writeValueAsString(doc));
            Files.write(
                    dir.resolve("spring-original-" + doc.kind() + ".pdf"),
                    new ReportPdfRenderer().table(doc));
        }
    }

    @Test
    void longFilterContextWrapsWithoutLosingSelectedScope() throws Exception {
        var query = new RegisterQuery();
        query.setSearch("X".repeat(87) + "-FILTER-END");
        query.setFrom(LocalDate.of(2026, 11, 1));
        query.setTo(LocalDate.of(2026, 11, 2));
        query.setOwnerId(123456789L);
        query.setClientId(987654321L);
        when(work.report(WorkKind.TASK, query)).thenReturn(List.of(row("Synthetic activity")));
        String document =
                text(pdf.work(WorkKind.TASK, query, Locale.ENGLISH), "long-filter-task")
                        .replaceAll("\\s+", "");
        assertTrue(document.contains("FILTER-END"));
        assertTrue(document.contains("#123456789"));
        assertTrue(document.contains("#987654321"));
    }

    @Test
    void staffAndClientReportsUseTheirOriginalDistinctStationery() throws Exception {
        var root = Path.of(".verification/operational-exports");
        Files.createDirectories(root);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            for (boolean byStaff : List.of(false, true)) {
                var query = new RegisterQuery();
                query.setFrom(LocalDate.of(2026, 11, 1));
                query.setTo(LocalDate.of(2026, 11, 2));
                if (byStaff) query.setOwnerId(3L);
                else query.setClientId(8L);
                var doc =
                        pdf.workDocument(
                                WorkKind.REPORT, List.of(row("Software training")), query, locale);
                assertEquals(byStaff ? 6 : 7, doc.columns().size());
                assertFalse(doc.footer().totalPages());
                assertEquals("FCMS", doc.headings().get(byStaff ? 0 : 3).text());
                assertTrue(doc.notes().stream().anyMatch(n -> n.contains("Independent review")));
                assertTrue(
                        doc.notes().stream()
                                .anyMatch(n ->
                                        n.contains(
                                                locale.getLanguage().equals("sw")
                                                        ? "Matokeo: Imekamilika"
                                                        : "Outcome: Completed")));
                var stationery =
                        new com.foresight.fcms.report.dto.OperationalDocument(
                                doc.kind(),
                                doc.title(),
                                doc.period(),
                                doc.pageLabel(),
                                doc.columns(),
                                doc.widths(),
                                doc.aligns(),
                                doc.rows(),
                                doc.totals(),
                                List.of(),
                                doc.headings(),
                                doc.footer(),
                                doc.emptyMessage());
                String name = doc.kind() + "-" + locale.getLanguage();
                Files.writeString(
                        root.resolve("source-" + name + ".json"),
                        new tools.jackson.databind.json.JsonMapper()
                                .writeValueAsString(stationery));
                Files.write(
                        root.resolve("spring-original-" + name + ".pdf"),
                        new ReportPdfRenderer().table(stationery));
            }
        }
    }

    @Test
    void categorySummaryUsesOriginalStationeryAndExactCentsInBothLanguages() throws Exception {
        var query = new com.foresight.fcms.finance.expense.dto.ExpenseCategoryQuery();
        query.setYear("2026");
        var source =
                new ExpenseCategoryService.Export(
                        List.of(
                                new ExpenseCategoryService.Category(
                                        "Travel", new BigDecimal("12.34")),
                                new ExpenseCategoryService.Category(
                                        "Utilities", new BigDecimal("99.09"))),
                        new BigDecimal("111.43"));
        when(categories.export(query)).thenReturn(source);
        for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("sw"))) {
            var doc = pdf.expenseCategoryDocument(source, query, locale);
            assertEquals(0, doc.footer().size());
            assertTrue(doc.headings().get(1).text().contains("111.43"));
            assertEquals(List.of(20f, 120f, 40f), doc.widths());
            String name = "EXPENSE_CATEGORIES-" + locale.getLanguage();
            var dir = Path.of(".verification/operational-exports");
            Files.createDirectories(dir);
            Files.writeString(
                    dir.resolve("source-" + name + ".json"),
                    new tools.jackson.databind.json.JsonMapper().writeValueAsString(doc));
            byte[] bytes = pdf.expenseCategories(query, locale);
            Files.write(dir.resolve("spring-original-" + name + ".pdf"), bytes);
            var rendered = text(bytes, "category-summary-" + locale.getLanguage());
            for (var value : List.of("12.34", "99.09", "111.43", "2026", "Travel", "Utilities"))
                assertTrue(rendered.contains(value), rendered);
            assertFalse(rendered.contains("Page"));
        }
    }

    @Test
    void emptySelectedStaffReportDoesNotInventAnIdentity() {
        var query = new RegisterQuery();
        query.setOwnerId(999L);
        var doc = pdf.workDocument(WorkKind.REPORT, List.of(), query, Locale.ENGLISH);
        assertEquals("STAFF NAME: #999", doc.headings().get(2).text());
        assertTrue(doc.rows().isEmpty());
    }
}
