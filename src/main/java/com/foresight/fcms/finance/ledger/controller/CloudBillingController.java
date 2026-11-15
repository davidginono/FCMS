package com.foresight.fcms.finance.ledger.controller;

import com.foresight.fcms.client.service.CloudBillingService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class CloudBillingController {
    private final CloudBillingService billing;

    public CloudBillingController(CloudBillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/finance/cloud-backup/billing")
    public String billing(
            @RequestParam(required = false) Integer year,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        m.addAttribute("records", billing.list(y, q));
        m.addAttribute("year", y);
        m.addAttribute("baseUrl", "/finance/cloud-backup/billing");
        m.addAttribute("resourceKey", "cloud-billing");
        return "ledger/billing";
    }
}
