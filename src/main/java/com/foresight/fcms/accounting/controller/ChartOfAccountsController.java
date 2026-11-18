package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.shared.controller.FormErrors;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.accounting.dto.AccountForm;
import com.foresight.fcms.accounting.dto.CoaQuery;
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
    public String list(@ModelAttribute("coaQuery") CoaQuery q,
            @RequestParam(required = false) Long parentId, Model m) {
        chart(q, parentId, m);
        var parents = new java.util.LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, false).items().forEach(p -> parents.put(p.id(), p));
        if (parentId != null)
            accounts.selected(java.util.List.of(parentId)).forEach(p -> parents.put(p.id(), p));
        m.addAttribute("filterGlParents", parents.values());
        return "accounting/accounts";
    }

    private void chart(CoaQuery q, Long parentId, Model m) {
        var chart = accounts.chart(q, parentId);
        m.addAttribute("query", q);
        m.addAttribute("records", chart.records());
        m.addAttribute("chartTotal", chart.total());
        m.addAttribute("groupPath", chart.path());
        m.addAttribute("parentId", parentId);
        m.addAttribute("classifications", java.util.List.of("ASSET", "LIABILITY", "EQUITY", "EXPENSE", "INCOME"));
    }

    @GetMapping("/children")
    public String children(@ModelAttribute("coaQuery") CoaQuery q, @RequestParam long parentId,
            jakarta.servlet.http.HttpServletResponse response, Model m) {
        chart(q, parentId, m);
        m.addAttribute("childFragment", true);
        response.setHeader("X-COA-Children", "true");
        response.setHeader("Cache-Control", "no-store");
        return "accounting/account-rows";
    }

    @GetMapping("/choices")
    @ResponseBody
    public ChoicePage<AccountingService.Choice> choices(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "true") boolean posting,
            @RequestParam(defaultValue = "") String parentMode) {
        return accounts.choices(search, page, posting, parentMode);
    }

    @GetMapping("/next-code")
    @ResponseBody
    public java.util.Map<String, String> nextCode(@RequestParam long parentId,
            @RequestParam(defaultValue = "") String mode, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return java.util.Map.of("code", accounts.nextAccountCode(parentId, mode));
    }

    @InitBinder("form")
    public void bind(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("parentId", "code", "name", "description", "normalBalance", "cashAccount", "version", "mode");
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
    public String create(@RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "") String mode, Model m, RedirectAttributes redirect) {
        var f = new AccountForm();
        f.setParentId(parentId);
        if (parentId != null) {
            var parent = accounts.account(parentId);
            f.setMode(mode.isEmpty() ? (parent.depth() == 3 ? "POSTING" : "GROUP") : mode);
            f.setNormalBalance(parent.normalBalance());
            f.setCode(accounts.nextAccountCode(parentId, f.getMode()));
            m.addAttribute("codeRangeExhausted", f.getCode().isEmpty());
        } else f.setMode(mode.isEmpty() ? "GROUP" : mode);
        if (!java.util.Set.of("GROUP", "POSTING").contains(f.getMode()))
            throw new IllegalArgumentException("error.invalid");
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
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    public String create(@Valid @ModelAttribute("form") AccountForm f, BindingResult b, Model m, RedirectAttributes redirect) {
        return save(null, f, b, m, redirect);
    }

    @PostMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    public String update(
            @PathVariable long id,
            @Valid @ModelAttribute("form") AccountForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        return save(id, f, b, m, redirect);
    }

    private String save(Long id, AccountForm f, BindingResult b, Model m, RedirectAttributes redirect) {
        if (!b.hasErrors())
            try {
                long saved = accounts.saveAccount(id, f);
                return ResourceFeedback.success(redirect, "/accounting/accounts?parentId=" + f.getParentId(), id == null ? "feedback.accountCreated" : "feedback.saved", "/accounting/accounts/" + saved);
            } catch (IllegalArgumentException e) {
                if ("gl.error.parent".equals(e.getMessage())) b.rejectValue("parentId", "gl.error.parent");
                else FormErrors.reject(b, e, "error.conflict");
            } catch (org.springframework.dao.DataAccessException e) {
                FormErrors.conflict(b, e, java.util.Map.of("gl_accounts_code_key", "code"));
            }
        options(f, id, m);
        return "accounting/account-form";
    }

    private void options(AccountForm f, Long id, Model m) {
        var choices = new java.util.LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, false, java.util.Set.of("GROUP", "POSTING").contains(f.getMode()) ? f.getMode() : "").items().forEach(c -> choices.put(c.id(), c));
        if (f.getParentId() != null)
            accounts.selected(java.util.List.of(f.getParentId()))
                    .forEach(c -> choices.put(c.id(), c));
        m.addAttribute("parents", choices.values());
        m.addAttribute("editing", id != null);
        m.addAttribute("breadcrumbCurrent", id == null ? "gl.account.create" : "gl.account.edit");
        m.addAttribute("formAction", "/accounting/accounts" + (id == null ? "" : "/" + id));
    }

    @PostMapping("/{id}/state")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('accounting.setup')")
    public String state(
            @PathVariable long id, @RequestParam long version, @RequestParam boolean active) {
        accounts.accountState(id, version, active);
        return "redirect:/accounting/accounts/" + id;
    }
}
