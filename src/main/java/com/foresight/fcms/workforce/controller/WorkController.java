package com.foresight.fcms.workforce.controller;

import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.dto.WorkForm;
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
                        : java.util.List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED"));
    }

    @GetMapping
    public String index(
            @PathVariable String workspace, @ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", work.list(WorkKind.from(workspace), q));
        m.addAllAttributes(work.filters(WorkKind.from(workspace), q.getClientId()));
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
        m.addAttribute("record", work.find(WorkKind.from(workspace), id));
        m.addAttribute("participants", work.participants(WorkKind.from(workspace), id));
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
            Model m) {
        return save(workspace, null, f, b, m);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable String workspace,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") WorkForm f,
            BindingResult b,
            Model m) {
        var k = WorkKind.from(workspace);
        if (!b.hasErrors())
            try {
                return "redirect:" + k.getRoute() + "/" + work.save(k, id, f);
            } catch (IllegalArgumentException e) {
                b.reject("error.invalid");
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
            @RequestParam(defaultValue = "") String comment) {
        var k = WorkKind.from(workspace);
        work.transition(k, id, version, action, comment);
        return "redirect:" + k.getRoute() + "/" + id;
    }
}
