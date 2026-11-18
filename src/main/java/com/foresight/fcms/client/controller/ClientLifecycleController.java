package com.foresight.fcms.client.controller;

import com.foresight.fcms.client.dto.ClientContractForm;
import com.foresight.fcms.client.service.ClientLifecycleService;
import com.foresight.fcms.shared.controller.FormErrors;
import com.foresight.fcms.shared.controller.ResourceFeedback;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients/{slug:saccos|amcos|unions|microfinance|others}/{id}")
@PreAuthorize("hasAuthority('client.read') and hasAuthority('client.write')")
public class ClientLifecycleController {
    private final ClientLifecycleService clients;

    public ClientLifecycleController(ClientLifecycleService clients) {
        this.clients = clients;
    }

    private ClientLifecycleService.ClientAction context(String slug, long id, Model model) {
        var record = clients.find(slug, id);
        model.addAttribute("record", record);
        model.addAttribute("clientRoute", "/clients/" + slug);
        return record;
    }

    @GetMapping("/lifecycle")
    public String confirm(@PathVariable String slug, @PathVariable long id, Model model) {
        context(slug, id, model);
        return "clients/client-lifecycle";
    }

    @PostMapping("/lifecycle")
    public String change(
            @PathVariable String slug,
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam String action,
            Model model,
            RedirectAttributes redirect) {
        try {
            Long year = clients.change(slug, id, version, action);
            return ResourceFeedback.success(
                    redirect,
                    "/clients/" + slug + (year == null ? "" : "?financialYearId=" + year),
                    "deactivate".equals(action)
                            ? "feedback.clientInactive"
                            : "feedback.clientDeleted",
                    null);
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("actionError", "error.stale");
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("actionError", "error.clientHistory");
        } catch (IllegalArgumentException e) {
            model.addAttribute("actionError", "error.clientHistory");
        }
        return confirm(slug, id, model);
    }

    @GetMapping("/contract/edit")
    public String contract(@PathVariable String slug, @PathVariable long id, Model model) {
        var record = context(slug, id, model);
        if (!model.containsAttribute("form")) {
            var form = new ClientContractForm();
            form.setVersion(record.version());
            model.addAttribute("form", form);
        }
        return "clients/client-contract-form";
    }

    @PostMapping("/contract")
    public String saveContract(
            @PathVariable String slug,
            @PathVariable long id,
            @Valid @ModelAttribute("form") ClientContractForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes redirect) {
        if (!errors.hasErrors())
            try {
                clients.updateContract(slug, id, form.getVersion(), form.getContractUpload());
                return ResourceFeedback.success(
                        redirect, "/clients/" + slug + "/" + id, "feedback.contractUpdated", null);
            } catch (OptimisticLockingFailureException e) {
                errors.reject("error.stale");
            } catch (IllegalArgumentException e) {
                if (e instanceof com.foresight.fcms.shared.validation.FieldValidationException)
                    FormErrors.reject(errors, e, "error.invalid");
                else
                    errors.rejectValue(
                            "contractUpload",
                            java.util.Set.of(
                                                    "error.uploadBusy",
                                                    "error.uploadRejected",
                                                    "error.uploadUnavailable")
                                            .contains(e.getMessage())
                                    ? e.getMessage()
                                    : "error.invalid");
            }
        return contract(slug, id, model);
    }
}
