package com.foresight.fcms.client.category.controller;

import com.foresight.fcms.client.category.dto.ClientCategoryFilter;
import com.foresight.fcms.client.category.dto.ClientCategoryForm;
import com.foresight.fcms.client.category.dto.ClientCategoryRowDto;
import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.category.model.ClientCategoryReportType;
import com.foresight.fcms.client.category.service.ClientCategoryPdfService;
import com.foresight.fcms.client.category.service.ClientCategoryService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/clients")
public class ClientCategoryController {

    private static final String CATEGORY_PATH = "/{categorySlug:amcos|unions|microfinance|others}";

    private final ClientCategoryService clientCategoryService;
    private final ClientCategoryPdfService pdfService;

    public ClientCategoryController(
            ClientCategoryService clientCategoryService, ClientCategoryPdfService pdfService) {
        this.clientCategoryService = clientCategoryService;
        this.pdfService = pdfService;
    }

    @GetMapping(CATEGORY_PATH)
    public String clients(
            @PathVariable String categorySlug,
            @ModelAttribute("filter") ClientCategoryFilter filter,
            Model model) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        populateBaseModel(model, category);
        if (filter.isRevenueView()) {
            var result =
                    clientCategoryService.findClients(
                            category,
                            filter,
                            PageRequest.of(
                                    filter.getPage(), filter.getSize(), Sort.by("clientName")));
            model.addAttribute("rows", result.page().getContent());
            model.addAttribute("page", result.page());
            model.addAttribute("totals", result.totals());
            model.addAttribute(
                    "reportType", ClientCategoryReportType.from(filter.getReportType(), category));
            return "clients/category-revenue";
        }

        var result =
                clientCategoryService.findClients(
                        category,
                        filter,
                        PageRequest.of(
                                filter.getPage(),
                                filter.getSize(),
                                Sort.by("clientName").ascending()));
        model.addAttribute("rows", result.page().getContent());
        model.addAttribute("page", result.page());
        model.addAttribute("totals", result.totals());
        model.addAttribute("reportType", ClientCategoryReportType.FULL);
        return "clients/client-category";
    }

    @GetMapping(CATEGORY_PATH + "/new")
    public String newClient(@PathVariable String categorySlug, Model model) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        populateBaseModel(model, category);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", clientCategoryService.newForm());
        }
        model.addAttribute("formAction", category.getRoute());
        model.addAttribute("formTitle", category.getCreateLabel());
        return "clients/client-category-form";
    }

    @PostMapping(CATEGORY_PATH)
    public String createClient(
            @PathVariable String categorySlug,
            @Valid @ModelAttribute("form") ClientCategoryForm form,
            BindingResult bindingResult,
            @RequestParam(name = "contractUpload", required = false) MultipartFile contractUpload,
            Model model,
            RedirectAttributes redirectAttributes) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        if (!bindingResult.hasErrors()) {
            try {
                Long id = clientCategoryService.createClient(category, form, contractUpload);
                return "redirect:" + category.getRoute() + "/" + id + "?saved";
            } catch (IllegalArgumentException e) {
                bindingResult.reject(
                        java.util.Set.of(
                                                "error.uploadBusy",
                                                "error.uploadRejected",
                                                "error.uploadUnavailable")
                                        .contains(e.getMessage())
                                ? e.getMessage()
                                : "error.invalid");
            }
        }
        populateBaseModel(model, category);
        model.addAttribute("formAction", category.getRoute());
        return "clients/client-category-form";
    }

    @GetMapping(CATEGORY_PATH + "/{id}")
    public String viewClient(
            @PathVariable String categorySlug, @PathVariable Long id, Model model) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        populateBaseModel(model, category);
        model.addAttribute("client", clientCategoryService.findClient(category, id));
        return "clients/client-category-detail";
    }

    @GetMapping(CATEGORY_PATH + "/{id}/edit")
    public String editClient(
            @PathVariable String categorySlug, @PathVariable Long id, Model model) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        populateBaseModel(model, category);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", clientCategoryService.formFor(category, id));
        }
        model.addAttribute("formAction", category.getRoute() + "/" + id);
        model.addAttribute("formTitle", "Update " + category.getSingularLabel());
        model.addAttribute("clientId", id);
        return "clients/client-category-form";
    }

    @PostMapping(CATEGORY_PATH + "/{id}")
    public String updateClient(
            @PathVariable String categorySlug,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") ClientCategoryForm form,
            BindingResult bindingResult,
            @RequestParam(name = "contractUpload", required = false) MultipartFile contractUpload,
            Model model,
            RedirectAttributes redirectAttributes) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        if (!bindingResult.hasErrors()) {
            try {
                clientCategoryService.updateClient(category, id, form, contractUpload);
                return "redirect:" + category.getRoute() + "/" + id + "?saved";
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                bindingResult.reject("error.stale");
            } catch (IllegalArgumentException e) {
                bindingResult.reject(
                        java.util.Set.of(
                                                "error.uploadBusy",
                                                "error.uploadRejected",
                                                "error.uploadUnavailable")
                                        .contains(e.getMessage())
                                ? e.getMessage()
                                : "error.invalid");
            }
        }
        populateBaseModel(model, category);
        model.addAttribute("formAction", category.getRoute() + "/" + id);
        model.addAttribute("clientId", id);
        return "clients/client-category-form";
    }

    @PostMapping(CATEGORY_PATH + "/{id}/delete")
    public String deleteClient(
            @PathVariable String categorySlug,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        return "redirect:" + category.getRoute() + "/" + id + "/archive";
    }

    @GetMapping(CATEGORY_PATH + "/{id}/contract")
    public ResponseEntity<Resource> contract(
            @PathVariable String categorySlug, @PathVariable Long id) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        Resource contract = clientCategoryService.contract(category, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract.pdf\"")
                .body(contract);
    }

    @GetMapping(CATEGORY_PATH + "/export.csv")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public void exportCsv(
            @PathVariable String categorySlug,
            @ModelAttribute("filter") ClientCategoryFilter filter,
            HttpServletResponse response)
            throws IOException {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + category.getSlug() + ".csv\"");
        try (PrintWriter writer = response.getWriter()) {
            writer.println(String.join(",", listHeaders(category)));
            for (ClientCategoryRowDto row : clientCategoryService.exportClients(category, filter)) {
                writer.println(String.join(",", listValues(category, row)));
            }
        }
    }

    @GetMapping(CATEGORY_PATH + "/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printPdf(
            @PathVariable String categorySlug,
            @ModelAttribute("filter") ClientCategoryFilter filter) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        var export = clientCategoryService.exportClientsWithTotals(category, filter);
        return pdf(
                category.getSlug() + ".pdf",
                pdfService.listPdf(category, export.rows(), export.totals()));
    }

    @GetMapping(CATEGORY_PATH + "/revenues/export.csv")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public void exportRevenueCsv(
            @PathVariable String categorySlug,
            @ModelAttribute("filter") ClientCategoryFilter filter,
            HttpServletResponse response)
            throws IOException {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        var report = clientCategoryService.revenueReport(category, filter);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + category.getSlug() + "-report.csv\"");
        try (PrintWriter writer = response.getWriter()) {
            if (!report.reportType().isFull()) {
                writer.println(
                        "No,"
                                + csv(category.getNameLabel())
                                + ","
                                + csv(report.reportType().getLabel()));
                int index = 1;
                for (ClientCategoryRowDto row : report.rows()) {
                    writer.println(
                            index++
                                    + ","
                                    + csv(row.clientName())
                                    + ","
                                    + report.reportType().rowValue(row));
                }
                writer.println(
                        ","
                                + csv("Grand Total")
                                + ","
                                + report.reportType().totalValue(report.totals()));
                return;
            }
            writer.println(String.join(",", revenueHeaders(category)));
            int index = 1;
            for (ClientCategoryRowDto row : report.rows()) {
                List<String> values = new ArrayList<>(revenueValues(category, row));
                values.addFirst(String.valueOf(index++));
                writer.println(String.join(",", values));
            }
        }
    }

    @GetMapping(CATEGORY_PATH + "/revenues/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printRevenuePdf(
            @PathVariable String categorySlug,
            @ModelAttribute("filter") ClientCategoryFilter filter) {
        ClientCategory category = ClientCategory.fromSlug(categorySlug);
        var report = clientCategoryService.revenueReport(category, filter);
        return pdf(category.getSlug() + "-report.pdf", pdfService.revenuePdf(report));
    }

    private void populateBaseModel(Model model, ClientCategory category) {
        model.addAttribute("category", category);
        model.addAttribute("financialYears", clientCategoryService.financialYears());
        model.addAttribute("paymentStatuses", clientCategoryService.paymentStatuses());
        model.addAttribute("reportTypes", clientCategoryService.reportTypes(category));
    }

    private List<String> listHeaders(ClientCategory category) {
        List<String> headers =
                new ArrayList<>(
                        List.of(
                                csv(category.getNameLabel()),
                                "Entry Date",
                                "Expire Date",
                                "Region",
                                "Annual Fee",
                                "Amount Paid"));
        if (category.isWithholdingTaxSupported()) {
            headers.add("W-H TAX");
        }
        if (category.isCommissionSupported()) {
            headers.add("Commission");
        }
        headers.addAll(
                List.of(
                        "Programmer Cost",
                        "System Install Fee",
                        "Training Fee",
                        "Android Fee",
                        "System Reinstall Fee"));
        if (category.isBulkSmsSupported()) {
            headers.add("Bulk SMS");
        }
        if (category.isHostingSupported()) {
            headers.add("Hosting Fee");
        }
        headers.addAll(List.of("Financial Year", "Payment Status"));
        return headers;
    }

    private List<String> listValues(ClientCategory category, ClientCategoryRowDto row) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                csv(row.clientName()),
                                String.valueOf(row.entryDate()),
                                String.valueOf(row.expireDate()),
                                csv(row.region()),
                                String.valueOf(row.annualFee()),
                                String.valueOf(row.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(String.valueOf(row.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(String.valueOf(row.commission()));
        }
        values.addAll(
                List.of(
                        String.valueOf(row.programmerCost()),
                        String.valueOf(row.systemInstallFee()),
                        String.valueOf(row.trainingFee()),
                        String.valueOf(row.androidFee()),
                        String.valueOf(row.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(String.valueOf(row.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(String.valueOf(row.hostingFee()));
        }
        values.addAll(List.of(csv(row.financialYear()), csv(row.paymentStatus())));
        return values;
    }

    private List<String> revenueHeaders(ClientCategory category) {
        List<String> headers =
                new ArrayList<>(
                        List.of(
                                "No",
                                csv(category.getNameLabel()),
                                "Annual Fee Invoiced",
                                "Annual Fee Paid"));
        if (category.isWithholdingTaxSupported()) {
            headers.add("W-H TAX");
        }
        if (category.isCommissionSupported()) {
            headers.add("Commission");
        }
        headers.addAll(
                List.of(
                        "Programmer Cost",
                        "System Install Fee",
                        "Training Fee",
                        "Android Fee",
                        "System Reinstall Fee"));
        if (category.isBulkSmsSupported()) {
            headers.add("Bulk SMS");
        }
        if (category.isHostingSupported()) {
            headers.add("Hosting Fee");
        }
        headers.addAll(List.of("Revenue Total", "Gross Profit"));
        return headers;
    }

    private List<String> revenueValues(ClientCategory category, ClientCategoryRowDto row) {
        List<String> values =
                new ArrayList<>(
                        List.of(
                                csv(row.clientName()),
                                String.valueOf(row.annualFee()),
                                String.valueOf(row.amount())));
        if (category.isWithholdingTaxSupported()) {
            values.add(String.valueOf(row.withholdingTax()));
        }
        if (category.isCommissionSupported()) {
            values.add(String.valueOf(row.commission()));
        }
        values.addAll(
                List.of(
                        String.valueOf(row.programmerCost()),
                        String.valueOf(row.systemInstallFee()),
                        String.valueOf(row.trainingFee()),
                        String.valueOf(row.androidFee()),
                        String.valueOf(row.systemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            values.add(String.valueOf(row.bulkSmsFee()));
        }
        if (category.isHostingSupported()) {
            values.add(String.valueOf(row.hostingFee()));
        }
        values.addAll(
                List.of(String.valueOf(row.revenueTotal()), String.valueOf(row.grossProfit())));
        return values;
    }

    private ResponseEntity<byte[]> pdf(String filename, byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }
}
