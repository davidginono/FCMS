package com.foresight.fcms.finance.expense.controller;

import com.foresight.fcms.finance.expense.dto.ExpenseCategoryQuery;
import com.foresight.fcms.finance.expense.dto.ExpenseForm;
import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.finance.expense.service.ExpenseCategoryService;
import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/finance/expenses")
public class ExpenseController {
    private final ExpenseService expenses;
    private final ExpenseCategoryService categories;

    public ExpenseController(ExpenseService e, ExpenseCategoryService categories) {
        expenses = e;
        this.categories = categories;
    }

    @ModelAttribute
    public void base(Model m) {
        m.addAttribute("baseUrl", "/finance/expenses");
        m.addAttribute("resourceKey", "expenses");
        m.addAttribute(
                "registerStatuses",
                java.util.List.of(
                        "draft", "submitted", "approved", "paid", "rejected", "reversed"));
    }

    @GetMapping
    public String index(@ModelAttribute("query") ExpenseQuery q, Model m) {
        m.addAttribute("records", expenses.list(q));
        m.addAttribute("expenseFilters", true);
        return "expenses/index";
    }

    @GetMapping("/categories")
    public String categories(@ModelAttribute("query") ExpenseCategoryQuery q, Model m) {
        var summary = categories.summary(q);
        m.addAttribute("records", summary.records());
        m.addAttribute("total", summary.total());
        m.addAttribute("baseUrl", "/finance/expenses/categories");
        m.addAttribute("resourceKey", "expense-categories");
        m.addAttribute("expenseFilters", true);
        m.addAttribute("categorySummary", true);
        m.addAttribute("breadcrumbUrl", "/finance/expenses");
        m.addAttribute("breadcrumbLabel", "nav.expenses");
        m.addAttribute("breadcrumbCurrent", "expense.categories");
        return "expenses/categories";
    }

    @GetMapping("/new")
    public String create(Model m) {
        m.addAttribute("form", new ExpenseForm());
        m.addAttribute("formAction", "/finance/expenses");
        return "expenses/form";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable long id, @ModelAttribute("historyQuery") RegisterQuery q, Model m) {
        var details = expenses.details(id, q);
        m.addAttribute("record", details.record());
        m.addAttribute("historyQuery", q);
        m.addAttribute("historyRecords", details.history());
        m.addAttribute("paymentEvidenceAvailable", details.paymentEvidenceAvailable());
        m.addAttribute("requestKey", java.util.UUID.randomUUID());
        return "expenses/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model m) {
        m.addAttribute("form", expenses.form(id));
        m.addAttribute("formAction", "/finance/expenses/" + id);
        return "expenses/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ExpenseForm f, BindingResult b, Model m) {
        return save(null, f, b, m);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") ExpenseForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/finance/expenses/" + expenses.save(id, f);
            } catch (IllegalArgumentException e) {
                b.reject("error.invalid");
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                b.reject("error.stale");
            }
        m.addAttribute("formAction", id == null ? "/finance/expenses" : "/finance/expenses/" + id);
        return "expenses/form";
    }

    @PostMapping("/{id}/actions/{action}")
    public String action(
            @PathVariable long id,
            @PathVariable String action,
            @RequestParam long version,
            @RequestParam(defaultValue = "") String comment,
            @RequestParam(defaultValue = "") String reference,
            @RequestParam java.util.UUID requestKey,
            Model m) {
        try {
            expenses.transition(id, version, action, comment, reference, requestKey);
            return "redirect:/finance/expenses/" + id;
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
            m.addAttribute("actionError", true);
            return detail(id, new RegisterQuery(), m);
        }
    }
}
