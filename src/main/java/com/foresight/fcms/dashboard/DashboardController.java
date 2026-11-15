package com.foresight.fcms.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String index(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    Long financialYearId,
            Model model) {
        model.addAttribute("overview", dashboardService.overview(financialYearId));
        return "dashboard/index";
    }
}
