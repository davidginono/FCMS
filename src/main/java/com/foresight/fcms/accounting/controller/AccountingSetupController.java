package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.AccountingService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting")
public class AccountingSetupController {
    private final AccountingService accounting;
    private final com.foresight.fcms.accounting.service.SourceCoverageService coverage;

    public AccountingSetupController(
            AccountingService accounting,
            com.foresight.fcms.accounting.service.SourceCoverageService coverage) {
        this.accounting = accounting;
        this.coverage = coverage;
    }

    @GetMapping("/books")
    public String books(Model m) {
        m.addAttribute("book", accounting.books().orElse(null));
        return "accounting/books";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    @GetMapping("/books/edit")
    public String configure(Model m) {
        var f = new BookForm();
        accounting
                .books()
                .ifPresent(
                        b -> {
                            f.setOpeningDate(b.openingDate());
                            f.setOpeningMode(b.openingMode());
                            f.setPolicyReference(b.policyReference());
                            f.setVersion(b.version());
                        });
        m.addAttribute("form", f);
        return "accounting/book-form";
    }

    @PostMapping("/books")
    public String configure(@Valid @ModelAttribute("form") BookForm f, BindingResult b) {
        if (!b.hasErrors())
            try {
                accounting.configure(f);
                return "redirect:/accounting/books?saved";
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        return "accounting/book-form";
    }

    @PostMapping("/books/approve")
    public String approve(@RequestParam long version, @RequestParam String reason) {
        accounting.approveBooks(version, reason);
        return "redirect:/accounting/books?approved";
    }

    @GetMapping("/periods")
    public String periods(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", accounting.periods(q));
        m.addAttribute("baseUrl", "/accounting/periods");
        m.addAttribute("resourceKey", "gl-periods");
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("registerStatuses", java.util.List.of("OPEN", "CLOSED"));
        return "accounting/periods";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    @GetMapping("/periods/new")
    public String periodForm(Model m) {
        m.addAttribute("form", new PeriodForm());
        return "accounting/period-form";
    }

    @PostMapping("/periods")
    public String createPeriod(@Valid @ModelAttribute("form") PeriodForm f, BindingResult b) {
        if (!b.hasErrors())
            try {
                accounting.createPeriod(f);
                return "redirect:/accounting/periods?saved";
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        return "accounting/period-form";
    }

    @GetMapping("/periods/{id}")
    public String period(@PathVariable long id, Model m) {
        var period = accounting.period(id);
        m.addAttribute("period", period);
        m.addAttribute("sourceCoverage", coverage.through(period.endsOn()));
        return "accounting/period-detail";
    }

    @PostMapping("/periods/{id}/state")
    public String periodState(
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam boolean close,
            @RequestParam String reason,
            Model m) {
        try {
            accounting.periodState(id, version, close, reason);
            return "redirect:/accounting/periods/" + id;
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
            m.addAttribute("actionError", true);
            m.addAttribute(
                    "actionErrorKey",
                    "gl.source.incomplete".equals(e.getMessage())
                            ? "gl.source.incomplete"
                            : "error.conflict");
            return period(id, m);
        }
    }
}
