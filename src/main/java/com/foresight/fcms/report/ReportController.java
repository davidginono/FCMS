package com.foresight.fcms.report;

import com.foresight.fcms.finance.report.service.FinancialReportService;
import com.foresight.fcms.report.service.ConsolidatedStatementPdfService;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Controller
@RequestMapping("/reports")
public class ReportController {
    private final FinancialReportService finance;
    private final ConsolidatedStatementPdfService pdf;

    public ReportController(FinancialReportService finance, ConsolidatedStatementPdfService pdf) {
        this.finance = finance;
        this.pdf = pdf;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Integer year, Model m, Locale locale) {
        var statement =
                finance.statement(year == null ? java.time.LocalDate.now().getYear() : year);
        m.addAttribute("statement", statement);
        m.addAttribute("statementDocument", pdf.document(statement, locale));
        m.addAttribute("reportType", "income-statement");
        m.addAttribute("reportTitle", "report.statement");
        return "reports/statement";
    }

    @GetMapping("/print")
    public String print(@RequestParam int year, Model m, Locale locale) {
        index(year, m, locale);
        return "accounting/statement-print";
    }

    @GetMapping("/statement.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam int year, Locale locale) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=statement-" + year + ".pdf")
                .body(pdf.render(finance.statement(year), locale));
    }
}
