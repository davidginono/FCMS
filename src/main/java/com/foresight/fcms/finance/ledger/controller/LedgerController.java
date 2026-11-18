package com.foresight.fcms.finance.ledger.controller;

import com.foresight.fcms.shared.controller.FormErrors;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.dto.LedgerQuery;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/finance/{ledger:petty-cash|cloud-backup|bulk-sms}")
public class LedgerController {
    private final LedgerService ledger;
    private final ClientDirectoryService clients;

    public LedgerController(LedgerService ledger, ClientDirectoryService clients) {
        this.ledger = ledger;
        this.clients = clients;
    }

    @ModelAttribute
    public void context(@PathVariable String ledger, Model m) {
        var k = LedgerKind.from(ledger);
        m.addAttribute("kind", k);
        m.addAttribute("baseUrl", k.getRoute());
        m.addAttribute("resourceKey", ledger);
    }

    @GetMapping
    public String list(
            @PathVariable String ledger,
            @ModelAttribute("query") LedgerQuery q,
            Model m,
            jakarta.servlet.http.HttpServletRequest request) {
        var k = LedgerKind.from(ledger);
        if (k == LedgerKind.BULK
                && request.getParameter("year") == null
                && q.getFrom() == null
                && q.getTo() == null)
            q.setYear(
                    String.valueOf(
                            com.foresight.fcms.shared.time.ApplicationCalendar.today().getYear()));
        if (request.getParameter("sort") == null) q.setSort("date");
        var workspace = this.ledger.workspace(k, q);
        m.addAttribute("records", workspace.records());
        m.addAttribute("balance", workspace.balance());
        m.addAttribute("ledgerTotals", workspace.totals());
        m.addAttribute("ledgerYears", workspace.years());
        m.addAttribute("ledgerFilters", true);
        return "ledger/index";
    }

    @GetMapping("/reconciliation")
    public String reconciliation(@PathVariable String ledger, Model m) {
        m.addAttribute("reconciliations", this.ledger.reconcile());
        return "ledger/reconciliation";
    }

    @GetMapping("/new")
    public String create(
            @PathVariable String ledger,
            @RequestParam(defaultValue = "") String operation,
            Model m) {
        var kind = LedgerKind.from(ledger);
        if (kind == LedgerKind.BULK
                        && !java.util.Set.of("STOCK_PURCHASE", "CLIENT_SALE").contains(operation)
                || kind == LedgerKind.PETTY
                        && !java.util.Set.of("DEPOSIT", "WITHDRAWAL").contains(operation))
            return "redirect:" + kind.getRoute();
        var form = new LedgerForm();
        form.setOperation(operation);
        if (kind == LedgerKind.BULK) form.setCategory(operation);
        m.addAttribute("form", form);
        m.addAttribute("clients", clients.saccos("", null));
        m.addAttribute("formAction", LedgerKind.from(ledger).getRoute());
        return "ledger/form";
    }

    @PostMapping
    public String post(
            @PathVariable String ledger,
            @Valid @ModelAttribute("form") LedgerForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        var k = LedgerKind.from(ledger);
        if (!b.hasErrors())
            try {
                long saved = this.ledger.post(k, f);
                return ResourceFeedback.success(redirect, k.getRoute(), "feedback.entryRecorded", k.getRoute() + "/" + saved);
            } catch (IllegalArgumentException e) {
                if ("error.insufficient".equals(e.getMessage())) b.rejectValue(k == LedgerKind.BULK ? "units" : "amount", "error.insufficient");
                else FormErrors.reject(b, e, "error.invalid");
            } catch (org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        m.addAttribute("clients", clients.saccos("", f.getClientId()));
        m.addAttribute("formAction", k.getRoute());
        return "ledger/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String ledger, @PathVariable long id, Model m) {
        m.addAttribute("record", this.ledger.find(LedgerKind.from(ledger), id));
        m.addAttribute("requestKey", java.util.UUID.randomUUID());
        return "ledger/detail";
    }

    @PostMapping("/{id}/reverse")
    public String reverse(
            @PathVariable String ledger,
            @PathVariable long id,
            @RequestParam java.util.UUID requestKey,
            @RequestParam String reason) {
        var k = LedgerKind.from(ledger);
        return "redirect:" + k.getRoute() + "/" + this.ledger.reverse(k, id, requestKey, reason);
    }
}
