package com.foresight.fcms.client.saccos.controller;

import com.foresight.fcms.client.saccos.model.SaccosReportType;
import com.foresight.fcms.reference.service.LegacyYearFilterService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SaccosCompatibilityController {

    private final LegacyYearFilterService financialYears;

    public SaccosCompatibilityController(LegacyYearFilterService financialYears) {
        this.financialYears = financialYears;
    }

    @GetMapping({"/saccos.php", "/user_saccos.php", "/user_all_saccos.php"})
    public String legacySaccos(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return "redirect:/clients/saccos";
    }

    @GetMapping({"/paidsaccos.php", "/user_paidsaccos.php"})
    public String legacyPaid(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 1L);
        return "redirect:/clients/saccos";
    }

    @GetMapping({"/unpaidsaccos.php", "/user_unpaidsaccos.php"})
    public String legacyUnpaid(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 2L);
        return "redirect:/clients/saccos";
    }

    @GetMapping({"/add_saccoss.php", "/user_add_saccos.php", "/add_contract.php"})
    public String legacyAdd() {
        return "redirect:/clients/saccos/new";
    }

    @GetMapping({"/view_saccos.php"})
    public String legacyView(@RequestParam(name = "id", required = false) Long id) {
        return id == null ? "redirect:/clients/saccos" : "redirect:/clients/saccos/" + id;
    }

    @GetMapping({
        "/update_saccos.php",
        "/user_update_saccos.php",
        "/update_contract.php",
        "/user_update_contract.php"
    })
    public String legacyEdit(@RequestParam(name = "id", required = false) Long id) {
        return id == null ? "redirect:/clients/saccos" : "redirect:/clients/saccos/" + id + "/edit";
    }

    @GetMapping("/saccos_revenues.php")
    public String legacyReports(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("view", "revenue");
        return "redirect:/clients/saccos";
    }

    @RequestMapping(
            value = {"/search_saccos.php", "/user_search_saccos.php"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public String legacySearch(
            @RequestParam(name = "search", required = false) String search,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("search", search == null ? "" : search.trim());
        return "redirect:/clients/saccos";
    }

    @PostMapping("/print_saccos.php")
    public String legacyPrintSaccos(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return "redirect:/clients/saccos/print.pdf";
    }

    @PostMapping("/print_saccos_excel.php")
    public String legacyExportSaccos(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return "redirect:/clients/saccos/export.csv";
    }

    @PostMapping("/print_paidsaccos.php")
    public String legacyPrintPaid(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        return "redirect:/clients/saccos/paid/print.pdf";
    }

    @PostMapping("/print_unpaidsaccos.php")
    public String legacyPrintUnpaid(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        return "redirect:/clients/saccos/unpaid/print.pdf";
    }

    @PostMapping("/print_saccosreport.php")
    public String legacyPrintReport(
            @RequestParam(name = "fyear", required = false) String fyear,
            @RequestParam(name = "report_type", required = false) String reportType,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, null, fyear);
        addStatus(redirectAttributes, status);
        addReportType(redirectAttributes, reportType);
        return "redirect:/clients/saccos/revenues/print.pdf";
    }

    @PostMapping("/print_saccosreport_excel.php")
    public String legacyExportReport(
            @RequestParam(name = "fyear", required = false) String fyear,
            @RequestParam(name = "report_type", required = false) String reportType,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, null, fyear);
        addStatus(redirectAttributes, status);
        addReportType(redirectAttributes, reportType);
        return "redirect:/clients/saccos/revenues/export.csv";
    }

    private void addYear(RedirectAttributes attributes, Long financialYearId, String fyear) {
        if (financialYearId != null) {
            attributes.addAttribute("financialYearId", financialYearId);
            return;
        }
        if (fyear != null && !fyear.isBlank()) {
            financialYears
                    .resolve(fyear.trim())
                    .ifPresent(year -> attributes.addAttribute("financialYearId", year));
        }
    }

    private void addStatus(RedirectAttributes attributes, String status) {
        Long paymentStatusId =
                switch (status == null ? "" : status.trim().toLowerCase()) {
                    case "paid" -> 1L;
                    case "unpaid" -> 2L;
                    case "pending" -> 3L;
                    default -> null;
                };
        if (paymentStatusId != null) {
            attributes.addAttribute("paymentStatusId", paymentStatusId);
        }
    }

    private void addReportType(RedirectAttributes attributes, String reportType) {
        if (reportType != null && !reportType.isBlank()) {
            attributes.addAttribute("reportType", SaccosReportType.from(reportType).getKey());
        }
    }
}
