package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.AccountingViews;
import com.foresight.fcms.accounting.dto.LedgerQuery;
import com.foresight.fcms.accounting.dto.TrialQuery;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.report')")
@Controller
@RequestMapping("/accounting")
public class FinancialStatementsController {
    private final AccountingService accounting;
    private final StatementService reports;
    private final StatementPresentation presentation;
    private final AccountingDocumentPdfService documentPdf;

    public FinancialStatementsController(
            AccountingService accounting,
            StatementService reports,
            StatementPresentation presentation,
            AccountingDocumentPdfService documentPdf) {
        this.accounting = accounting;
        this.reports = reports;
        this.presentation = presentation;
        this.documentPdf = documentPdf;
    }

    @GetMapping("/statements")
    public String overview() {
        return "redirect:/accounting/statements/income-statement";
    }

    @GetMapping(
            "/statements/{type:income-statement|balance-sheet|cash-flow|changes-in-equity|notes}")
    public String statement(
            @PathVariable String type,
            @ModelAttribute("query") RegisterQuery q,
            Model m,
            Locale locale) {
        dates(q);
        m.addAttribute("reportType", type);
        m.addAttribute("reportTitle", "gl.statement." + type);
        m.addAttribute("breadcrumbCurrent", "gl.statement." + type);
        m.addAttribute("book", accounting.books().orElse(null));
        if (accounting.books().map(b -> b.ready()).orElse(false)) {
            var comparison = reports.comparison(type, q.getFrom(), q.getTo());
            statementModel(type, comparison, m, locale);
        }
        return "accounting/statement";
    }

    @GetMapping(
            "/statements/{type:income-statement|balance-sheet|cash-flow|changes-in-equity|notes}/print")
    public String statementPrint(
            @PathVariable String type,
            @ModelAttribute("query") RegisterQuery q,
            Model m,
            Locale locale) {
        if (type.equals("notes")) {
            dates(q);
            statementModel(type, reports.notesExport(q.getFrom(), q.getTo()), m, locale);
        } else if (Set.of("cash-flow", "changes-in-equity").contains(type)) {
            dates(q);
            statementModel(type, reports.cashEquityExport(type, q.getFrom(), q.getTo()), m, locale);
        } else statement(type, q, m, locale);
        return "accounting/statement-print";
    }

    @GetMapping(
            "/statements/{type:income-statement|balance-sheet|cash-flow|changes-in-equity|notes}/print.pdf")
    public ResponseEntity<byte[]> statementPdf(
            @PathVariable String type, @ModelAttribute RegisterQuery q, Locale locale) {
        dates(q);
        var report =
                type.equals("notes")
                        ? reports.notesExport(q.getFrom(), q.getTo())
                        : Set.of("cash-flow", "changes-in-equity").contains(type)
                                ? reports.cashEquityExport(type, q.getFrom(), q.getTo())
                                : reports.comparison(type, q.getFrom(), q.getTo());
        return output(documentPdf.statement(presentation.statement(type, report, locale), locale));
    }

    private void statementModel(
            String type, StatementService.Comparison comparison, Model m, Locale locale) {
        var document = presentation.statement(type, comparison, locale);
        m.addAttribute("reportType", type);
        m.addAttribute("reportTitle", "gl.statement." + type);
        m.addAttribute("statement", comparison.current());
        m.addAttribute("comparison", comparison);
        m.addAttribute("statementDocument", document);
        m.addAttribute("statementEmpty", type.equals("notes") && document.rows().isEmpty());
    }

    @GetMapping("/trial-balance")
    public String trial(@ModelAttribute("query") TrialQuery q, Model m, Locale locale) {
        dates(q);
        m.addAttribute("baseUrl", "/accounting/trial-balance");
        m.addAttribute("resourceKey", "gl-trial");
        m.addAttribute("statementAdditionalSorts", Map.of("classification", "gl.classification"));
        m.addAttribute("today", com.foresight.fcms.shared.time.ApplicationCalendar.today());
        m.addAttribute("reportType", "trial");
        m.addAttribute("reportTitle", "gl.trialBalance");
        m.addAttribute(
                "statementSortKeys",
                List.of("code", "name", "opening", "debit", "credit", "netChange", "closing"));
        m.addAttribute("book", accounting.books().orElse(null));
        if (accounting.books().map(b -> b.ready()).orElse(false)) {
            var result = reports.trialWorkspace(q);
            m.addAttribute("records", result.records());
            m.addAttribute("statement", result.statement());
            m.addAttribute("trialTotals", result.totals());
            m.addAttribute("statementEmpty", result.records().rows().isEmpty());
            m.addAttribute(
                    "statementDocument", presentation.trialPage(result, q.getSearch(), locale));
        }
        return "accounting/trial-balance";
    }

    @GetMapping("/trial-balance/print")
    public String trialPrint(@ModelAttribute("query") TrialQuery q, Model m, Locale locale) {
        dates(q);
        var result = reports.trialExport(q);
        m.addAttribute("statement", result.statement());
        m.addAttribute("statementDocument", presentation.trial(result, locale));
        m.addAttribute("reportTitle", "gl.trialBalance");
        return "accounting/statement-print";
    }

    @GetMapping("/trial-balance/print.pdf")
    public ResponseEntity<byte[]> trialPdf(@ModelAttribute TrialQuery q, Locale locale) {
        dates(q);
        var result = reports.trialExport(q);
        return output(documentPdf.statement(presentation.trial(result, locale), locale));
    }

    @GetMapping("/accounts/{id}/ledger")
    public String ledger(
            @PathVariable long id, @ModelAttribute("query") LedgerQuery q, Model m, Locale locale) {
        var book =
                dates(q).orElseThrow(
                                () -> new IllegalArgumentException("accounting.error.opening"));
        var result = reports.ledger(id, q);
        ledgerModel(id, result, m, locale);
        m.addAttribute("book", book);
        m.addAttribute("today", com.foresight.fcms.shared.time.ApplicationCalendar.today());
        m.addAttribute(
                "statementSortKeys",
                List.of("date", "", "description", "debit", "credit", "balance"));
        m.addAttribute(
                "statementAdditionalSorts",
                Map.of("reference", "common.reference", "kind", "gl.kind"));
        return "accounting/account-ledger";
    }

    @GetMapping("/accounts/{id}/ledger/print")
    public String ledgerPrint(
            @PathVariable long id, @ModelAttribute("query") LedgerQuery q, Model m, Locale locale) {
        dates(q);
        ledgerModel(id, reports.ledgerExport(id, q), m, locale);
        return "accounting/statement-print";
    }

    @GetMapping("/accounts/{id}/ledger/print.pdf")
    public ResponseEntity<byte[]> ledgerPdf(
            @PathVariable long id, @ModelAttribute LedgerQuery q, Locale locale) {
        dates(q);
        return output(
                documentPdf.statement(
                        presentation.ledger(reports.ledgerExport(id, q), locale), locale));
    }

    private void ledgerModel(
            long id, StatementService.AccountLedger result, Model m, Locale locale) {
        m.addAttribute("ledger", result);
        m.addAttribute("statement", result);
        m.addAttribute("records", result.records());
        m.addAttribute("baseUrl", "/accounting/accounts/" + id + "/ledger");
        m.addAttribute("resourceKey", "gl-account-ledger");
        m.addAttribute("reportType", "ledger");
        m.addAttribute("reportTitle", "gl.generalLedger");
        m.addAttribute("statementDocument", presentation.ledger(result, locale));
    }

    @ExceptionHandler(StatementService.ReportLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String reportLimit(jakarta.servlet.http.HttpServletResponse response, Model m) {
        response.setHeader("Retry-After", "900");
        m.addAttribute("errorCode", "error.exportBusy");
        return "shared/error";
    }

    @ExceptionHandler(StatementService.LedgerScopeLimitException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String ledgerScope(Model m) {
        m.addAttribute("errorCode", "error.narrowReport");
        return "shared/error";
    }

    private Optional<AccountingViews.Book> dates(RegisterQuery q) {
        var book = accounting.books();
        var start =
                book.map(b -> b.openingDate())
                        .orElse(
                                com.foresight.fcms.shared.time.ApplicationCalendar.today()
                                        .withDayOfYear(1));
        if (q.getFrom() == null) q.setFrom(start);
        if (q.getTo() == null) q.setTo(com.foresight.fcms.shared.time.ApplicationCalendar.today());
        return book;
    }

    private ResponseEntity<byte[]> output(byte[] bytes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
