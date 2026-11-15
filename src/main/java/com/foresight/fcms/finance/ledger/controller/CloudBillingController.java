package com.foresight.fcms.finance.ledger.controller;

import com.foresight.fcms.client.service.CloudBillingService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class CloudBillingController {
    private final CloudBillingService billing;
    private final com.foresight.fcms.report.service.OperationalReportPdfService pdf;

    public CloudBillingController(
            CloudBillingService billing,
            com.foresight.fcms.report.service.OperationalReportPdfService pdf) {
        this.billing = billing;
        this.pdf = pdf;
    }

    @GetMapping("/finance/cloud-backup/billing/print.pdf")
    public org.springframework.http.ResponseEntity<byte[]> print(
            @RequestParam(required = false) Integer year,
            @ModelAttribute RegisterQuery query,
            java.util.Locale locale) {
        int selectedYear =
                year == null
                        ? com.foresight.fcms.shared.time.ApplicationCalendar.today().getYear()
                        : year;
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=cloud-billing.pdf")
                .body(
                        pdf.billing(
                                selectedYear, billing.report(selectedYear, query), query, locale));
    }

    @GetMapping("/finance/cloud-backup/billing")
    public String billing(
            @RequestParam(required = false) Integer year,
            @ModelAttribute("query") RegisterQuery q,
            Model m) {
        int y =
                year == null
                        ? com.foresight.fcms.shared.time.ApplicationCalendar.today().getYear()
                        : year;
        m.addAttribute("records", billing.list(y, q));
        m.addAttribute("year", y);
        m.addAttribute("baseUrl", "/finance/cloud-backup/billing");
        m.addAttribute("resourceKey", "cloud-billing");
        return "ledger/billing";
    }
}
