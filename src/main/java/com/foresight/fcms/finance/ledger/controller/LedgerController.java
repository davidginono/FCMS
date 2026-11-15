package com.foresight.fcms.finance.ledger.controller;

import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/finance/{ledger:income|petty-cash|cloud-backup|bulk-sms}")
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
            @PathVariable String ledger, @ModelAttribute("query") RegisterQuery q, Model m) {
        var k = LedgerKind.from(ledger);
        m.addAttribute("records", this.ledger.list(k, q));
        m.addAttribute("balance", this.ledger.balance(k));
        return "ledger/index";
    }

    @GetMapping("/reconciliation")
    public String reconciliation(@PathVariable String ledger, Model m) {
        m.addAttribute("reconciliations", this.ledger.reconcile());
        return "ledger/reconciliation";
    }

    @GetMapping("/new")
    public String create(@PathVariable String ledger, Model m) {
        m.addAttribute("form", new LedgerForm());
        m.addAttribute("clients", clients.saccos("", null));
        m.addAttribute("formAction", LedgerKind.from(ledger).getRoute());
        return "ledger/form";
    }

    @PostMapping
    public String post(
            @PathVariable String ledger,
            @Valid @ModelAttribute("form") LedgerForm f,
            BindingResult b,
            Model m) {
        var k = LedgerKind.from(ledger);
        if (!b.hasErrors())
            try {
                return "redirect:" + k.getRoute() + "/" + this.ledger.post(k, f);
            } catch (IllegalArgumentException e) {
                b.reject("error.invalid");
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
