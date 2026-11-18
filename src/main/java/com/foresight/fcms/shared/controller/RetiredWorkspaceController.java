package com.foresight.fcms.shared.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Old bookmarks cannot expose retired forms, exports or mutation handlers. */
@Controller
public class RetiredWorkspaceController {
    @RequestMapping({
        "/accounting/books", "/accounting/books/**",
        "/accounting/periods", "/accounting/periods/**",
        "/accounting/equity-reporting", "/accounting/equity-reporting/**",
        "/finance/expenses", "/finance/expenses/**",
        "/finance/income", "/finance/income/**",
        "/finance/cloud-backup/billing", "/finance/cloud-backup/billing/**",
        "/finance/cloud-backup/billing/print.pdf",
        "/reports", "/reports/**",
        "/clients/{category:saccos|amcos|unions|microfinance|others}/revenues",
        "/clients/{category:saccos|amcos|unions|microfinance|others}/revenues/**",
        "/saccos_revenues.php", "/union_revenues.php", "/microfinance_report.php", "/other_report.php",
        "/print_saccosreport.php", "/print_saccosreport_excel.php",
        "/print_unionreport.php", "/print_unionreport_excel.php",
        "/print_microfinance_report.php", "/print_microfinance_report_excel.php",
        "/print_other_report.php", "/print_other_report_excel.php"
    })
    public ResponseEntity<Void> removed() {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).build();
    }
}
