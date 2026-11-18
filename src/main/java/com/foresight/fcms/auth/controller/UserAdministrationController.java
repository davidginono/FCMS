package com.foresight.fcms.auth.controller;

import com.foresight.fcms.shared.controller.FormErrors;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.auth.dto.UserForm;
import com.foresight.fcms.auth.dto.UserFormSession;
import com.foresight.fcms.auth.service.AccessService;
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
    private final AccessService access;

    public UserAdministrationController(UserAdministrationService users, AccessService access) {
        this.users = users;
        this.access = access;
    }

    @GetMapping("/form-session")
    @ResponseBody
    public org.springframework.http.ResponseEntity<UserFormSession> formSession(
            org.springframework.security.web.csrf.CsrfToken csrf) {
        var actor = access.require("iam.user.manage");
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new UserFormSession(Long.toString(actor.id()), csrf.getParameterName(), csrf.getToken()));
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
        access.require("iam.user.manage");
        return "redirect:/admin/users/" + id + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model m) {
        formContext(m, id);
        m.addAttribute("form", users.form(id));
        return "users/form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") UserForm f, BindingResult b, Model m, RedirectAttributes redirect) {
        return save(null, f, b, m, redirect);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") UserForm f,
            BindingResult b,
            Model m, RedirectAttributes redirect) {
        if (!b.hasErrors())
            try {
                long saved = users.save(id, f);
                return ResourceFeedback.success(redirect, "/admin/users", id == null ? "feedback.userCreated" : "feedback.saved", "/admin/users/" + saved + "/edit");
            } catch (IllegalArgumentException e) {
                if (java.util.Set.of("error.passwordDefault", "error.password").contains(e.getMessage())) b.rejectValue("password", e.getMessage());
                else FormErrors.reject(b, e,
                        "error.passwordDefault".equals(e.getMessage())
                                ? "error.passwordDefault"
                                : "error.lastAdmin".equals(e.getMessage())
                                        ? "error.lastAdmin" : "error.invalid");
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (org.springframework.dao.DataAccessException e) {
                FormErrors.conflict(b, e, java.util.Map.of("users_email_lower_uq", "email", "users_email_uq", "email"));
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
