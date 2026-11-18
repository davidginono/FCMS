package com.foresight.fcms.client.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.client.service.ClientArchiveService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/clients/{slug:saccos|amcos|unions|microfinance|others}")
@PreAuthorize("hasAuthority('client.read') and hasAuthority('client.write')")
public class ClientArchiveController {
    private final ClientArchiveService archive;

    public ClientArchiveController(ClientArchiveService archive) {
        this.archive = archive;
    }

    @ModelAttribute
    public void context(@PathVariable String slug, Model m) {
        m.addAttribute("clientLabel", archive.register(slug).label());
        m.addAttribute("clientRoute", "/clients/" + slug);
    }

    @GetMapping("/archive")
    public String list(
            @PathVariable String slug, @ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", archive.list(slug, q));
        m.addAttribute("baseUrl", "/clients/" + slug + "/archive");
        m.addAttribute("resourceKey", "client-archive-" + slug);
        m.addAttribute("hideDateFilters", true);
        return "clients/archive";
    }

    @GetMapping({"/{id}/archive", "/archive/{id}"})
    public String detail(@PathVariable String slug, @PathVariable long id, Model m) {
        m.addAttribute("record", archive.find(slug, id));
        return "clients/archive-detail";
    }

    @PostMapping("/{id}/visibility")
    public String change(
            @PathVariable String slug,
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam boolean visible,
            @RequestParam String reason,
            Model m, RedirectAttributes redirect) {
        try {
            archive.visibility(slug, id, version, visible, reason);
            return ResourceFeedback.success(redirect, "/clients/" + slug + (visible ? "/" + id : "/archive"), "feedback.saved", null);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            m.addAttribute("actionError", "error.stale");
        } catch (IllegalArgumentException e) {
            m.addAttribute("actionError", "error.invalid");
        }
        m.addAttribute("reason", reason);
        return detail(slug, id, m);
    }
}
