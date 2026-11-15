package com.foresight.fcms.backup.controller;

import com.foresight.fcms.backup.service.*;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/backups")
public class BackupController {
    private final BackupCatalogService catalog;
    private final BackupExecutionService execution;

    public BackupController(BackupCatalogService catalog, BackupExecutionService execution) {
        this.catalog = catalog;
        this.execution = execution;
    }

    @GetMapping
    public String index(
            @ModelAttribute("query") com.foresight.fcms.shared.dto.RegisterQuery q, Model m) {
        m.addAttribute("records", catalog.list(q));
        m.addAttribute("baseUrl", "/backups");
        m.addAttribute("resourceKey", "backups");
        m.addAttribute(
                "registerStatuses", java.util.List.of("QUEUED", "RUNNING", "READY", "FAILED"));
        return "backups/index";
    }

    @PostMapping
    public String create() {
        catalog.request();
        return "redirect:/backups";
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=fcms-" + id + ".dump")
                .body(execution.download(id));
    }
}
