package com.foresight.fcms.report;

import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.report.service.LedgerReportPdfService;
import com.foresight.fcms.report.service.PdfReportService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.shared.view.ApplicationTimestamps;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class OperationalReportController {
    private final PdfReportService pdf;
    private final LedgerReportPdfService ledgerPdf;
    private final LedgerService ledgers;
    private final ExpenseService expenses;
    private final WorkService work;

    public OperationalReportController(
            PdfReportService pdf,
            LedgerReportPdfService ledgerPdf,
            LedgerService ledgers,
            ExpenseService expenses,
            WorkService work) {
        this.pdf = pdf;
        this.ledgerPdf = ledgerPdf;
        this.ledgers = ledgers;
        this.expenses = expenses;
        this.work = work;
    }

    @GetMapping("/finance/{ledger:income|petty-cash|cloud-backup|bulk-sms}/print.pdf")
    public ResponseEntity<byte[]> ledger(
            @PathVariable String ledger, @ModelAttribute RegisterQuery query, Locale locale) {
        return output(ledger, ledgerPdf.render(LedgerKind.from(ledger), query, locale));
    }

    @GetMapping("/finance/expenses/print.pdf")
    public ResponseEntity<byte[]> expenses(@ModelAttribute RegisterQuery query) {
        var rows =
                expenses.report(query).stream()
                        .map(
                                r ->
                                        List.of(
                                                ApplicationTimestamps.date(r.date()),
                                                r.owner(),
                                                r.category(),
                                                r.description(),
                                                r.amount().toPlainString(),
                                                r.status()))
                        .toList();
        return output(
                "expenses",
                pdf.table(
                        "Expenses",
                        List.of(
                                "Date",
                                "Staff",
                                "Category",
                                "Description",
                                "Amount (TZS)",
                                "Status"),
                        rows));
    }

    @GetMapping("/{workspace:tasks|leaves|staff-reports}/print.pdf")
    public ResponseEntity<byte[]> work(
            @PathVariable String workspace, @ModelAttribute RegisterQuery query) {
        var kind = WorkKind.from(workspace);
        var rows =
                work.report(kind, query).stream()
                        .map(
                                r ->
                                        List.of(
                                                r.owner(),
                                                r.client(),
                                                r.subject(),
                                                ApplicationTimestamps.display(r.startAt()),
                                                ApplicationTimestamps.display(r.endAt()),
                                                r.status()))
                        .toList();
        return output(
                workspace,
                pdf.table(
                        kind.name(),
                        List.of("Staff", "Client", "Subject", "From", "To", "Status"),
                        rows));
    }

    @GetMapping("/finance/{ledger:income|petty-cash|cloud-backup|bulk-sms}/{id}/print.pdf")
    public ResponseEntity<byte[]> receipt(@PathVariable String ledger, @PathVariable long id) {
        var r = ledgers.find(LedgerKind.from(ledger), id);
        var rows = new java.util.ArrayList<java.util.List<String>>();
        rows.add(java.util.List.of("Date", ApplicationTimestamps.date(r.date())));
        rows.add(java.util.List.of("Reference", r.reference()));
        rows.add(java.util.List.of("Description", r.description()));
        rows.add(java.util.List.of("Amount (TZS)", r.amount().toPlainString()));
        rows.add(java.util.List.of("Payment mode", r.paymentMode()));
        rows.add(java.util.List.of("SMS units", Long.toString(r.units())));
        rows.add(java.util.List.of("Recorded by", r.actor()));
        if (r.reversalOf() != null)
            rows.add(java.util.List.of("Reversal of", r.reversalOf().toString()));
        if (r.reversedBy() != null)
            rows.add(java.util.List.of("Reversed by", r.reversedBy().toString()));
        return output(
                ledger + "-" + id,
                pdf.table(r.reference(), java.util.List.of("Field", "Value"), rows));
    }

    private ResponseEntity<byte[]> output(String name, byte[] body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + name + ".pdf")
                .body(body);
    }
}
