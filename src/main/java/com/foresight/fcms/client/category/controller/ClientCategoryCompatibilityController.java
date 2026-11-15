package com.foresight.fcms.client.category.controller;

import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.category.model.ClientCategoryReportType;
import com.foresight.fcms.reference.service.LegacyYearFilterService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ClientCategoryCompatibilityController {

    private final LegacyYearFilterService financialYears;

    public ClientCategoryCompatibilityController(LegacyYearFilterService financialYears) {
        this.financialYears = financialYears;
    }

    @GetMapping({"/union.php", "/user_union.php"})
    public String legacyUnion(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return redirect(ClientCategory.UNIONS);
    }

    @GetMapping({"/paidunion.php", "/user_paidunion.php"})
    public String legacyPaidUnion(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 1L);
        return redirect(ClientCategory.UNIONS);
    }

    @GetMapping({"/unpaidunion.php", "/user_unpaidunion.php"})
    public String legacyUnpaidUnion(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 2L);
        return redirect(ClientCategory.UNIONS);
    }

    @GetMapping({"/microfinance.php", "/user_microfinance.php"})
    public String legacyMicrofinance(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return redirect(ClientCategory.MICROFINANCE);
    }

    @GetMapping({"/paidmicrofinance.php", "/user_paidmicrofinance.php"})
    public String legacyPaidMicrofinance(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 1L);
        return redirect(ClientCategory.MICROFINANCE);
    }

    @GetMapping({"/unpaidmicrofinance.php", "/user_unpaidmicrofinance.php"})
    public String legacyUnpaidMicrofinance(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("paymentStatusId", 2L);
        return redirect(ClientCategory.MICROFINANCE);
    }

    @GetMapping({"/other_client.php", "/user_other_client.php"})
    public String legacyOthers(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes) {
        addYear(redirectAttributes, financialYearId, null);
        return redirect(ClientCategory.OTHERS);
    }

    @GetMapping({"/union_revenues.php", "/microfinance_report.php", "/other_report.php"})
    public String legacyReport(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromReportPath(request.getRequestURI());
        addYear(redirectAttributes, financialYearId, null);
        redirectAttributes.addAttribute("view", "revenue");
        return redirect(category);
    }

    @GetMapping({"/add_union.php", "/add_microfinance.php", "/add_other_client.php"})
    public String legacyAdd(jakarta.servlet.http.HttpServletRequest request) {
        return "redirect:" + categoryFromAddPath(request.getRequestURI()).getRoute() + "/new";
    }

    @GetMapping({"/update_union.php", "/update_microfinance.php", "/update_other_client.php"})
    public String legacyEdit(
            @RequestParam(name = "id", required = false) Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromEditPath(request.getRequestURI());
        return id == null
                ? redirect(category)
                : "redirect:" + category.getRoute() + "/" + id + "/edit";
    }

    @GetMapping({"/view_union.php", "/view_microfinance.php", "/view_other_client.php"})
    public String legacyView(
            @RequestParam(name = "id", required = false) Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromViewPath(request.getRequestURI());
        return id == null ? redirect(category) : "redirect:" + category.getRoute() + "/" + id;
    }

    @RequestMapping(
            value = {"/search_union.php", "/search_microfinance.php", "/search_other_client.php"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public String legacySearch(
            @RequestParam(name = "search", required = false) String search,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromSearchPath(request.getRequestURI());
        redirectAttributes.addAttribute("search", search == null ? "" : search.trim());
        return redirect(category);
    }

    @PostMapping({"/print_union.php", "/print_microfinance.php", "/print_otherclients.php"})
    public String legacyPrintList(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromPrintPath(request.getRequestURI());
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return "redirect:" + category.getRoute() + "/print.pdf";
    }

    @PostMapping({
        "/print_union_excel.php",
        "/print_microfinance_excel.php",
        "/print_otherclients_excel.php"
    })
    public String legacyExportList(
            @RequestParam(name = "fyear_id", required = false) Long financialYearId,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromPrintPath(request.getRequestURI());
        addYear(redirectAttributes, financialYearId, null);
        addStatus(redirectAttributes, status);
        return "redirect:" + category.getRoute() + "/export.csv";
    }

    @PostMapping({
        "/print_unionreport.php",
        "/print_microfinance_report.php",
        "/print_other_report.php"
    })
    public String legacyPrintReport(
            @RequestParam(name = "fyear", required = false) String fyear,
            @RequestParam(name = "report_type", required = false) String reportType,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromReportPath(request.getRequestURI());
        addYear(redirectAttributes, null, fyear);
        addStatus(redirectAttributes, status);
        addReportType(redirectAttributes, reportType, category);
        return "redirect:" + category.getRoute() + "/revenues/print.pdf";
    }

    @PostMapping({
        "/print_unionreport_excel.php",
        "/print_microfinance_report_excel.php",
        "/print_other_report_excel.php"
    })
    public String legacyExportReport(
            @RequestParam(name = "fyear", required = false) String fyear,
            @RequestParam(name = "report_type", required = false) String reportType,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {
        ClientCategory category = categoryFromReportPath(request.getRequestURI());
        addYear(redirectAttributes, null, fyear);
        addStatus(redirectAttributes, status);
        addReportType(redirectAttributes, reportType, category);
        return "redirect:" + category.getRoute() + "/revenues/export.csv";
    }

    private String redirect(ClientCategory category) {
        return "redirect:" + category.getRoute();
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

    private void addReportType(
            RedirectAttributes attributes, String reportType, ClientCategory category) {
        if (reportType != null && !reportType.isBlank()) {
            attributes.addAttribute(
                    "reportType", ClientCategoryReportType.from(reportType, category).getKey());
        }
    }

    private ClientCategory categoryFromReportPath(String path) {
        if (path.contains("microfinance")) {
            return ClientCategory.MICROFINANCE;
        }
        if (path.contains("other")) {
            return ClientCategory.OTHERS;
        }
        return ClientCategory.UNIONS;
    }

    private ClientCategory categoryFromAddPath(String path) {
        if (path.contains("microfinance")) {
            return ClientCategory.MICROFINANCE;
        }
        if (path.contains("other")) {
            return ClientCategory.OTHERS;
        }
        return ClientCategory.UNIONS;
    }

    private ClientCategory categoryFromEditPath(String path) {
        return categoryFromAddPath(path);
    }

    private ClientCategory categoryFromViewPath(String path) {
        return categoryFromAddPath(path);
    }

    private ClientCategory categoryFromSearchPath(String path) {
        return categoryFromAddPath(path);
    }

    private ClientCategory categoryFromPrintPath(String path) {
        if (path.contains("microfinance")) {
            return ClientCategory.MICROFINANCE;
        }
        if (path.contains("other")) {
            return ClientCategory.OTHERS;
        }
        return ClientCategory.UNIONS;
    }
}
