package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.dto.EquityReporting.*;
import com.foresight.fcms.accounting.service.EquityReportingService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.dao.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/equity-reporting")
@PreAuthorize("hasAuthority('accounting.report')")
public class EquityReportingController {
    private final EquityReportingService reporting;
    private final org.springframework.validation.Validator validator;

    public EquityReportingController(
            EquityReportingService reporting, org.springframework.validation.Validator validator) {
        this.reporting = reporting;
        this.validator = validator;
    }

    @InitBinder("form")
    public void binder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(9);
    }

    @ModelAttribute
    public void context(Model m) {
        m.addAttribute("breadcrumbUrl", "/accounting/statements/changes-in-equity");
        m.addAttribute("breadcrumbLabel", "gl.statement.changes-in-equity");
        m.addAttribute("breadcrumbCurrent", "gl.equity.setup");
    }

    @GetMapping
    public String index(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", reporting.history(q));
        m.addAttribute("baseUrl", "/accounting/equity-reporting");
        m.addAttribute("resourceKey", "equity-reporting-history");
        m.addAttribute("movements", false);
        return "accounting/equity-reporting-index";
    }

    @GetMapping("/movements")
    public String movements(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", reporting.journals(q));
        m.addAttribute("baseUrl", "/accounting/equity-reporting/movements");
        m.addAttribute("resourceKey", "equity-reporting-movements");
        m.addAttribute("movements", true);
        return "accounting/equity-reporting-index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String form(Model m) {
        return form(reporting.form(), m);
    }

    private String form(Form f, Model m) {
        m.addAttribute("form", f);
        m.addAttribute("choices", reporting.choices());
        return "accounting/equity-reporting-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String propose(
            @ModelAttribute("form") Form f,
            BindingResult b,
            @RequestParam(defaultValue = "save") String operation,
            Model m) {
        if (operation.equals("add") && f.getComponents() != null && f.getComponents().size() < 9) {
            m.addAttribute("unsavedDraft", true);
            f.getComponents().add(new ComponentForm());
            return form(f, m);
        }
        if (operation.matches("remove:[0-8]") && f.getComponents() != null) {
            m.addAttribute("unsavedDraft", true);
            int i = Integer.parseInt(operation.substring(7));
            if (i < f.getComponents().size()) f.getComponents().remove(i);
            return form(f, m);
        }
        if (!operation.equals("save")) b.reject("error.invalid");
        validator.validate(f, b);
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/equity-reporting/" + reporting.propose(f) + "?saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject(
                        "gl.equity.accountError".equals(e.getMessage())
                                ? e.getMessage()
                                : "error.invalid");
            } catch (DataAccessException e) {
                b.reject("error.conflict");
            }
        return form(f, m);
    }

    @GetMapping("/movements/{journal}")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String movement(@PathVariable long journal, Model m) {
        return movement(journal, reporting.movementForm(journal), m);
    }

    private String movement(long journal, MovementForm f, Model m) {
        m.addAttribute("journalId", journal);
        m.addAttribute("categories", Category.values());
        m.addAttribute("form", f);
        return "accounting/equity-reporting-movement";
    }

    @PostMapping("/movements/{journal}")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String classify(
            @PathVariable long journal,
            @Valid @ModelAttribute("form") MovementForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/equity-reporting/"
                        + reporting.classify(journal, f)
                        + "?saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject("error.invalid");
            } catch (DataAccessException e) {
                b.reject("error.conflict");
            }
        return movement(journal, f, m);
    }

    @GetMapping("/{id}")
    public String details(@PathVariable long id, Model m) {
        return details(id, null, m);
    }

    private String details(long id, ReportingDecisionForm f, Model m) {
        var d = reporting.details(id);
        m.addAttribute("revision", d.revision());
        m.addAttribute("allowedActions", d.actions());
        m.addAttribute("components", d.components());
        m.addAttribute("category", d.category());
        if (f == null) {
            f = new ReportingDecisionForm();
            f.setVersion(d.revision().version());
        }
        m.addAttribute("decisionForm", f);
        return "accounting/equity-reporting-detail";
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyAuthority('accounting.setup','accounting.review')")
    public String decide(
            @PathVariable long id,
            @Valid @ModelAttribute("decisionForm") ReportingDecisionForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                reporting.decide(id, f);
                return "redirect:/accounting/equity-reporting/" + id + "?saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject("error.invalid");
            } catch (DataAccessException e) {
                b.reject("error.conflict");
            }
        return details(id, f, m);
    }
}
