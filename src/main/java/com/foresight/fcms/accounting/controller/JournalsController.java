package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;


import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/journals")
public class JournalsController {
    private final JournalService journals;
    private final SourceBindingService bindings;
    private final JournalDocumentService documents;
    private final AccountingDocumentPdfService documentPdf;
    private final StatementService reports;
    private final VoucherService vouchers;

    public JournalsController(
            JournalService journals,
            SourceBindingService bindings,
            JournalDocumentService documents,
            AccountingDocumentPdfService documentPdf,
            StatementService reports, VoucherService vouchers) {
        this.journals = journals;
        this.bindings = bindings;
        this.documents = documents;
        this.documentPdf = documentPdf;
        this.reports = reports;
        this.vouchers = vouchers;
    }

    @ModelAttribute
    public void context(Model m) {
        m.addAttribute("baseUrl", "/accounting/journals");
        m.addAttribute("resourceKey", "gl-journals");
        m.addAttribute(
                "registerStatuses",
                java.util.List.of("DRAFT", "SUBMITTED", "POSTED", "REJECTED", "CANCELLED"));
    }

    @GetMapping
    public String list(
            @ModelAttribute("query") RegisterQuery q,
            @RequestParam(defaultValue = "") String kind,
            Model m) {
        m.addAttribute("records", journals.list(q, kind));
        m.addAttribute("journalKind", kind);
        m.addAttribute("voucherKind",kind.isEmpty()?"JOURNAL":kind);
        m.addAttribute("legacyRegister",true);
        m.addAttribute("voucherRoute",VoucherService.route(kind));
        return "accounting/vouchers/register";
    }

    @GetMapping("/{id}")
    public String details(
            @PathVariable long id,
            @RequestParam(required = false) Long ledgerAccount,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            Model m) {
        if (ledgerAccount != null)
            m.addAttribute("ledgerReturn", reports.ledgerContext(ledgerAccount, id, from, to));
        return detail(id, m);
    }

    private String detail(long id, Model m) {
        var document =
                documents.document(
                        id, org.springframework.context.i18n.LocaleContextHolder.getLocale());
        m.addAttribute("voucherDocument", document);
        m.addAttribute("journal", document.journal());
        m.addAttribute("voucherHeader",vouchers.header(id).orElse(null));
        m.addAttribute("voucherRows",vouchers.rows(id));
        m.addAttribute("voucherKind",document.journal().kind());
        m.addAttribute("voucherRoute",VoucherService.route(document.journal().kind()));
        m.addAttribute("baseUrl","/accounting/vouchers/"+VoucherService.route(document.journal().kind()));
        m.addAttribute("actionBase","/accounting/journals/"+id);
        m.addAttribute("sourceBinding", bindings.forJournal(id).orElse(null));
        m.addAttribute("sourceReviewAllowed", bindings.canReview(id));
        m.addAttribute("requestKey", java.util.UUID.randomUUID());
        return "accounting/vouchers/details";
    }

    @PostMapping("/{id}/actions/{action}")
    public String action(
            @PathVariable long id,
            @PathVariable String action,
            @RequestParam long version,
            @RequestParam(defaultValue = "") String reason,
            Model m) {
        try {
            journals.transition(id, version, action, reason);
            return "redirect:/accounting/journals/" + id;
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
            m.addAttribute("actionError", true);
            return detail(id, m);
        }
    }

    @PostMapping("/{id}/reverse")
    public String reverse(
            @PathVariable long id,
            @RequestParam java.util.UUID requestKey,
            @RequestParam java.time.LocalDate date,
            @RequestParam String reason,
            Model m) {
        try {
            return "redirect:/accounting/journals/"
                    + journals.reverse(id, requestKey, date, reason);
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
            m.addAttribute("actionError", true);
            return detail(id, m);
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.report')")
    @GetMapping("/{id}/print.pdf")
    public ResponseEntity<byte[]> print(@PathVariable long id, java.util.Locale locale) {
        var document = documents.document(id, locale);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=fcms-"
                                + document.journal().kind().toLowerCase(java.util.Locale.ROOT)
                                + "-"
                                + id
                                + ".pdf")
                .body(documentPdf.voucher(document, locale));
    }
}
