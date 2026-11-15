package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.shared.dto.*;

import jakarta.validation.Valid;

import org.springframework.dao.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/accounting/config")
@PreAuthorize("hasAuthority('accounting.read')")
public class TransactionConfigurationController {
    private final ActivityTransactionService configuration;
    private final AccountingService accounts;
    private final PostingMappingService templates;

    public TransactionConfigurationController(
            ActivityTransactionService configuration,
            AccountingService accounts,
            PostingMappingService templates) {
        this.configuration = configuration;
        this.accounts = accounts;
        this.templates = templates;
    }

    @ModelAttribute("query")
    public RegisterQuery query() {
        var q = new RegisterQuery();
        q.setSort("code");
        q.setDirection("asc");
        return q;
    }

    @InitBinder("activityForm")
    public void bindActivity(WebDataBinder b) {
        b.setAllowedFields("code", "name", "description", "version", "requestKey");
    }

    @InitBinder("transactionForm")
    public void bindTransaction(WebDataBinder b) {
        b.setAllowedFields(
                "activityId",
                "code",
                "name",
                "description",
                "event",
                "debitAccountId",
                "creditAccountId",
                "voucherKind",
                "cashFlow",
                "policyReference",
                "requestKey");
    }

    @InitBinder("templateForm")
    public void bindTemplate(WebDataBinder b) {
        b.setAllowedFields(
                "version",
                "debitAccountId",
                "creditAccountId",
                "voucherKind",
                "cashFlow",
                "policyReference",
                "requestKey");
    }

    @GetMapping("/activities")
    public String activities(
            @ModelAttribute("query") RegisterQuery q,
            @RequestParam(defaultValue = "false") boolean create,
            @RequestParam(required = false) Long created,
            Model m) {
        if (!m.containsAttribute("activityForm"))
            m.addAttribute("activityForm", new ActivityForm());
        activityList(q, m);
        if (create) m.addAttribute("modalOpen", true);
        if (created != null) {
            m.addAttribute("createdResource", configuration.activity(created));
            m.addAttribute("modalOpen", true);
        }
        return "accounting/config/activities";
    }

    @PostMapping("/activities")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String createActivity(
            @Valid @ModelAttribute("activityForm") ActivityForm f,
            BindingResult b,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/config/activities?created="
                        + configuration.createActivity(f);
            } catch (IllegalArgumentException e) {
                b.reject(error(e));
            } catch (DataIntegrityViolationException e) {
                b.reject("error.conflict");
            }
        activityList(q, m);
        m.addAttribute("modalOpen", true);
        return "accounting/config/activities";
    }

    private void activityList(RegisterQuery q, Model m) {
        m.addAttribute("records", configuration.activities(q));
        listContext(m, "activities");
    }

    private void listContext(Model m, String section) {
        m.addAttribute("baseUrl", "/accounting/config/" + section);
        m.addAttribute("resourceKey", "gl-config-" + section);
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("registerStatuses", List.of("ACTIVE", "INACTIVE"));
    }

    @GetMapping("/activities/{id}")
    public String activity(@PathVariable long id, Model m) {
        m.addAttribute("activity", configuration.activity(id));
        m.addAttribute("breadcrumbCurrent", "config.activity");
        return "accounting/config/activity-detail";
    }

    @GetMapping("/activities/{id}/edit")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String editActivity(@PathVariable long id, Model m) {
        var a = configuration.activity(id);
        var f = new ActivityForm();
        f.setCode(a.code());
        f.setName(a.name());
        f.setDescription(a.description());
        f.setVersion(a.version());
        m.addAttribute("activityForm", f);
        m.addAttribute("activity", a);
        m.addAttribute("breadcrumbCurrent", "config.activity.edit");
        return "accounting/config/activity-edit";
    }

    @PostMapping("/activities/{id}")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String editActivity(
            @PathVariable long id,
            @Valid @ModelAttribute("activityForm") ActivityForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                configuration.editActivity(id, f);
                return "redirect:/accounting/config/activities/" + id + "?saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject(error(e));
            } catch (DataIntegrityViolationException e) {
                b.reject("error.conflict");
            }
        m.addAttribute("activity", configuration.activity(id));
        m.addAttribute("breadcrumbCurrent", "config.activity.edit");
        return "accounting/config/activity-edit";
    }

    @PostMapping("/activities/{id}/state")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String activityState(
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam boolean active,
            @RequestParam String reason,
            Model m) {
        try {
            configuration.activityState(id, version, active, reason);
            return "redirect:/accounting/config/activities/" + id + "?saved";
        } catch (OptimisticLockingFailureException e) {
            m.addAttribute("actionError", "error.stale");
        } catch (IllegalArgumentException e) {
            m.addAttribute("actionError", error(e));
        }
        return activity(id, m);
    }

    @GetMapping("/activities/choices")
    @ResponseBody
    public ChoicePage<ActivityTransactionService.Choice> activityChoices(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return configuration.activityChoices(search, page);
    }

    @GetMapping("/transactions")
    public String transactions(
            @ModelAttribute("query") RegisterQuery q,
            @RequestParam(required = false) Long activityId,
            @RequestParam(defaultValue = "false") boolean create,
            @RequestParam(required = false) Long created,
            Model m) {
        var f = new TransactionConfigForm();
        f.setActivityId(activityId);
        if (!m.containsAttribute("transactionForm")) m.addAttribute("transactionForm", f);
        transactionList(q, activityId, m);
        transactionOptions(f, m);
        if (create) m.addAttribute("modalOpen", true);
        if (created != null) {
            m.addAttribute("createdResource", configuration.transaction(created));
            m.addAttribute("modalOpen", true);
        }
        return "accounting/config/transactions";
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String createTransaction(
            @Valid @ModelAttribute("transactionForm") TransactionConfigForm f,
            BindingResult b,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/config/transactions?created="
                        + configuration.createTransaction(f);
            } catch (IllegalArgumentException e) {
                b.reject(error(e));
            } catch (DataIntegrityViolationException e) {
                b.reject("error.conflict");
            }
        transactionList(q, null, m);
        transactionOptions(f, m);
        m.addAttribute("modalOpen", true);
        return "accounting/config/transactions";
    }

    private void transactionList(RegisterQuery q, Long activity, Model m) {
        m.addAttribute("records", configuration.transactions(q, activity));
        m.addAttribute("activityId", activity);
        listContext(m, "transactions");
        var choices = new LinkedHashMap<Long, ActivityTransactionService.Choice>();
        configuration.activityChoices("", 0).items().forEach(c -> choices.put(c.id(), c));
        if (activity != null) {
            var a = configuration.activity(activity);
            choices.put(
                    a.id(),
                    new ActivityTransactionService.Choice(a.id(), a.code() + " " + a.name()));
        }
        m.addAttribute("filterActivities", choices.values());
    }

    private void transactionOptions(TransactionConfigForm f, Model m) {
        var choices = new LinkedHashMap<Long, ActivityTransactionService.Choice>();
        configuration.activityChoices("", 0).items().forEach(c -> choices.put(c.id(), c));
        if (f.getActivityId() != null)
            try {
                var a = configuration.activity(f.getActivityId());
                choices.put(
                        a.id(),
                        new ActivityTransactionService.Choice(a.id(), a.code() + " " + a.name()));
            } catch (IllegalArgumentException ignored) {
                /* A missing submitted choice stays a validation error. */
            }
        m.addAttribute("activities", choices.values());
        m.addAttribute("events", PostingMappingService.EVENTS);
        accountOptions(f, m);
    }

    private void accountOptions(PostingMappingForm f, Model m) {
        var choices = new LinkedHashMap<Long, AccountingService.Choice>();
        accounts.choices("", 0, true).items().forEach(c -> choices.put(c.id(), c));
        accounts.selected(
                        java.util.stream.Stream.of(f.getDebitAccountId(), f.getCreditAccountId())
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .forEach(c -> choices.put(c.id(), c));
        m.addAttribute("accounts", choices.values());
    }

    @GetMapping("/transactions/{id}")
    public String transaction(
            @PathVariable long id,
            @RequestParam(required = false) Long review,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        var detail = configuration.detail(id, review, q);
        m.addAttribute("detail", detail);
        m.addAttribute("selectedReview", review);
        m.addAttribute("transaction", detail.transaction());
        m.addAttribute("proposal", detail.proposal());
        m.addAttribute("effective", detail.effective());
        m.addAttribute("records", detail.pending());
        m.addAttribute("baseUrl", "/accounting/config/transactions/" + id);
        m.addAttribute("resourceKey", "gl-template-approvals");
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("breadcrumbCurrent", "config.transaction.details");
        return "accounting/config/transaction-detail";
    }

    @GetMapping("/transactions/{id}/template")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String template(@PathVariable long id, Model m) {
        var detail = configuration.detail(id, null, new RegisterQuery());
        var f = new TransactionTemplateForm();
        f.setVersion(detail.transaction().version());
        var p = detail.proposal();
        if (p != null) {
            f.setDebitAccountId(p.debitAccountId());
            f.setCreditAccountId(p.creditAccountId());
            f.setVoucherKind(p.voucherKind());
            f.setCashFlow(p.cashFlow());
            f.setPolicyReference(p.policyReference());
        }
        m.addAttribute("templateForm", f);
        templateContext(id, f, m);
        return "accounting/config/template-edit";
    }

    @PostMapping("/transactions/{id}/template")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String template(
            @PathVariable long id,
            @Valid @ModelAttribute("templateForm") TransactionTemplateForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/config/transactions/"
                        + id
                        + "?review="
                        + configuration.saveTemplate(id, f)
                        + "&saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (IllegalArgumentException e) {
                b.reject(error(e));
            } catch (DataIntegrityViolationException e) {
                b.reject("error.conflict");
            }
        templateContext(id, f, m);
        return "accounting/config/template-edit";
    }

    private void templateContext(long id, PostingMappingForm f, Model m) {
        m.addAttribute("transaction", configuration.transaction(id));
        m.addAttribute("breadcrumbCurrent", "config.template.edit");
        accountOptions(f, m);
    }

    @PostMapping("/transactions/{id}/templates/{template}/actions/{action}")
    public String review(
            @PathVariable long id,
            @PathVariable long template,
            @PathVariable String action,
            @RequestParam long version,
            @RequestParam long transactionVersion,
            @RequestParam String reason,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        try {
            configuration.reviewTemplate(id, template, version, transactionVersion, action, reason);
            return "redirect:/accounting/config/transactions/"
                    + id
                    + "?review="
                    + template
                    + "&saved";
        } catch (OptimisticLockingFailureException e) {
            m.addAttribute("actionError", "error.stale");
        } catch (IllegalArgumentException e) {
            m.addAttribute("actionError", error(e));
        }
        return transaction(id, template, q, m);
    }

    @PostMapping("/transactions/{id}/state")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String transactionState(
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam boolean active,
            @RequestParam String reason,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        try {
            configuration.transactionState(id, version, active, reason);
            return "redirect:/accounting/config/transactions/" + id + "?saved";
        } catch (OptimisticLockingFailureException e) {
            m.addAttribute("actionError", "error.stale");
        } catch (IllegalArgumentException e) {
            m.addAttribute("actionError", error(e));
        }
        return transaction(id, null, q, m);
    }

    @GetMapping("/transactions/choices")
    @ResponseBody
    public ChoicePage<PostingMappingService.TemplateChoice> templates(
            @RequestParam String event,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return templates.choices(event, search, page);
    }

    private static String error(IllegalArgumentException e) {
        return e.getMessage() != null
                        && (e.getMessage().startsWith("config.error.")
                                || e.getMessage().startsWith("accounting.error."))
                ? e.getMessage()
                : "error.invalid";
    }
}
