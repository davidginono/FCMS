package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.SourcePreparationForm;
import com.foresight.fcms.accounting.service.SourcePostingService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/accounting/sources")
@PreAuthorize("hasAuthority('accounting.read') and hasAuthority('finance.gl-source.read')")
public class PostingSourcesController {
    private final SourcePostingService sources;
    private final com.foresight.fcms.accounting.service.PostingMappingService templates;

    public PostingSourcesController(
            SourcePostingService sources,
            com.foresight.fcms.accounting.service.PostingMappingService templates) {
        this.templates = templates;
        this.sources = sources;
    }

    @InitBinder("form")
    public void bind(WebDataBinder binder) {
        binder.setAllowedFields("mappingId", "evidenceReference", "requestKey");
    }

    @GetMapping
    public String list(@ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", sources.list(q));
        m.addAttribute("baseUrl", "/accounting/sources");
        m.addAttribute("resourceKey", "gl-sources");
        m.addAttribute(
                "registerStatuses",
                java.util.List.of(
                        "BOOKS_PENDING",
                        "UNVERIFIED",
                        "OPENING_COVERED",
                        "WAITING_ORIGINAL",
                        "PERIOD_REQUIRED",
                        "PERIOD_CLOSED",
                        "UNMAPPED",
                        "READY",
                        "DRAFT",
                        "SUBMITTED",
                        "REJECTED",
                        "POSTED"));
        return "accounting/sources";
    }

    @GetMapping("/{kind}/{id}")
    public String detail(
            @PathVariable String kind,
            @PathVariable long id,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        var workspace = sources.workspace(kind, id, q);
        var form = new SourcePreparationForm();
        form.setMappingId(workspace.item().mappingId());
        m.addAttribute("form", form);
        show(kind, id, workspace, m);
        return "accounting/source-detail";
    }

    @PostMapping("/{kind}/{id}/prepare")
    @PreAuthorize("hasAuthority('accounting.write') and hasAuthority('finance.gl-source.read')")
    public String prepare(
            @PathVariable String kind,
            @PathVariable long id,
            @Valid @ModelAttribute("form") SourcePreparationForm form,
            BindingResult b,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        if (b.hasFieldErrors("mappingId") || b.hasFieldErrors("requestKey"))
            b.reject("error.invalid");
        if (!b.hasErrors())
            try {
                return "redirect:/accounting/journals/" + sources.prepare(kind, id, form);
            } catch (IllegalArgumentException e) {
                b.reject(
                        e.getMessage() != null
                                        && (e.getMessage().startsWith("gl.source.")
                                                || e.getMessage().startsWith("accounting.error."))
                                ? e.getMessage()
                                : "error.conflict");
            } catch (org.springframework.dao.DataAccessException e) {
                b.reject("error.conflict");
            }
        m.addAttribute("prepareError", true);
        show(kind, id, sources.workspace(kind, id, q), m);
        return "accounting/source-detail";
    }

    private void show(String kind, long id, SourcePostingService.Workspace workspace, Model m) {
        m.addAttribute("workspace", workspace);
        m.addAttribute("preparationKey", java.util.UUID.randomUUID());
        m.addAttribute("item", workspace.item());
        m.addAttribute("source", workspace.item().source());
        m.addAttribute("mapping", workspace.mapping());
        var choices =
                new java.util.LinkedHashMap<
                        Long,
                        com.foresight.fcms.accounting.service.PostingMappingService
                                .TemplateChoice>();
        if (!workspace.item().mappingEvent().isEmpty()) {
            templates
                    .choices(workspace.item().mappingEvent(), "", 0)
                    .items()
                    .forEach(c -> choices.put(c.id(), c));
            if (m.getAttribute("form") instanceof SourcePreparationForm form
                    && form.getMappingId() != null)
                try {
                    var selected = templates.find(form.getMappingId());
                    if (selected.event().equals(workspace.item().mappingEvent())
                            && selected.state().equals("APPROVED"))
                        choices.put(
                                selected.id(),
                                new com.foresight.fcms.accounting.service.PostingMappingService
                                        .TemplateChoice(
                                        selected.id(),
                                        selected.transactionCode()
                                                + " "
                                                + selected.transactionName()));
                } catch (IllegalArgumentException ignored) {
                    /* Missing submitted templates cannot become valid choices. */
                }
        }
        m.addAttribute("templates", choices.values());
        m.addAttribute("records", workspace.attempts());
        m.addAttribute("baseUrl", "/accounting/sources/" + kind + "/" + id);
        m.addAttribute("resourceKey", "gl-source-attempts");
        m.addAttribute("hideDateFilters", true);
        m.addAttribute("breadcrumbCurrent", "gl.source.details");
    }
}
