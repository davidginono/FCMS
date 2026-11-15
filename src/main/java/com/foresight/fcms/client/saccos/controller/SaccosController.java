package com.foresight.fcms.client.saccos.controller;

import static com.foresight.fcms.shared.view.CsvText.quote;

import com.foresight.fcms.client.saccos.dto.SaccosFilter;
import com.foresight.fcms.client.saccos.dto.SaccosForm;
import com.foresight.fcms.client.saccos.dto.SaccosRowDto;
import com.foresight.fcms.client.saccos.model.SaccosReportType;
import com.foresight.fcms.client.saccos.service.SaccosPdfService;
import com.foresight.fcms.client.saccos.service.SaccosService;

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

@Controller
@RequestMapping("/clients")
public class SaccosController {

    private final SaccosService saccosService;
    private final SaccosPdfService pdfService;

    public SaccosController(SaccosService saccosService, SaccosPdfService pdfService) {
        this.saccosService = saccosService;
        this.pdfService = pdfService;
    }

    @GetMapping("/saccos")
    public String saccos(@ModelAttribute("filter") SaccosFilter filter, Model model) {
        if (filter.isRevenueView()) {
            return renderSaccosRevenue(filter, model);
        }
        return renderSaccosList(filter, model);
    }

    private String renderSaccosList(SaccosFilter filter, Model model) {
        var result =
                saccosService.findSaccos(
                        filter,
                        PageRequest.of(
                                filter.getPage(),
                                filter.getSize(),
                                Sort.by(
                                                org.springframework.data.domain.Sort.Direction
                                                        .fromString(filter.getDirection()),
                                                java.util.Map.of(
                                                                "name",
                                                                "saccosName",
                                                                "region",
                                                                "region",
                                                                "entryDate",
                                                                "entryDate",
                                                                "expireDate",
                                                                "expireDate",
                                                                "annualFee",
                                                                "annualFee",
                                                                "amount",
                                                                "amount",
                                                                "financialYear",
                                                                "financialYear.fyear",
                                                                "paymentStatus",
                                                                "status.name")
                                                        .getOrDefault(
                                                                filter.getSort(), "saccosName"))
                                        .and(Sort.by("id"))));
        populateOptions(model);
        model.addAttribute("clients", result.page().getContent());
        model.addAttribute("rows", result.page().getContent());
        model.addAttribute(
                "category", java.util.Map.of("route", "/clients/saccos", "title", "SACCOS"));
        model.addAttribute("page", result.page());
        model.addAttribute("totals", result.totals());
        model.addAttribute("reportType", SaccosReportType.FULL);
        return "clients/saccos";
    }

    private String renderSaccosRevenue(SaccosFilter filter, Model model) {
        var result =
                saccosService.findSaccos(
                        filter,
                        PageRequest.of(
                                filter.getPage(),
                                filter.getSize(),
                                Sort.by("saccosName").and(Sort.by("id"))));
        populateOptions(model);
        model.addAttribute("rows", result.page().getContent());
        model.addAttribute("page", result.page());
        model.addAttribute("totals", result.totals());
        model.addAttribute(
                "category", java.util.Map.of("route", "/clients/saccos", "title", "SACCOS"));
        model.addAttribute("reportType", SaccosReportType.from(filter.getReportType()));
        return "clients/saccos-revenue";
    }

    @GetMapping("/saccos/paid")
    public String paidSaccos(
            @ModelAttribute("filter") SaccosFilter filter, RedirectAttributes redirectAttributes) {
        addFilterRedirectAttributes(filter, redirectAttributes);
        redirectAttributes.addAttribute("paymentStatusId", 1L);
        return "redirect:/clients/saccos";
    }

    @GetMapping("/saccos/unpaid")
    public String unpaidSaccos(
            @ModelAttribute("filter") SaccosFilter filter, RedirectAttributes redirectAttributes) {
        addFilterRedirectAttributes(filter, redirectAttributes);
        redirectAttributes.addAttribute("paymentStatusId", 2L);
        return "redirect:/clients/saccos";
    }

    @GetMapping("/saccos/revenues")
    public String revenueReport(
            @RequestParam(name = "financialYearId", required = false) Long financialYearId,
            @RequestParam(name = "paymentStatusId", required = false) Long paymentStatusId,
            @RequestParam(name = "reportType", required = false) String reportType,
            @RequestParam(name = "search", required = false) String search,
            RedirectAttributes redirectAttributes) {
        if (financialYearId != null) {
            redirectAttributes.addAttribute("financialYearId", financialYearId);
        }
        if (paymentStatusId != null) {
            redirectAttributes.addAttribute("paymentStatusId", paymentStatusId);
        }
        if (reportType != null && !reportType.isBlank()) {
            redirectAttributes.addAttribute(
                    "reportType", SaccosReportType.from(reportType).getKey());
        }
        if (search != null && !search.isBlank()) {
            redirectAttributes.addAttribute("search", search.trim());
        }
        redirectAttributes.addAttribute("view", "revenue");
        return "redirect:/clients/saccos";
    }

    @GetMapping("/saccos/new")
    public String newSaccos(Model model) {
        populateOptions(model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", saccosService.newForm());
        }
        model.addAttribute("formAction", "/clients/saccos");
        model.addAttribute("formTitle", "Add SACCOS");
        return "clients/saccos-form";
    }

    @PostMapping("/saccos")
    public String createSaccos(
            @Valid @ModelAttribute("form") SaccosForm form,
            BindingResult bindingResult,
            @RequestParam(name = "contractUpload", required = false) MultipartFile contractUpload,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = saccosService.createSaccos(form, contractUpload);
                return "redirect:" + "/clients/saccos" + "/" + id + "?saved";
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
        populateOptions(model);
        model.addAttribute("formAction", "/clients/saccos");
        return "clients/saccos-form";
    }

    @GetMapping("/saccos/{id}")
    public String viewSaccos(@PathVariable Long id, Model model) {
        model.addAttribute("saccos", saccosService.findSaccos(id));
        return "clients/saccos-detail";
    }

    @GetMapping("/saccos/{id}/edit")
    public String editSaccos(@PathVariable Long id, Model model) {
        populateOptions(model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", saccosService.formFor(id));
        }
        model.addAttribute("formAction", "/clients/saccos/" + id);
        model.addAttribute("formTitle", "Update SACCOS");
        model.addAttribute("saccosId", id);
        return "clients/saccos-form";
    }

    @PostMapping("/saccos/{id}")
    public String updateSaccos(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") SaccosForm form,
            BindingResult bindingResult,
            @RequestParam(name = "contractUpload", required = false) MultipartFile contractUpload,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                saccosService.updateSaccos(id, form, contractUpload);
                return "redirect:" + "/clients/saccos" + "/" + id + "?saved";
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
        populateOptions(model);
        model.addAttribute("formAction", "/clients/saccos" + "/" + id);
        model.addAttribute("saccosId", id);
        return "clients/saccos-form";
    }

    @PostMapping("/saccos/{id}/delete")
    public String deleteSaccos(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return "redirect:/clients/saccos/" + id + "/archive";
    }

    @GetMapping("/saccos/{id}/contract")
    public ResponseEntity<Resource> contract(@PathVariable Long id) {
        Resource contract = saccosService.contract(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract.pdf\"")
                .body(contract);
    }

    @GetMapping("/saccos/export.csv")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public void exportCsv(
            @ModelAttribute("filter") SaccosFilter filter, HttpServletResponse response)
            throws IOException {
        var rows = saccosService.exportSaccos(filter);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"saccos.csv\"");
        try (PrintWriter writer = response.getWriter()) {
            writer.println(
                    "Saccos Name,Entry Date,Expire Date,Region,Annual Fee,Amount Paid,Date"
                            + " Paid,W-TAX,Commission,Programmer Cost,System Install Fee,Training"
                            + " Fee,Android Fee,Android Cost,System Reinstall Fee,Bulk SMS,Hosting"
                            + " Fee,ESS,Cloud Backup Fee,Cloud Backup Cost,Financial Year,Payment"
                            + " Status,Status,Invoice");
            for (SaccosRowDto row : rows) {
                writer.println(
                        quote(row.saccosName())
                                + ","
                                + row.entryDate()
                                + ","
                                + row.expireDate()
                                + ","
                                + quote(row.region())
                                + ","
                                + row.annualFee()
                                + ","
                                + row.amount()
                                + ","
                                + row.datePaid()
                                + ","
                                + row.withholdingTax()
                                + ","
                                + row.commission()
                                + ","
                                + row.programmerCost()
                                + ","
                                + row.systemInstallFee()
                                + ","
                                + row.trainingFee()
                                + ","
                                + row.androidFee()
                                + ","
                                + row.androidCost()
                                + ","
                                + row.systemReinstallFee()
                                + ","
                                + row.bulkSmsFee()
                                + ","
                                + row.hostingFee()
                                + ","
                                + row.essFee()
                                + ","
                                + row.cloudBackupFee()
                                + ","
                                + row.cloudBackupCost()
                                + ","
                                + quote(row.financialYear())
                                + ","
                                + quote(row.paymentStatus())
                                + ","
                                + quote(row.operationalStatus())
                                + ","
                                + quote(row.invoiceStatus()));
            }
        }
    }

    @GetMapping("/saccos/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printSaccosPdf(@ModelAttribute("filter") SaccosFilter filter) {
        var export = saccosService.exportSaccosWithTotals(filter);
        byte[] pdf = pdfService.listPdf("SACCOS Register", export.rows(), export.totals());
        return pdf("saccos.pdf", pdf);
    }

    @GetMapping("/saccos/paid/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printPaidSaccosPdf(
            @ModelAttribute("filter") SaccosFilter filter) {
        filter.setPaymentStatusId(1L);
        var export = saccosService.exportSaccosWithTotals(filter);
        byte[] pdf =
                pdfService.paymentSummaryPdf(
                        "Paid SACCOS - " + export.selectedFinancialYearLabel(),
                        export.rows(),
                        export.totals(),
                        true);
        return pdf("paid-saccos.pdf", pdf);
    }

    @GetMapping("/saccos/unpaid/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printUnpaidSaccosPdf(
            @ModelAttribute("filter") SaccosFilter filter) {
        filter.setPaymentStatusId(2L);
        var export = saccosService.exportSaccosWithTotals(filter);
        byte[] pdf =
                pdfService.paymentSummaryPdf(
                        "Unpaid SACCOS", export.rows(), export.totals(), false);
        return pdf("unpaid-saccos.pdf", pdf);
    }

    @GetMapping("/saccos/revenues/export.csv")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public void exportRevenueCsv(
            @RequestParam(name = "financialYearId", required = false) Long financialYearId,
            @RequestParam(name = "paymentStatusId", required = false) Long paymentStatusId,
            @RequestParam(name = "reportType", required = false) String reportType,
            @RequestParam(name = "search", required = false) String search,
            HttpServletResponse response)
            throws IOException {
        SaccosService.SaccosReportResult report =
                saccosService.revenueReport(financialYearId, paymentStatusId, reportType, search);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"saccos-report.csv\"");
        try (PrintWriter writer = response.getWriter()) {
            if (!report.reportType().isFull()) {
                writer.println("No,Saccos Name," + quote(report.reportType().getLabel()));
                int index = 1;
                for (SaccosRowDto row : report.rows()) {
                    writer.println(
                            index++
                                    + ","
                                    + quote(row.saccosName())
                                    + ","
                                    + report.reportType().rowValue(row));
                }
                writer.println(
                        ","
                                + quote("Grand Total")
                                + ","
                                + report.reportType().totalValue(report.totals()));
                return;
            }
            writer.println(
                    "No,Saccos Name,Annual Fee Invoiced,Annual Fee Paid,W-H"
                        + " TAX,Commission,Programmer Cost,System Install Fee,Training Fee,Android"
                        + " Fee,Android Cost,System Reinstall Fee,Bulk SMS,Hosting Fee,Cloud Backup"
                        + " Fee,Cloud Backup Cost,Revenue Total,Gross Profit");
            int index = 1;
            for (SaccosRowDto row : report.rows()) {
                writer.println(
                        index++
                                + ","
                                + quote(row.saccosName())
                                + ","
                                + row.annualFee()
                                + ","
                                + row.amount()
                                + ","
                                + row.withholdingTax()
                                + ","
                                + row.commission()
                                + ","
                                + row.programmerCost()
                                + ","
                                + row.systemInstallFee()
                                + ","
                                + row.trainingFee()
                                + ","
                                + row.androidFee()
                                + ","
                                + row.androidCost()
                                + ","
                                + row.systemReinstallFee()
                                + ","
                                + row.bulkSmsFee()
                                + ","
                                + row.hostingFee()
                                + ","
                                + row.cloudBackupFee()
                                + ","
                                + row.cloudBackupCost()
                                + ","
                                + row.revenueTotal()
                                + ","
                                + row.grossProfit());
            }
        }
    }

    @GetMapping("/saccos/revenues/print.pdf")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('client.export')")
    public ResponseEntity<byte[]> printRevenuePdf(
            @RequestParam(name = "financialYearId", required = false) Long financialYearId,
            @RequestParam(name = "paymentStatusId", required = false) Long paymentStatusId,
            @RequestParam(name = "reportType", required = false) String reportType,
            @RequestParam(name = "search", required = false) String search) {
        SaccosService.SaccosReportResult report =
                saccosService.revenueReport(financialYearId, paymentStatusId, reportType, search);
        byte[] pdf = pdfService.revenuePdf(report);
        return pdf("saccos-report.pdf", pdf);
    }

    @PostMapping("/saccos/search")
    public String searchSaccos(
            @RequestParam(name = "search", required = false) String search,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("search", search == null ? "" : search.trim());
        return "redirect:/clients/saccos";
    }

    private void populateOptions(Model model) {
        model.addAttribute("financialYears", saccosService.financialYears());
        model.addAttribute("paymentStatuses", saccosService.paymentStatuses());
        model.addAttribute("reportTypes", saccosService.reportTypes());
    }

    private void addFilterRedirectAttributes(
            SaccosFilter filter, RedirectAttributes redirectAttributes) {
        if (filter.getFinancialYearId() != null) {
            redirectAttributes.addAttribute("financialYearId", filter.getFinancialYearId());
        }
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            redirectAttributes.addAttribute("search", filter.getSearch().trim());
        }
    }

    private ResponseEntity<byte[]> pdf(String filename, byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
