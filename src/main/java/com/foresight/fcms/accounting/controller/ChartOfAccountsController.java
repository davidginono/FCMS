package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.AccountForm;
import com.foresight.fcms.accounting.service.AccountingService;
import com.foresight.fcms.shared.dto.*;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/accounts")
public class ChartOfAccountsController {
    private final AccountingService accounts;
    private final com.foresight.fcms.accounting.service.AccountReportingService reporting;

    public ChartOfAccountsController(
            AccountingService accounts,
            com.foresight.fcms.accounting.service.AccountReportingService reporting) {
        this.accounts = accounts;
        this.reporting = reporting;
    }

    @ModelAttribute("query")
    public RegisterQuery query() {
        var query = new RegisterQuery();
        query.setSort("code");
        query.setDirection("asc");
        return query;
    }

    @ModelAttribute
    public void context(Model m) {
        m.addAttribute("baseUrl", "/accounting/accounts");
        m.addAttribute("resourceKey", "gl-accounts");
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("registerStatuses", java.util.List.of("ACTIVE", "INACTIVE"));
    }

    @GetMapping
    public String list(
            @ModelAttribute("query") RegisterQuery q,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "0") int groupPage,
            Model m) {
        m.addAttribute("grouped", accounts.groupedAccounts(q, parentId, group, groupPage));
        m.addAttribute("parentId", parentId);
        var parents = new java.util.LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, false).items().forEach(p -> parents.put(p.id(), p));
        if (parentId != null) {
            m.addAttribute("parent", accounts.account(parentId));
            accounts.selected(java.util.List.of(parentId)).forEach(p -> parents.put(p.id(), p));
        }
        m.addAttribute("filterGlParents", parents.values());
        return "accounting/accounts";
    }

    @GetMapping("/choices")
    @ResponseBody
    public ChoicePage<AccountingService.Choice> choices(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "true") boolean posting) {
        return accounts.choices(search, page, posting);
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable long id,
            @ModelAttribute("query") RegisterQuery q,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Model m) {
        q.setSort(sort);
        q.setDirection(direction);
        var workspace = reporting.workspace(id, q);
        m.addAttribute("account", workspace.account());
        m.addAttribute("reporting", workspace);
        m.addAttribute("records", workspace.history());
        m.addAttribute("baseUrl", "/accounting/accounts/" + id);
        m.addAttribute("resourceKey", "gl-reporting-history");
        m.addAttribute(
                "registerStatuses",
                java.util.List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED", "RETIRED"));
        m.addAttribute("breadcrumbCurrent", "gl.account.details");
        return "accounting/account-detail";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    @GetMapping("/new")
    public String create(@RequestParam(required = false) Long parentId, Model m) {
        var f = new AccountForm();
        f.setParentId(parentId);
        if (parentId != null) f.setNormalBalance(accounts.account(parentId).normalBalance());
        m.addAttribute("form", f);
        options(f, null, m);
        return "accounting/account-form";
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model m) {
        var a = accounts.account(id);
        var f = new AccountForm();
        f.setParentId(a.parentId());
        f.setCode(a.code());
        f.setName(a.name());
        f.setDescription(a.description());
        f.setNormalBalance(a.normalBalance());
        f.setCashAccount(a.cashAccount());
        f.setVersion(a.version());
        m.addAttribute("form", f);
        options(f, id, m);
        return "accounting/account-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AccountForm f, BindingResult b, Model m) {
        return save(null, f, b, m);
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @Valid @ModelAttribute("form") AccountForm f,
            BindingResult b,
            Model m) {
        return save(id, f, b, m);
    }

    private String save(Long id, AccountForm f, BindingResult b, Model m) {
        if (!b.hasErrors())
            try {
                accounts.saveAccount(id, f);
                return "redirect:/accounting/accounts?saved";
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        options(f, id, m);
        return "accounting/account-form";
    }

    private void options(AccountForm f, Long id, Model m) {
        var choices = new java.util.LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, false).items().forEach(c -> choices.put(c.id(), c));
        if (f.getParentId() != null)
            accounts.selected(java.util.List.of(f.getParentId()))
                    .forEach(c -> choices.put(c.id(), c));
        m.addAttribute("parents", choices.values());
        m.addAttribute("editing", id != null);
        m.addAttribute("breadcrumbCurrent", id == null ? "gl.account.create" : "gl.account.edit");
        m.addAttribute("formAction", "/accounting/accounts" + (id == null ? "" : "/" + id));
    }

    @PostMapping("/{id}/state")
    public String state(
            @PathVariable long id, @RequestParam long version, @RequestParam boolean active) {
        accounts.accountState(id, version, active);
        return "redirect:/accounting/accounts/" + id;
    }
}
