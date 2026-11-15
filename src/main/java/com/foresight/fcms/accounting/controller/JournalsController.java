package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/journals")
public class JournalsController {
    private final JournalService journals;
    private final SourceBindingService bindings;
    private final AccountingService accounts;
    private final JournalDocumentService documents;
    private final AccountingDocumentPdfService documentPdf;
    private final StatementService reports;

    public JournalsController(
            JournalService journals,
            SourceBindingService bindings,
            AccountingService accounts,
            JournalDocumentService documents,
            AccountingDocumentPdfService documentPdf,
            StatementService reports) {
        this.journals = journals;
        this.bindings = bindings;
        this.accounts = accounts;
        this.documents = documents;
        this.documentPdf = documentPdf;
        this.reports = reports;
    }

    @InitBinder("form")
    public void bind(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(50);
        binder.setAllowedFields(
                "kind",
                "reference",
                "description",
                "evidenceReference",
                "date",
                "cashFlow",
                "requestKey",
                "version",
                "lines[*].accountId",
                "lines[*].debit",
                "lines[*].credit",
                "lines[*].description");
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
        return "accounting/journals";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.write')")
    @GetMapping("/new")
    public String create(@RequestParam(defaultValue = "JOURNAL") String kind, Model m) {
        if (!java.util.Set.of("JOURNAL", "RECEIPT", "PAYMENT", "OPENING").contains(kind))
            throw new IllegalArgumentException();
        var f = new JournalForm();
        f.setKind(kind);
        m.addAttribute("form", f);
        options(f, null, m);
        return "accounting/journal-form";
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
        m.addAttribute("sourceBinding", bindings.forJournal(id).orElse(null));
        m.addAttribute("sourceReviewAllowed", bindings.canReview(id));
        m.addAttribute("requestKey", java.util.UUID.randomUUID());
        return "accounting/journal-detail";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.write')")
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model m) {
        var f = journals.form(id);
        m.addAttribute("form", f);
        options(f, id, m);
        return "accounting/journal-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") JournalForm f, BindingResult b, Model m) {
        return save(null, f, b, m);
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @Valid @ModelAttribute("form") JournalForm f,
            BindingResult b,
            Model m) {
        return save(id, f, b, m);
    }

    private String save(Long id, JournalForm f, BindingResult b, Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/journals/" + journals.save(id, f);
            } catch (IllegalArgumentException e) {
                b.reject(
                        e.getMessage() != null && e.getMessage().startsWith("accounting.error.")
                                ? e.getMessage()
                                : "error.invalid");
            } catch (org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        options(f, id, m);
        return "accounting/journal-form";
    }

    private void options(JournalForm f, Long id, Model m) {
        var choices = new java.util.LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, true).items().forEach(c -> choices.put(c.id(), c));
        accounts.selected(
                        f.getLines().stream()
                                .map(JournalLineForm::getAccountId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList())
                .forEach(c -> choices.put(c.id(), c));
        m.addAttribute("accounts", choices.values());
        m.addAttribute("formAction", "/accounting/journals" + (id == null ? "" : "/" + id));
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
