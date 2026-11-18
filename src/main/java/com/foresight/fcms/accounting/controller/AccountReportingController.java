package com.foresight.fcms.accounting.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.AccountReportingService;

import jakarta.validation.Valid;

import org.springframework.dao.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/accounts/{account}/reporting")
public class AccountReportingController {
    private final AccountReportingService reporting;

    public AccountReportingController(AccountReportingService reporting) {
        this.reporting = reporting;
    }

    @PreAuthorize("hasAuthority('accounting.setup')")
    @GetMapping("/new")
    public String create(@PathVariable long account, Model m) {
        return form(account, null, m);
    }

    @PreAuthorize("hasAuthority('accounting.setup')")
    @PostMapping
    public String propose(
            @PathVariable long account,
            @Valid @ModelAttribute("form") ReportingForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        if (!b.hasErrors())
            try {
                long id = reporting.propose(account, f);
                return ResourceFeedback.success(redirect, "/accounting/accounts/" + account, "feedback.reportingProposed", "/accounting/accounts/" + account + "/reporting/" + id);
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                String key = error(e);
                if (key.equals("gl.reporting.noteRequired")) b.rejectValue("noteNumber", key);
                else if (key.equals("gl.reporting.sectionInvalid")) b.rejectValue("section", key);
                else if (key.equals("gl.reporting.textInvalid")) b.rejectValue("noteText", key);
                else b.reject(key);
            } catch (DataAccessException e) {
                b.reject("error.conflict");
            }
        if (b.hasFieldErrors("requestKey")
                || b.hasFieldErrors("accountVersion")
                || b.hasFieldErrors("baseId")) b.reject("error.invalid");
        return form(account, f, m);
    }

    @PreAuthorize("hasAuthority('accounting.read')")
    @GetMapping("/{id}")
    public String details(@PathVariable long account, @PathVariable long id, Model m) {
        return details(account, id, null, m);
    }

    @PreAuthorize("hasAnyAuthority('accounting.setup','accounting.review')")
    @PostMapping("/{id}/decision")
    public String decide(
            @PathVariable long account,
            @PathVariable long id,
            @Valid @ModelAttribute("decisionForm") ReportingDecisionForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        if (!b.hasErrors())
            try {
                reporting.decide(account, id, f);
                return ResourceFeedback.success(redirect, "/accounting/accounts/" + account, "feedback.saved", "/accounting/accounts/" + account + "/reporting/" + id);
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject(error(e));
            } catch (DataAccessException e) {
                b.reject("error.conflict");
            }
        if (b.hasFieldErrors("version") || b.hasFieldErrors("action")) b.reject("error.invalid");
        m.addAttribute("decisionFailed", b.hasErrors());
        return details(account, id, f, m);
    }

    private String form(long account, ReportingForm retained, Model m) {
        var setup = reporting.setup(account, retained);
        m.addAttribute("account", setup.account());
        m.addAttribute("form", setup.form());
        m.addAttribute("sectionEditable", setup.sectionEditable());
        context(account, "gl.reporting.propose", m);
        return "accounting/reporting-form";
    }

    private String details(long account, long id, ReportingDecisionForm retained, Model m) {
        var details = reporting.details(account, id);
        m.addAttribute("account", details.account());
        m.addAttribute("reportingDecision", details);
        if (retained == null) {
            retained = new ReportingDecisionForm();
            retained.setVersion(details.revision().version());
        }
        m.addAttribute("decisionForm", retained);
        context(account, "gl.reporting.revision", m);
        return "accounting/reporting-detail";
    }

    private void context(long account, String current, Model m) {
        m.addAttribute("breadcrumbUrl", "/accounting/accounts/" + account);
        m.addAttribute("breadcrumbLabel", "gl.account.details");
        m.addAttribute("breadcrumbCurrent", current);
    }

    private String error(IllegalArgumentException e) {
        return e.getMessage() != null && e.getMessage().matches("gl\\.reporting\\.[A-Za-z]+")
                ? e.getMessage()
                : "error.invalid";
    }
}
