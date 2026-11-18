package com.foresight.fcms.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public String index() {
        return "redirect:" + dashboard.home();
    }

    @GetMapping("/admin/dashboard")
    public String administration() {
        return "redirect:" + dashboard.administratorHome();
    }

    @GetMapping("/accounting/dashboard")
    public String accountant(
            @RequestParam(required = false) Long financialYearId,
            @RequestParam(defaultValue = "saccos") String category,
            Model model) {
        model.addAttribute("overview", dashboard.accountantOverview(financialYearId, category));
        model.addAttribute("dashboardCategory", category);
        return "dashboard/index";
    }

    @GetMapping("/director/dashboard")
    public String director(
            @RequestParam(required = false) Long financialYearId,
            @RequestParam(defaultValue = "saccos") String category,
            Model model) {
        model.addAttribute("overview", dashboard.directorOverview(financialYearId, category));
        model.addAttribute("dashboardCategory", category);
        return "dashboard/index";
    }

    @GetMapping("/staff/dashboard")
    public String staff(Model model) {
        model.addAttribute("overview", dashboard.staffOverview());
        return "dashboard/index";
    }
}
