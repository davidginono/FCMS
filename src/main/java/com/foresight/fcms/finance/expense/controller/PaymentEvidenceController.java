package com.foresight.fcms.finance.expense.controller;

import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.finance.expense.service.PaymentEvidenceService;
import com.foresight.fcms.shared.dto.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/finance/expenses")
@PreAuthorize(
        "hasAuthority('expense.read') and (hasAuthority('expense.evidence.write') or"
            + " hasAuthority('expense.evidence.review'))")
public class PaymentEvidenceController {
    private final PaymentEvidenceService evidence;

    public PaymentEvidenceController(PaymentEvidenceService evidence) {
        this.evidence = evidence;
    }

    @InitBinder("form")
    public void fields(WebDataBinder b) {
        b.setAllowedFields(
                "paidOn",
                "payerId",
                "paymentReference",
                "documentReference",
                "explanation",
                "openingPolicyReference",
                "requestKey");
    }

    @GetMapping("/evidence")
    public String list(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", evidence.list(q));
        m.addAttribute("baseUrl", "/finance/expenses/evidence");
        m.addAttribute("resourceKey", "payment-evidence");
        m.addAttribute(
                "registerStatuses",
                java.util.List.of("UNVERIFIED", "PENDING", "APPROVED", "REJECTED", "CANCELLED"));
        return "expenses/evidence-index";
    }

    @GetMapping("/evidence/payers")
    @ResponseBody
    public ChoicePage<PaymentEvidenceService.Payer> payers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return evidence.payers(search, page);
    }

    @GetMapping("/{expense}/evidence")
    public String payment(
            @PathVariable long expense, @ModelAttribute("query") RegisterQuery q, Model m) {
        return show(expense, null, q, m);
    }

    @GetMapping("/{expense}/evidence/{id}")
    public String detail(
            @PathVariable long expense,
            @PathVariable long id,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        return show(expense, id, q, m);
    }

    private String show(long expense, Long id, RegisterQuery q, Model m) {
        var w = evidence.workspace(expense, id, q);
        m.addAttribute("workspace", w);
        m.addAttribute("payment", w.payment());
        m.addAttribute("evidence", w.evidence());
        m.addAttribute("records", w.history());
        m.addAttribute(
                "baseUrl",
                "/finance/expenses/" + expense + "/evidence" + (id == null ? "" : "/" + id));
        m.addAttribute("resourceKey", "payment-evidence-history");
        m.addAttribute("hideDateFilters", true);
        return "expenses/evidence-detail";
    }

    @GetMapping("/{expense}/evidence/new")
    @PreAuthorize("hasAuthority('expense.evidence.write')")
    public String form(@PathVariable long expense, Model m) {
        var w = evidence.workspace(expense, null, new RegisterQuery());
        if (!w.canCreate()) return "redirect:/finance/expenses/" + expense + "/evidence";
        var f = new PaymentEvidenceForm();
        f.setPaymentReference(w.payment().paymentReference());
        m.addAttribute("form", f);
        return formPage(expense, f, m);
    }

    @PostMapping("/{expense}/evidence")
    @PreAuthorize("hasAuthority('expense.evidence.write')")
    public String create(
            @PathVariable long expense,
            @Valid @ModelAttribute("form") PaymentEvidenceForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/finance/expenses/"
                        + expense
                        + "/evidence/"
                        + evidence.create(expense, f);
            } catch (IllegalArgumentException e) {
                b.reject(key(e));
            } catch (org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        return formPage(expense, f, m);
    }

    private String formPage(long expense, PaymentEvidenceForm f, Model m) {
        m.addAttribute("payment", evidence.payment(expense));
        var payers = new java.util.LinkedHashMap<Long, PaymentEvidenceService.Payer>();
        evidence.payers("", 0).items().forEach(p -> payers.put(p.id(), p));
        evidence.selectedPayer(f.getPayerId()).forEach(p -> payers.put(p.id(), p));
        m.addAttribute("payers", payers.values());
        m.addAttribute("formAction", "/finance/expenses/" + expense + "/evidence");
        return "expenses/evidence-form";
    }

    @PostMapping("/{expense}/evidence/{id}/review")
    public String review(
            @PathVariable long expense,
            @PathVariable long id,
            @Valid @ModelAttribute("review") PaymentEvidenceReview f,
            BindingResult b,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        if (!b.hasErrors())
            try {
                evidence.review(expense, id, f);
                return "redirect:/finance/expenses/" + expense + "/evidence/" + id;
            } catch (IllegalArgumentException e) {
                m.addAttribute("actionError", key(e));
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                m.addAttribute("actionError", "error.stale");
            } catch (org.springframework.dao.DataAccessException e) {
                m.addAttribute("actionError", "error.conflict");
            }
        if (b.hasErrors()) m.addAttribute("actionError", "error.invalid");
        m.addAttribute("attemptedComment", f.comment());
        return show(expense, id, q, m);
    }

    private String key(IllegalArgumentException e) {
        return e.getMessage() != null
                        && (e.getMessage().startsWith("expense.evidence.")
                                || e.getMessage().equals("error.dates"))
                ? e.getMessage()
                : "error.conflict";
    }
}
