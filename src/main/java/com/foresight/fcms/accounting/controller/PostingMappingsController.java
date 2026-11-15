package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.service.PostingMappingService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Retained bookmarks resolve to their transaction and exact historical template evidence. */
@Controller
@RequestMapping("/accounting/posting-mappings")
@PreAuthorize("hasAuthority('accounting.read')")
public class PostingMappingsController {
    private final PostingMappingService templates;

    public PostingMappingsController(PostingMappingService templates) {
        this.templates = templates;
    }

    @GetMapping
    public String list() {
        return "redirect:/accounting/config/transactions";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('accounting.setup')")
    public String create() {
        return "redirect:/accounting/config/transactions?create=true";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id) {
        return "redirect:/accounting/config/transactions/"
                + templates.find(id).transactionId()
                + "?review="
                + id;
    }

    @PostMapping({"", "/{id}/actions/{action}"})
    public void retiredCommand() {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GONE);
    }
}
