package com.foresight.fcms.auth.controller;

import com.foresight.fcms.auth.dto.UserForm;
import com.foresight.fcms.auth.service.UserAdministrationService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService users;

    public UserAdministrationController(UserAdministrationService users) {
        this.users = users;
    }

    @GetMapping
    public String list(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", users.list(q));
        m.addAttribute("registerStatuses", java.util.List.of("active", "inactive", "suspended"));
        return "users/index";
    }

    @GetMapping("/new")
    public String create(Model m) {
        formContext(m, null);
        m.addAttribute("form", new UserForm());
        return "users/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model m) {
        m.addAttribute("record", users.find(id));
        m.addAttribute("breadcrumbCurrent", "iam.user.details");
        return "users/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model m) {
        formContext(m, id);
        m.addAttribute("form", users.form(id));
        return "users/form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") UserForm f, BindingResult b, Model m) {
        return save(null, f, b, m);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") UserForm f,
            BindingResult b,
            Model m) {
        if (!b.hasErrors())
            try {
                return "redirect:/admin/users/" + users.save(id, f);
            } catch (IllegalArgumentException e) {
                b.reject(
                        "error.passwordDefault".equals(e.getMessage())
                                ? "error.passwordDefault"
                                : "error.invalid");
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                b.reject("error.conflict");
            }
        formContext(m, id);
        return "users/form";
    }

    private void formContext(Model model, Long id) {
        model.addAllAttributes(users.options());
        String title = id == null ? "iam.user.create" : "iam.user.edit";
        model.addAttribute("formAction", id == null ? "/admin/users" : "/admin/users/" + id);
        model.addAttribute("formTitle", title);
        model.addAttribute("breadcrumbCurrent", title);
        model.addAttribute("formPrimaryAction", id == null ? "common.create" : "common.save");
    }
}
