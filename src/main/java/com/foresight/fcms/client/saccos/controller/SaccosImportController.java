package com.foresight.fcms.client.saccos.controller;

import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.client.saccos.service.SaccosImportService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/clients/saccos/import")
public class SaccosImportController {
    private final SaccosImportService imports;
    private final MessageSource messages;

    public SaccosImportController(SaccosImportService imports, MessageSource messages) {
        this.imports = imports;
        this.messages = messages;
    }

    @ModelAttribute
    public void base(Model model) {
        model.addAttribute("breadcrumbUrl", "/clients/saccos");
        model.addAttribute("breadcrumbLabel", "client.category.saccos");
        model.addAttribute("breadcrumbCurrent", "import.title");
        model.addAttribute("resourceKey", "saccos-import-preview");
    }

    @GetMapping
    public String upload(Model model) {
        imports.formAccess();
        var form = new SaccosImportUpload();
        form.setRequestKey(UUID.randomUUID());
        model.addAttribute("upload", form);
        return uploadView(model);
    }

    private String uploadView(Model model) {
        model.addAttribute("columns", imports.columns());
        model.addAttribute("drafts", imports.drafts());
        return "clients/import-upload";
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fcms-saccos-import.csv\"")
                .body(imports.template());
    }

    @PostMapping
    public String stage(
            @ModelAttribute("upload") SaccosImportUpload form,
            BindingResult binding,
            Model model,
            Locale locale,
            HttpServletResponse response) {
        imports.formAccess();
        if (binding.hasErrors())
            model.addAttribute(
                    "formError", messages.getMessage("import.error.request", null, locale));
        else
            try {
                return "redirect:/clients/saccos/import/"
                        + imports.stage(form.getFile(), form.getRequestKey());
            } catch (IllegalArgumentException error) {
                model.addAttribute(
                        Set.of(
                                                "import.error.rate",
                                                "import.error.drafts",
                                                "import.error.request",
                                                "error.invalid")
                                        .contains(String.valueOf(error.getMessage()))
                                ? "formError"
                                : "fileError",
                        error(error, locale));
            }
        response.setStatus(422);
        return uploadView(model);
    }

    @GetMapping("/{token}")
    public String preview(
            @PathVariable UUID token, @ModelAttribute("query") RegisterQuery query, Model model) {
        var preview = imports.preview(token, query);
        model.addAttribute("preview", preview);
        model.addAttribute("records", preview.records());
        model.addAttribute("baseUrl", "/clients/saccos/import/" + token);
        model.addAttribute("breadcrumbCurrent", "import.preview");
        return "clients/import-preview";
    }

    @PostMapping("/{token}/commit")
    public String commit(
            @PathVariable UUID token, Model model, Locale locale, HttpServletResponse response) {
        try {
            return "redirect:/clients/saccos/import/"
                    + token
                    + (imports.commit(token) ? "?saved" : "");
        } catch (IllegalArgumentException error) {
            model.addAttribute("actionError", error(error, locale));
            response.setStatus(409);
        } catch (DataAccessException error) {
            model.addAttribute(
                    "actionError", messages.getMessage("import.error.commit", null, locale));
            response.setStatus(409);
        }
        return preview(token, new RegisterQuery(), model);
    }

    @PostMapping("/{token}/discard")
    public String discard(@PathVariable UUID token) {
        imports.discard(token);
        return "redirect:/clients/saccos/import/" + token;
    }

    private String error(IllegalArgumentException error, Locale locale) {
        String code = error.getMessage();
        if (code == null
                || !(code.startsWith("import.error.")
                        || Set.of(
                                        "error.invalid",
                                        "error.uploadBusy",
                                        "error.uploadRejected",
                                        "error.uploadUnavailable")
                                .contains(code))) code = "error.invalid";
        Object[] args =
                error instanceof SaccosImportFailure failure
                        ? new Object[] {failure.line(), failure.field()}
                        : new Object[0];
        return messages.getMessage(
                code, args, messages.getMessage("error.invalid", null, locale), locale);
    }
}
