package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/accounting/statements")
@PreAuthorize("hasAuthority('accounting.report')")
public class StatementDrillController {
    private final StatementDrillService drill;
    private final AccountingService accounting;
    private final StatementPresentation presentation;
    private final AccountingDocumentPdfService pdf;

    public StatementDrillController(
            StatementDrillService drill,
            AccountingService accounting,
            StatementPresentation presentation,
            AccountingDocumentPdfService pdf) {
        this.drill = drill;
        this.accounting = accounting;
        this.presentation = presentation;
        this.pdf = pdf;
    }

    private void dates(RegisterQuery q) {
        if (q.getFrom() == null)
            q.setFrom(
                    accounting
                            .books()
                            .orElseThrow(
                                    () -> new IllegalArgumentException("accounting.error.opening"))
                            .openingDate());
        if (q.getTo() == null) q.setTo(LocalDate.now());
    }

    @GetMapping("/{type}/lines/{line}")
    public String accounts(
            @PathVariable String type,
            @PathVariable String line,
            @ModelAttribute("query") RegisterQuery q,
            Model m,
            java.util.Locale locale) {
        dates(q);
        var result = drill.accounts(type, line, q);
        m.addAttribute("breakdown", result);
        m.addAttribute("statementLineLabel", result.label());
        m.addAttribute("statementLineLabelKey", result.labelKey());
        m.addAttribute("statement", result.statement());
        m.addAttribute("records", result.records());
        m.addAttribute("total", result.total());
        m.addAttribute("filteredTotal", result.filteredTotal());
        m.addAttribute(
                "statementDocument", presentation.trialPage(result.trial(), q.getSearch(), locale));
        m.addAttribute("statementDrill", true);
        context(type, line, null, m);
        return "accounting/statement-drill";
    }

    @GetMapping("/{type}/lines/{line}/accounts/{account}")
    public String entries(
            @PathVariable String type,
            @PathVariable String line,
            @PathVariable long account,
            @ModelAttribute("query") RegisterQuery q,
            Model m,
            java.util.Locale locale) {
        dates(q);
        var result = drill.entries(type, line, account, q);
        m.addAttribute("entries", result);
        m.addAttribute("statementLineLabel", result.label());
        m.addAttribute("statementLineLabelKey", result.labelKey());
        m.addAttribute("statement", result.statement());
        m.addAttribute("account", result.account());
        m.addAttribute("records", result.records());
        m.addAttribute("total", result.account().amount());
        m.addAttribute("filteredTotal", result.filteredTotal());
        m.addAttribute("statementDocument", presentation.ledger(result.ledger(), locale));
        m.addAttribute("ledger", result.ledger());
        m.addAttribute("paperKind", "ledger");
        context(type, line, account, m);
        return "accounting/statement-drill";
    }

    private void context(String type, String line, Long account, Model m) {
        m.addAttribute("reportType", type);
        m.addAttribute("reportTitle", "gl.statement." + type);
        m.addAttribute("statementLine", line);
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("preserveReportDates", true);
        m.addAttribute(
                "baseUrl",
                "/accounting/statements/"
                        + type
                        + "/lines/"
                        + line
                        + (account == null ? "" : "/accounts/" + account));
        m.addAttribute("resourceKey", "gl-statement-drill");
        m.addAttribute(
                "breadcrumbCurrent", account == null ? "gl.drill.accounts" : "gl.drill.entries");
    }

    @GetMapping("/{type}/lines/{line}/print")
    public String print(
            @PathVariable String type,
            @PathVariable String line,
            @ModelAttribute("query") RegisterQuery q,
            Model m,
            java.util.Locale locale) {
        dates(q);
        var result = drill.export(type, line, q);
        m.addAttribute("statement", result.trial().statement());
        m.addAttribute("statementDocument", presentation.contributionTrial(result, locale));
        m.addAttribute("reportTitle", "gl.trialBalance");
        return "accounting/statement-print";
    }

    @GetMapping("/{type}/lines/{line}/print.pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(
            @PathVariable String type,
            @PathVariable String line,
            @ModelAttribute RegisterQuery q,
            java.util.Locale locale) {
        dates(q);
        var result = drill.export(type, line, q);
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=statement-accounts.pdf")
                .body(pdf.statement(presentation.contributionTrial(result, locale), locale));
    }
}
