package com.foresight.fcms.sales.controller;

import com.foresight.fcms.sales.dto.SalesForm;
import com.foresight.fcms.sales.dto.SalesItem;
import com.foresight.fcms.sales.model.SalesKind;
import com.foresight.fcms.sales.service.SalesPdfService;
import com.foresight.fcms.sales.service.SalesService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import jakarta.validation.Valid;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/sales/{workspace:prospects|invoices}")
public class SalesController {
    private final SalesService sales;
    private final SalesPdfService pdf;

    public SalesController(SalesService sales, SalesPdfService pdf) {
        this.sales = sales;
        this.pdf = pdf;
    }

    @InitBinder("form")
    public void bind(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(50);
        binder.setAllowedFields(
                "clientType",
                "clientName",
                "region",
                "contactName",
                "phone",
                "email",
                "documentNumber",
                "documentDate",
                "validUntil",
                "currency",
                "terms",
                "billingAddress",
                "billingCity",
                "billingTin",
                "paymentMethod",
                "version",
                "requestKey",
                "items[*].description",
                "items[*].amount");
    }

    @ModelAttribute
    public void context(@PathVariable String workspace, Model m) {
        var k = SalesKind.from(workspace);
        m.addAttribute("kind", k);
        m.addAttribute("baseUrl", k.getRoute());
        m.addAttribute("resourceKey", workspace);
        m.addAttribute("clientTypes", k.getTypes());
        m.addAttribute("currencies", List.of("TZS", "USD", "KES", "EUR", "GBP"));
        m.addAttribute("paymentMethods", List.of("CASH", "CHEQUE", "BANK_TRANSFER"));
    }

    @GetMapping
    public String index(
            @PathVariable String workspace, @ModelAttribute("query") RegisterQuery q, Model m) {
        m.addAttribute("records", sales.list(SalesKind.from(workspace), q));
        return "sales/index";
    }

    @GetMapping("/new")
    public String create(@PathVariable String workspace, Model m) {
        // The public application interface checks write permission before rendering a blank task.
        sales.formAccess(SalesKind.from(workspace));
        m.addAttribute("form", new SalesForm());
        formContext(workspace, null, m);
        return "sales/form";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable String workspace,
            @PathVariable long id,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        var k = SalesKind.from(workspace);
        m.addAttribute("record", sales.find(k, id));
        m.addAttribute("records", sales.history(k, id, q));
        m.addAttribute("baseUrl", k.getRoute() + "/" + id);
        m.addAttribute("resourceKey", workspace + "-history");
        return "sales/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String workspace, @PathVariable long id, Model m) {
        m.addAttribute("form", sales.form(SalesKind.from(workspace), id));
        formContext(workspace, id, m);
        return "sales/form";
    }

    private void formContext(String workspace, Long id, Model m) {
        var k = SalesKind.from(workspace);
        m.addAttribute("editing", id != null);
        m.addAttribute("formAction", k.getRoute() + (id == null ? "" : "/" + id));
        m.addAttribute("formTitle", k.getClaim() + (id == null ? ".create" : ".edit"));
        m.addAttribute("breadcrumbUrl", k.getRoute());
        m.addAttribute("breadcrumbLabel", k.getTitle());
        m.addAttribute("breadcrumbCurrent", k.getClaim() + (id == null ? ".create" : ".edit"));
    }

    @PostMapping(
            value = {"", "/{id}"},
            params = "addItem")
    public String add(
            @PathVariable String workspace,
            @PathVariable(required = false) Long id,
            @ModelAttribute("form") SalesForm f,
            BindingResult b,
            Model m) {
        sales.formAccess(SalesKind.from(workspace));
        if (id != null) sales.find(SalesKind.from(workspace), id);
        if (f.getItems() != null && f.getItems().size() < 50) f.getItems().add(new SalesItem());
        else b.reject("sales.error.items");
        m.addAttribute("unsavedDraft", true);
        formContext(workspace, id, m);
        return "sales/form";
    }

    @PostMapping(
            value = {"", "/{id}"},
            params = "removeItem")
    public String remove(
            @PathVariable String workspace,
            @PathVariable(required = false) Long id,
            @RequestParam int removeItem,
            @ModelAttribute("form") SalesForm f,
            BindingResult b,
            Model m) {
        sales.formAccess(SalesKind.from(workspace));
        if (id != null) sales.find(SalesKind.from(workspace), id);
        if (!b.hasErrors()
                && f.getItems() != null
                && f.getItems().size() > 1
                && removeItem >= 0
                && removeItem < f.getItems().size()) f.getItems().remove(removeItem);
        m.addAttribute("unsavedDraft", true);
        formContext(workspace, id, m);
        return "sales/form";
    }

    @PostMapping(
            value = {"", "/{id}"},
            params = {"!addItem", "!removeItem"})
    public String save(
            @PathVariable String workspace,
            @PathVariable(required = false) Long id,
            @Valid @ModelAttribute("form") SalesForm f,
            BindingResult b,
            Model m) {
        var k = SalesKind.from(workspace);
        sales.formAccess(k);
        if (!b.hasErrors())
            try {
                sales.save(k, id, f);
                return "redirect:" + k.getRoute() + "?saved";
            } catch (OptimisticLockingFailureException e) {
                b.reject("error.stale");
            } catch (DataIntegrityViolationException e) {
                b.reject("error.conflict");
            } catch (IllegalArgumentException e) {
                b.reject(
                        List.of("error.dates", "sales.error.items", "error.conflict")
                                        .contains(e.getMessage())
                                ? e.getMessage()
                                : "error.invalid");
            }
        formContext(workspace, id, m);
        return "sales/form";
    }

    @GetMapping("/{id}/document.pdf")
    public ResponseEntity<byte[]> document(
            @PathVariable String workspace,
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean download) {
        var k = SalesKind.from(workspace);
        var d = sales.export(k, id);
        var body = pdf.render(d);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                                .filename(
                                        (k == SalesKind.PROSPECT ? "proforma-" : "invoice-")
                                                + id
                                                + ".pdf")
                                .build()
                                .toString())
                .body(body);
    }
}
