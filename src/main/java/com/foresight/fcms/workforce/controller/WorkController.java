package com.foresight.fcms.workforce.controller;

import com.foresight.fcms.shared.controller.FormErrors;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.workforce.dto.WorkForm;
import com.foresight.fcms.workforce.dto.WorkQuery;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/{workspace:tasks|leaves|staff-reports}")
public class WorkController {
    private final WorkService work;

    public WorkController(WorkService w) {
        work = w;
    }

    @ModelAttribute
    public void context(@PathVariable String workspace, Model m) {
        var k = WorkKind.from(workspace);
        m.addAttribute("kind", k);
        m.addAttribute("baseUrl", k.getRoute());
        m.addAttribute("resourceKey", workspace);
        m.addAttribute(
                "registerStatuses",
                k == WorkKind.TASK
                        ? java.util.List.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED")
                        : k == WorkKind.REPORT
                                ? java.util.List.of(
                                        "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED")
                                : java.util.List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED"));
    }

    @GetMapping
    public String index(
            @PathVariable String workspace, @ModelAttribute("query") WorkQuery q, Model m) {
        m.addAttribute("records", work.list(WorkKind.from(workspace), q));
        m.addAllAttributes(work.filters(WorkKind.from(workspace), q));
        return "work/index";
    }

    @GetMapping("/new")
    public String create(@PathVariable String workspace, Model m) {
        var k = WorkKind.from(workspace);
        var form = new WorkForm();
        m.addAllAttributes(work.options(k, form));
        m.addAttribute("form", form);
        m.addAttribute("formAction", k.getRoute());
        return "work/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String workspace, @PathVariable long id, Model m) {
        var details = work.details(WorkKind.from(workspace), id);
        m.addAttribute("record", details.record());
        m.addAttribute("participants", details.participants());
        m.addAttribute("commands", details.commands());
        return "work/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String workspace, @PathVariable long id, Model m) {
        var k = WorkKind.from(workspace);
        var f = work.form(k, id);
        m.addAllAttributes(work.options(k, f));
        m.addAttribute("form", f);
        m.addAttribute("formAction", k.getRoute() + "/" + id);
        return "work/form";
    }

    @PostMapping
    public String create(
            @PathVariable String workspace,
            @Valid @ModelAttribute("form") WorkForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        return save(workspace, null, f, b, m, redirect);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable String workspace,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") WorkForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        var k = WorkKind.from(workspace);
        if (!b.hasErrors())
            try {
                long saved = work.save(k, id, f);
                return ResourceFeedback.success(redirect, id == null ? k.getRoute() : k.getRoute() + "/" + saved,
                        id != null ? "feedback.saved" : k == WorkKind.TASK ? "feedback.taskCreated" : k == WorkKind.LEAVE ? "feedback.leaveCreated" : "feedback.staffReportCreated", k.getRoute() + "/" + saved);
            } catch (IllegalArgumentException e) {
                FormErrors.reject(b, e,
                        "work.requestConflict".equals(e.getMessage())
                                ? "work.requestConflict"
                                : "error.invalid");
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                b.reject("error.stale");
            }
        m.addAllAttributes(work.options(k, f));
        m.addAttribute("formAction", k.getRoute() + (id == null ? "" : "/" + id));
        return "work/form";
    }

    @PostMapping("/{id}/actions/{action}")
    public String action(
            @PathVariable String workspace,
            @PathVariable long id,
            @PathVariable String action,
            @RequestParam long version,
            @RequestParam(defaultValue = "") String comment,
            Model model,
            jakarta.servlet.http.HttpServletResponse response, RedirectAttributes redirect) {
        var kind = WorkKind.from(workspace);
        try {
            work.transition(kind, id, version, action, comment);
            return ResourceFeedback.success(redirect, kind.getRoute() + "/" + id, "feedback.saved", null);
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException error) {
            response.setStatus(409);
            model.addAttribute("actionError", true);
            return detail(workspace, id, model);
        }
    }
}
