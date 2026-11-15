package com.foresight.fcms.report;

import com.foresight.fcms.finance.expense.dto.ExpenseCategoryQuery;
import com.foresight.fcms.finance.expense.dto.ExpenseQuery;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.report.service.LedgerReportPdfService;
import com.foresight.fcms.report.service.OperationalReportPdfService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.model.WorkKind;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Controller
public class OperationalReportController {
    private final OperationalReportPdfService pdf;
    private final LedgerReportPdfService ledgerPdf;

    public OperationalReportController(
            OperationalReportPdfService pdf, LedgerReportPdfService ledgerPdf) {
        this.pdf = pdf;
        this.ledgerPdf = ledgerPdf;
    }

    @GetMapping("/finance/{ledger:income|petty-cash|cloud-backup|bulk-sms}/print.pdf")
    public ResponseEntity<byte[]> ledger(
            @PathVariable String ledger, @ModelAttribute RegisterQuery query, Locale locale) {
        return output(ledger, ledgerPdf.render(LedgerKind.from(ledger), query, locale));
    }

    @GetMapping("/finance/expenses/print.pdf")
    public ResponseEntity<byte[]> expenses(@ModelAttribute ExpenseQuery query, Locale locale) {
        return output("expenses", pdf.expenses(query, locale));
    }

    @GetMapping("/finance/expenses/categories/print.pdf")
    public ResponseEntity<byte[]> expenseCategories(
            @ModelAttribute ExpenseCategoryQuery query, Locale locale) {
        return output("expense-categories", pdf.expenseCategories(query, locale));
    }

    @GetMapping("/{workspace:tasks|leaves|staff-reports}/print.pdf")
    public ResponseEntity<byte[]> work(
            @PathVariable String workspace, @ModelAttribute RegisterQuery query, Locale locale) {
        return output(workspace, pdf.work(WorkKind.from(workspace), query, locale));
    }

    @GetMapping("/finance/{ledger:income|petty-cash|cloud-backup|bulk-sms}/{id}/print.pdf")
    public ResponseEntity<byte[]> receipt(
            @PathVariable String ledger, @PathVariable long id, Locale locale) {
        return output(ledger + "-" + id, pdf.receipt(LedgerKind.from(ledger), id, locale));
    }

    private ResponseEntity<byte[]> output(String name, byte[] body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + name + ".pdf")
                .body(body);
    }
}
