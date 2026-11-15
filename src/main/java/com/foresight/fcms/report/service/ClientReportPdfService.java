package com.foresight.fcms.report.service;

import com.foresight.fcms.report.dto.OperationalDocument;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Shared legacy client stationery and localization, without client persistence or permission logic.
 */
@Service
public class ClientReportPdfService {
    private final MessageSource messages;
    private final ReportPdfRenderer renderer;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ClientReportPdfService(MessageSource messages, ReportPdfRenderer renderer) {
        this(messages, renderer, java.time.Clock.systemUTC());
    }

    ClientReportPdfService(
            MessageSource messages, ReportPdfRenderer renderer, java.time.Clock clock) {
        this.messages = messages;
        this.renderer = renderer;
        this.clock = clock;
    }

    private static final Map<String, String> KEYS =
            Map.ofEntries(
                    Map.entry("No", "legacyReport.no"),
                    Map.entry("Year", "report.year"),
                    Map.entry("Entry Date", "client.entryDate"),
                    Map.entry("Expire Date", "client.expireDate"),
                    Map.entry("Region", "client.region"),
                    Map.entry("Contract", "client.contract"),
                    Map.entry("Annual Fee", "client.annualFee"),
                    Map.entry("Annual Invoiced", "client.annualFee"),
                    Map.entry("Amount Paid", "client.amountPaid"),
                    Map.entry("Annual Paid", "client.amountPaid"),
                    Map.entry("Annual Fee (Amount To Be Paid)", "client.annualFee"),
                    Map.entry("Date Paid", "client.datePaid"),
                    Map.entry("W-H TAX", "legacyReport.withholdingTax"),
                    Map.entry("W-TAX", "legacyReport.withholdingTax"),
                    Map.entry("Commission", "client.commission"),
                    Map.entry("Programmer Cost", "client.programmerCost"),
                    Map.entry("Install Fee", "client.systemInstallFee"),
                    Map.entry("Training", "client.trainingFee"),
                    Map.entry("Training Fee", "client.trainingFee"),
                    Map.entry("Android", "client.androidFee"),
                    Map.entry("Android Fee", "client.androidFee"),
                    Map.entry("Android Cost", "client.androidCost"),
                    Map.entry("Reinstall", "client.systemReinstallFee"),
                    Map.entry("Reinstall Fee", "client.systemReinstallFee"),
                    Map.entry("Bulk SMS", "client.bulkSmsFee"),
                    Map.entry("Hosting", "client.hostingFee"),
                    Map.entry("Hosting Fee", "client.hostingFee"),
                    Map.entry("Cloud Backup", "client.cloudBackupFee"),
                    Map.entry("Cloud Cost", "client.cloudBackupCost"),
                    Map.entry("Revenue", "client.revenueTotal"),
                    Map.entry("Gross Profit", "client.grossProfit"),
                    Map.entry("Payment", "common.status"),
                    Map.entry("Paid", "status.PAID"),
                    Map.entry("Unpaid", "status.UNPAID"),
                    Map.entry("PAID", "status.PAID"),
                    Map.entry("UNPAID", "status.UNPAID"),
                    Map.entry("PENDING", "status.PENDING"),
                    Map.entry("Yes", "common.yes"),
                    Map.entry("No contract", "client.noContract"),
                    Map.entry("All", "common.all"),
                    Map.entry("All Years", "common.all"),
                    Map.entry("All Payment Statuses", "common.all"),
                    Map.entry("SACCOS Name", "common.name"),
                    Map.entry("AMCOS Name", "common.name"),
                    Map.entry("Union Name", "common.name"),
                    Map.entry("Microfinance Name", "common.name"),
                    Map.entry("Client Name", "common.name"),
                    Map.entry("System Install Fee", "client.systemInstallFee"),
                    Map.entry("System Reinstall Fee", "client.systemReinstallFee"),
                    Map.entry("Grand Total", "common.total"),
                    Map.entry("Total", "common.total"));

    public String label(String value) {
        String key = KEYS.get(value);
        return messages.getMessage(
                key == null ? value : key, null, value, LocaleContextHolder.getLocale());
    }

    public String title(String value) {
        // Titles are application-generated metadata; row descriptions/names never pass through this
        // mapping.
        String[] parts = value.split(" - ", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.endsWith(" Register"))
                part =
                        part.substring(0, part.length() - 9)
                                + " "
                                + messages.getMessage(
                                        "client.registerTitle",
                                        null,
                                        LocaleContextHolder.getLocale());
            else if (part.endsWith(" Report"))
                part =
                        part.substring(0, part.length() - 7)
                                + " "
                                + messages.getMessage(
                                        "client.reportTitle",
                                        null,
                                        LocaleContextHolder.getLocale());
            else if (part.equals("Paid SACCOS")) part = label("client.paidTitle");
            else if (part.equals("Unpaid SACCOS")) part = label("client.unpaidTitle");
            for (var category :
                    Map.of(
                                    "OTHERS",
                                    "other_clients",
                                    "MICROFINANCE",
                                    "microfinance_clients",
                                    "UNION",
                                    "unions",
                                    "AMCOS",
                                    "amcos")
                            .entrySet()) {
                if (part.startsWith(category.getKey() + " ")) {
                    part =
                            messages.getMessage(
                                            "client.category." + category.getValue(),
                                            null,
                                            LocaleContextHolder.getLocale())
                                    + part.substring(category.getKey().length());
                    break;
                }
            }
            parts[i] = label(part);
        }
        return String.join(" - ", parts);
    }

    private String reportPeriod(String title) {
        var match = java.util.regex.Pattern.compile("(?:^| - )(\\d{4})(?: - |$)").matcher(title);
        if (!match.find()) return label("All Years");
        String year = match.group(1);
        return label("legacyReport.range") + ": 01/01/" + year + " - 31/12/" + year;
    }

    public byte[] render(
            String kind,
            String title,
            List<String> headers,
            List<List<String>> rows,
            List<String> totals,
            List<Float> widths) {
        boolean plainRegister =
                kind.equals("CLIENT_SACCOS")
                        || kind.equals("CLIENT_MICROFINANCE_REGISTER")
                        || kind.equals("CLIENT_OTHERS_REGISTER");
        boolean pageRegister =
                kind.equals("CLIENT_AMCOS_REGISTER") || kind.equals("CLIENT_UNIONS_REGISTER");
        boolean revenue = kind.endsWith("REVENUE") || kind.equals("CLIENT_SINGLE");
        String printed =
                kind.equals("CLIENT_PAYMENT")
                        ? "   |   "
                                + label("legacyReport.printedShort")
                                + ": "
                                + java.time.format.DateTimeFormatter.ofPattern(
                                                "dd MMM uuuu HH:mm:ss", Locale.ENGLISH)
                                        .withZone(java.time.ZoneId.of("Africa/Dar_es_Salaam"))
                                        .format(clock.instant())
                        : "";
        String emptyMessage =
                switch (kind) {
                    case "CLIENT_SACCOS", "CLIENT_UNIONS_REGISTER" ->
                            label("legacyReport.noYearRecords");
                    case "CLIENT_OTHERS_REGISTER" -> label("common.noRecords");
                    default -> "";
                };
        return renderer.table(
                new OperationalDocument(
                        kind,
                        title(title),
                        (kind.endsWith("REVENUE") || kind.equals("CLIENT_SINGLE"))
                                ? reportPeriod(title)
                                : "",
                        label("legacyReport.page"),
                        headers.stream().map(this::label).toList(),
                        widths,
                        java.util.stream.IntStream.range(0, headers.size())
                                .mapToObj(i ->
                                        i == 1
                                                        || (plainRegister
                                                                && !kind.equals("CLIENT_SACCOS")
                                                                && i == 3)
                                                ? "L"
                                                : "C")
                                .toList(),
                        rows,
                        kind.equals("CLIENT_SACCOS") && rows.isEmpty()
                                ? List.of()
                                : totals.stream().map(this::label).toList(),
                        List.of(),
                        List.of(),
                        new OperationalDocument.Footer(
                                !pageRegister,
                                plainRegister || revenue
                                        ? 0
                                        : kind.equals("CLIENT_PAYMENT") ? 9 : 8,
                                printed),
                        emptyMessage));
    }
}
