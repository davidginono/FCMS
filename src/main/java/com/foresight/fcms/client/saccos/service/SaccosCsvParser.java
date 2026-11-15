package com.foresight.fcms.client.saccos.service;

import com.foresight.fcms.client.saccos.dto.*;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;

/** Strict, bounded RFC4180 adapter for legacy and canonical SACCOS column names. */
@Component
public class SaccosCsvParser {
    public static final int MAX_BYTES = 1024 * 1024, MAX_ROWS = 100;

    public record Column(
            String key,
            String header,
            String label,
            boolean required,
            BiConsumer<SaccosForm, String> setter) {}

    private static BigDecimal money(String text) {
        if (text.isEmpty()) return new BigDecimal("0.00");
        if (!text.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
        return new BigDecimal(text).setScale(2);
    }

    private static LocalDate date(String text) {
        if (text.isEmpty()) return null;
        if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        var value = LocalDate.parse(text);
        if (value.getYear() < 1) throw new IllegalArgumentException();
        return value;
    }

    private static String name(String text) {
        if (text.isBlank()
                || text.length() > 255
                || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException();
        return text;
    }

    public static final List<Column> COLUMNS =
            List.of(
                    new Column(
                            "saccosName",
                            "Saccos_Name",
                            "common.name",
                            true,
                            (f, v) -> f.setSaccosName(name(v))),
                    new Column(
                            "entryDate",
                            "Entry_Date",
                            "client.entryDate",
                            true,
                            (f, v) -> {
                                var d = date(v);
                                if (d == null) throw new IllegalArgumentException();
                                f.setEntryDate(d);
                            }),
                    new Column(
                            "expireDate",
                            "expire_date",
                            "client.expireDate",
                            false,
                            (f, v) -> f.setExpireDate(date(v))),
                    new Column(
                            "region",
                            "Region",
                            "client.region",
                            true,
                            (f, v) -> f.setRegion(name(v))),
                    new Column(
                            "annualFee",
                            "Annual_fee",
                            "client.annualFee",
                            false,
                            (f, v) -> f.setAnnualFee(money(v))),
                    new Column(
                            "amount",
                            "Amount",
                            "client.amountPaid",
                            false,
                            (f, v) -> f.setAmount(money(v))),
                    new Column(
                            "datePaid",
                            "datepaid",
                            "client.datePaid",
                            false,
                            (f, v) -> f.setDatePaid(date(v))),
                    new Column(
                            "withholdingTax",
                            "tax",
                            "client.withholdingTax",
                            false,
                            (f, v) -> f.setWithholdingTax(money(v))),
                    new Column(
                            "commission",
                            "com",
                            "client.commission",
                            false,
                            (f, v) -> f.setCommission(money(v))),
                    new Column(
                            "programmerCost",
                            "Programmer_cost",
                            "client.programmerCost",
                            false,
                            (f, v) -> f.setProgrammerCost(money(v))),
                    new Column(
                            "systemInstallFee",
                            "Sys_install_fee",
                            "client.systemInstallFee",
                            false,
                            (f, v) -> f.setSystemInstallFee(money(v))),
                    new Column(
                            "trainingFee",
                            "Training_fee",
                            "client.trainingFee",
                            false,
                            (f, v) -> f.setTrainingFee(money(v))),
                    new Column(
                            "androidFee",
                            "Android_fee",
                            "client.androidFee",
                            false,
                            (f, v) -> f.setAndroidFee(money(v))),
                    new Column(
                            "androidCost",
                            "android_cost",
                            "client.androidCost",
                            false,
                            (f, v) -> f.setAndroidCost(money(v))),
                    new Column(
                            "systemReinstallFee",
                            "Sys_reinstall_fee",
                            "client.systemReinstallFee",
                            false,
                            (f, v) -> f.setSystemReinstallFee(money(v))),
                    new Column(
                            "bulkSmsFee",
                            "bulk_sms",
                            "client.bulkSmsFee",
                            false,
                            (f, v) -> f.setBulkSmsFee(money(v))),
                    new Column(
                            "hostingFee",
                            "hosting_fee",
                            "client.hostingFee",
                            false,
                            (f, v) -> f.setHostingFee(money(v))),
                    new Column(
                            "essFee",
                            "ess_fee",
                            "client.essFee",
                            false,
                            (f, v) -> f.setEssFee(money(v))),
                    new Column(
                            "cloudBackupFee",
                            "cloud_backup",
                            "client.cloudBackupFee",
                            false,
                            (f, v) -> f.setCloudBackupFee(money(v))),
                    new Column(
                            "cloudBackupCost",
                            "backup_cost",
                            "client.cloudBackupCost",
                            false,
                            (f, v) -> f.setCloudBackupCost(money(v))),
                    new Column(
                            "financialYear", "financial_year", "report.year", true, (f, v) -> {}),
                    new Column(
                            "paymentStatus",
                            "payment_status",
                            "common.status",
                            true,
                            (f, v) -> {}));
    private static final Map<String, String> HEADERS;

    static {
        var names = new HashMap<String, String>();
        for (var c : COLUMNS) {
            names.put(normalize(c.header()), c.key());
            names.put(normalize(c.key()), c.key());
        }
        names.put("fyearid", "financialYearId");
        names.put("saccosstatusid", "paymentStatusId");
        HEADERS = Map.copyOf(names);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
    }

    public List<SaccosImportRow> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw failure("import.error.file", 0, "");
        final String source;
        try {
            source =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException bad) {
            throw failure("import.error.encoding", 0, "");
        }
        String text = source.startsWith("\ufeff") ? source.substring(1) : source;
        var records = records(text);
        if (records.size() < 2) throw failure("import.error.empty", 0, "");
        var headers = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var raw : records.getFirst().cells()) {
            String key = HEADERS.get(normalize(raw.trim()));
            if (key == null) throw failure("import.error.header", 1, "");
            if (!seen.add(key)) throw failure("import.error.duplicate", 1, "");
            headers.add(key);
        }
        for (var c : COLUMNS)
            if (c.required()
                    && !seen.contains(c.key())
                    && !(c.key().equals("financialYear") && seen.contains("financialYearId"))
                    && !(c.key().equals("paymentStatus") && seen.contains("paymentStatusId")))
                throw failure("import.error.required", 1, c.header());
        if (seen.containsAll(Set.of("financialYear", "financialYearId"))
                || seen.containsAll(Set.of("paymentStatus", "paymentStatusId")))
            throw failure("import.error.duplicate", 1, "");
        var result = new ArrayList<SaccosImportRow>();
        for (var record : records.subList(1, records.size())) {
            if (record.cells().size() != headers.size())
                throw failure("import.error.columns", record.line(), "");
            var values = new LinkedHashMap<String, String>();
            for (int i = 0; i < headers.size(); i++)
                values.put(headers.get(i), record.cells().get(i).trim());
            var row = new SaccosImportRow(record.line(), values);
            form(row, 1L, 1L);
            for (String key : List.of("financialYearId", "paymentStatusId"))
                if (values.containsKey(key)) id(row, key);
            if (values.containsKey("financialYear")
                    && (values.get("financialYear").isBlank()
                            || values.get("financialYear").length() > 255))
                throw failure("import.error.value", row.line(), "financial_year");
            if (values.containsKey("paymentStatus")
                    && !Set.of("PAID", "UNPAID", "PENDING")
                            .contains(values.get("paymentStatus").toUpperCase(Locale.ROOT)))
                throw failure("import.error.value", row.line(), "payment_status");
            result.add(row);
        }
        return List.copyOf(result);
    }

    public long id(SaccosImportRow row, String key) {
        try {
            String value = row.values().get(key);
            if (value == null || !value.matches("[0-9]{1,19}"))
                throw new IllegalArgumentException();
            long id = Long.parseLong(value);
            if (id < 1) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException bad) {
            throw failure(
                    "import.error.reference",
                    row.line(),
                    key.equals("financialYearId") ? "fyear_id" : "saccos_status_id");
        }
    }

    public SaccosForm form(SaccosImportRow row, long year, long status) {
        var form = new SaccosForm();
        for (var c : COLUMNS) {
            if (c.key().equals("financialYear") || c.key().equals("paymentStatus")) continue;
            try {
                c.setter().accept(form, row.values().getOrDefault(c.key(), ""));
            } catch (RuntimeException bad) {
                throw failure("import.error.value", row.line(), c.header());
            }
        }
        if (form.getExpireDate() != null && form.getExpireDate().isBefore(form.getEntryDate()))
            throw failure("import.error.value", row.line(), "expire_date");
        form.setFinancialYearId(year);
        form.setPaymentStatusId(status);
        return form;
    }

    public byte[] template() {
        return (String.join(",", COLUMNS.stream().map(Column::header).toList()) + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private record Record(int line, List<String> cells) {}

    private List<Record> records(String text) {
        var result = new ArrayList<Record>();
        var cells = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false, closed = false;
        int line = 1, start = 1;
        for (int i = 0; i <= text.length(); i++) {
            char ch = i == text.length() ? '\n' : text.charAt(i);
            if (ch == 0 || Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')
                throw failure("import.error.csv", line, "");
            if (quoted) {
                if (i == text.length()) throw failure("import.error.csv", line, "");
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    field.append(ch);
                    if (ch == '\n') line++;
                }
            } else if (ch == '"') {
                if (!field.isEmpty() || closed) throw failure("import.error.csv", line, "");
                quoted = true;
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                cells.add(field.toString());
                field.setLength(0);
                closed = false;
                if (cells.size() > 24) throw failure("import.error.columns", line, "");
                if (ch != ',') {
                    if (cells.stream().anyMatch(v -> !v.isBlank()))
                        result.add(new Record(start, List.copyOf(cells)));
                    if (result.size() > MAX_ROWS + 1) throw failure("import.error.limit", line, "");
                    cells.clear();
                    if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    line++;
                    start = line;
                }
            } else {
                if (closed) throw failure("import.error.csv", line, "");
                field.append(ch);
            }
            if (field.length() > 4096) throw failure("import.error.value", line, "");
        }
        return result;
    }

    private SaccosImportFailure failure(String code, int line, String field) {
        return new SaccosImportFailure(code, line, field);
    }
}
