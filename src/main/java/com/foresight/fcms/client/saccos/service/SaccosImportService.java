package com.foresight.fcms.client.saccos.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.*;
import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.shared.dto.*;
import com.foresight.fcms.shared.view.ApplicationTimestamps;
import com.foresight.fcms.upload.UploadInspectionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Private bounded upload snapshots and one atomic, idempotent canonical creation command. */
@Service
public class SaccosImportService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final SaccosService saccos;
    private final SaccosCsvParser csv;
    private final UploadInspectionService inspections;
    private final AuthRateLimitService limits;
    private final AuditService audit;
    private final TransactionTemplate writes;
    private final Semaphore uploads = new Semaphore(2);
    private final JsonMapper json = new JsonMapper();

    public SaccosImportService(
            JdbcTemplate db,
            AccessService access,
            SaccosService saccos,
            SaccosCsvParser csv,
            UploadInspectionService inspections,
            AuthRateLimitService limits,
            AuditService audit,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.db = db;
        this.access = access;
        this.saccos = saccos;
        this.csv = csv;
        this.inspections = inspections;
        this.limits = limits;
        this.audit = audit;
        this.writes = new TransactionTemplate(transactions);
        this.writes.setTimeout(10);
    }

    public record PreparedRow(
            SaccosImportRow row, long yearId, long statusId, String yearLabel, String statusCode) {}

    public record Fact(String label, String value) {}

    public record Row(
            int line,
            String name,
            String region,
            String year,
            String status,
            BigDecimal annualFee,
            BigDecimal paid,
            Long clientId,
            List<Fact> facts) {}

    public record Preview(
            UUID token,
            String state,
            boolean importable,
            int rowCount,
            BigDecimal annualFee,
            BigDecimal paid,
            Instant expiresAt,
            RegisterPage<Row> records) {}

    public record Column(String header, String label, boolean required, String format) {}

    public record Draft(
            long id,
            UUID token,
            int rows,
            BigDecimal annualFee,
            BigDecimal paid,
            Instant createdAt) {}

    private record Batch(
            long id,
            UUID token,
            String digest,
            String state,
            int count,
            String payload,
            BigDecimal annualFee,
            BigDecimal paid,
            Instant expiresAt) {}

    private AccessService.Actor actor() {
        var a = access.require("client.import");
        a.require("client.read");
        a.require("client.write");
        return a;
    }

    public void formAccess() {
        actor();
    }

    @Transactional(readOnly = true)
    public List<Draft> drafts() {
        var a = actor();
        return db.query(
                "select id,token,row_count,total_annual_fee,total_paid,created_at from"
                        + " saccos_import_batches where owner_id=? and state='DRAFT' and"
                        + " expires_at>current_timestamp order by created_at desc,id desc limit 10",
                (r, n) ->
                        new Draft(
                                r.getLong(1),
                                r.getObject(2, UUID.class),
                                r.getInt(3),
                                r.getBigDecimal(4),
                                r.getBigDecimal(5),
                                r.getTimestamp(6).toInstant()),
                a.id());
    }

    public byte[] template() {
        actor();
        return csv.template();
    }

    public List<Column> columns() {
        return SaccosCsvParser.COLUMNS.stream()
                .map(c ->
                        new Column(
                                c.header(),
                                c.label(),
                                c.required(),
                                c.key().equals("financialYear")
                                        ? "2026"
                                        : c.key().equals("paymentStatus")
                                                ? "PAID / UNPAID / PENDING"
                                                : c.key().endsWith("Date")
                                                                || c.key().equals("datePaid")
                                                        ? "YYYY-MM-DD"
                                                        : Set.of("saccosName", "region")
                                                                        .contains(c.key())
                                                                ? "255"
                                                                : "0.00"))
                .toList();
    }

    /**
     * Scanner and parser run before opening a write transaction; the original file is not stored.
     */
    public UUID stage(MultipartFile file, UUID requestKey) {
        var a = actor();
        if (requestKey == null) throw new IllegalArgumentException("error.invalid");
        if (!limits.allow("saccos-import:" + a.id(), 20))
            throw new IllegalArgumentException("import.error.rate");
        if (file == null
                || file.isEmpty()
                || file.getSize() > SaccosCsvParser.MAX_BYTES
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv")
                || file.getContentType() != null
                        && !Set.of(
                                        "text/csv",
                                        "application/csv",
                                        "text/plain",
                                        "application/vnd.ms-excel",
                                        "application/octet-stream")
                                .contains(file.getContentType().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("import.error.file");
        if (!uploads.tryAcquire()) throw new IllegalArgumentException("error.uploadBusy");
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(SaccosCsvParser.MAX_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > SaccosCsvParser.MAX_BYTES)
                throw new IllegalArgumentException("import.error.file");
            inspections.inspect(bytes);
            var rows = prepare(csv.parse(bytes));
            String digest =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String payload = json.writeValueAsString(rows);
            var forms = rows.stream().map(this::form).toList();
            BigDecimal annual =
                    forms.stream()
                            .map(SaccosForm::getAnnualFee)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid =
                    forms.stream()
                            .map(SaccosForm::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            return writes.execute(tx -> {
                var owner = actor();
                if (owner.id() != a.id())
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Access denied");
                // Serialize this owner's draft quota and upload request keys, including the first
                // batch.
                db.query(
                        "select pg_advisory_xact_lock(hashtextextended(?,0))",
                        (r, n) -> 0,
                        "fcms.saccos-import:" + owner.id());
                int expired =
                        db.update(
                                "update saccos_import_batches set"
                                    + " state='EXPIRED',payload='[]'::jsonb where owner_id=? and"
                                    + " state='DRAFT' and expires_at<=current_timestamp",
                                owner.id());
                if (expired > 0)
                    audit.record(
                            owner.id(),
                            "client.import.expire",
                            "saccos_import_batches",
                            null,
                            "Expired drafts=" + expired);
                var previous =
                        db.query(
                                "select token,digest from saccos_import_batches where owner_id=?"
                                        + " and request_key=?",
                                (r, n) ->
                                        Map.entry(
                                                r.getObject("token", UUID.class),
                                                r.getString("digest")),
                                owner.id(),
                                requestKey);
                if (!previous.isEmpty()) {
                    if (!previous.getFirst().getValue().equals(digest))
                        throw new IllegalArgumentException("import.error.request");
                    return previous.getFirst().getKey();
                }
                if (db.queryForObject(
                                "select count(*) from saccos_import_batches where owner_id=? and"
                                        + " state='DRAFT'",
                                Integer.class,
                                owner.id())
                        >= 10) throw new IllegalArgumentException("import.error.drafts");
                UUID token = UUID.randomUUID();
                long id =
                        db.queryForObject(
                                "insert into"
                                    + " saccos_import_batches(token,owner_id,request_key,digest,row_count,payload,total_annual_fee,total_paid)"
                                    + " values (?,?,?,?,?,?::jsonb,?,?) returning id",
                                Long.class,
                                token,
                                owner.id(),
                                requestKey,
                                digest,
                                rows.size(),
                                payload,
                                annual,
                                paid);
                audit.record(
                        owner.id(),
                        "client.import.preview",
                        "saccos_import_batches",
                        id,
                        "Rows=" + rows.size() + "; SHA256=" + digest);
                return token;
            });
        } catch (java.io.IOException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("error.uploadUnavailable");
        } finally {
            uploads.release();
        }
    }

    private List<PreparedRow> prepare(List<SaccosImportRow> rows) {
        var yearIds = new HashSet<Long>();
        var years = new HashSet<String>();
        var statusIds = new HashSet<Long>();
        var statuses = new HashSet<String>();
        for (var r : rows) {
            if (r.values().containsKey("financialYearId"))
                yearIds.add(csv.id(r, "financialYearId"));
            else years.add(r.values().get("financialYear"));
            if (r.values().containsKey("paymentStatusId"))
                statusIds.add(csv.id(r, "paymentStatusId"));
            else statuses.add(r.values().get("paymentStatus").toUpperCase(Locale.ROOT));
        }
        var refs = saccos.importReferences(yearIds, years, statusIds, statuses);
        var result = new ArrayList<PreparedRow>();
        for (var r : rows) {
            Long year =
                    r.values().containsKey("financialYearId")
                            ? Long.valueOf(csv.id(r, "financialYearId"))
                            : refs.years().entrySet().stream()
                                    .filter(e ->
                                            e.getValue().equals(r.values().get("financialYear")))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
            Long status =
                    r.values().containsKey("paymentStatusId")
                            ? Long.valueOf(csv.id(r, "paymentStatusId"))
                            : refs.statuses().entrySet().stream()
                                    .filter(e ->
                                            e.getValue()
                                                    .equalsIgnoreCase(
                                                            r.values().get("paymentStatus")))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
            if (year == null || !refs.years().containsKey(year))
                throw new SaccosImportFailure("import.error.reference", r.line(), "financial_year");
            if (status == null
                    || !refs.statuses().containsKey(status)
                    || !Set.of("PAID", "UNPAID", "PENDING")
                            .contains(refs.statuses().get(status).toUpperCase(Locale.ROOT)))
                throw new SaccosImportFailure("import.error.reference", r.line(), "payment_status");
            result.add(
                    new PreparedRow(
                            r,
                            year,
                            status,
                            refs.years().get(year),
                            refs.statuses().get(status).toUpperCase(Locale.ROOT)));
        }
        return List.copyOf(result);
    }

    private SaccosForm form(PreparedRow r) {
        return csv.form(r.row(), r.yearId(), r.statusId());
    }

    private List<PreparedRow> payload(Batch b) {
        if (!Set.of("DRAFT", "COMMITTED").contains(b.state())) return List.of();
        try {
            var rows = List.of(json.readValue(b.payload(), PreparedRow[].class));
            if (rows.size() != b.count() || rows.size() > 100) throw new IllegalArgumentException();
            return rows;
        } catch (RuntimeException corrupt) {
            throw new IllegalArgumentException("import.error.unavailable");
        }
    }

    private Batch owned(UUID token, long owner, boolean lock) {
        return db
                .query(
                        "select * from saccos_import_batches where token=? and owner_id=?"
                                + (lock ? " for update" : ""),
                        (r, n) ->
                                new Batch(
                                        r.getLong("id"),
                                        r.getObject("token", UUID.class),
                                        r.getString("digest"),
                                        r.getString("state"),
                                        r.getInt("row_count"),
                                        r.getString("payload"),
                                        r.getBigDecimal("total_annual_fee"),
                                        r.getBigDecimal("total_paid"),
                                        r.getTimestamp("expires_at").toInstant()),
                        token,
                        owner)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID token, RegisterQuery query) {
        var a = actor();
        var b = owned(token, a.id(), false);
        var prepared = payload(b);
        var ids = new HashMap<Integer, Long>();
        if (b.state().equals("COMMITTED"))
            db.query(
                    "select i.ordinal,i.client_id from saccos_import_clients i"
                            + " join saccos s on s.id=i.client_id and s.visible=true"
                            + " where i.batch_id=? order by i.ordinal limit 100",
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            r -> ids.put(r.getInt(1), r.getLong(2)),
                    b.id());
        int start = (int) Math.min(prepared.size(), query.offset()),
                end = Math.min(prepared.size(), start + query.getSize() + 1);
        var rows = new ArrayList<Row>();
        for (int i = start; i < end; i++) {
            var r = prepared.get(i);
            var f = form(r);
            var facts = new ArrayList<Fact>();
            for (var c : SaccosCsvParser.COLUMNS)
                if (!Set.of(
                                "saccosName",
                                "region",
                                "financialYear",
                                "paymentStatus",
                                "annualFee",
                                "amount")
                        .contains(c.key())) {
                    String value = r.row().values().getOrDefault(c.key(), "");
                    if (c.key().endsWith("Date") || c.key().equals("datePaid"))
                        value =
                                value.isBlank()
                                        ? ""
                                        : ApplicationTimestamps.date(
                                                java.time.LocalDate.parse(value));
                    else
                        value =
                                value.isBlank()
                                        ? "0.00"
                                        : new BigDecimal(value).setScale(2).toPlainString();
                    facts.add(new Fact(c.label(), value));
                }
            rows.add(
                    new Row(
                            r.row().line(),
                            f.getSaccosName(),
                            f.getRegion(),
                            r.yearLabel(),
                            r.statusCode(),
                            f.getAnnualFee(),
                            f.getAmount(),
                            ids.get(i + 1),
                            List.copyOf(facts)));
        }
        boolean live = b.state().equals("DRAFT") && b.expiresAt().isAfter(Instant.now());
        return new Preview(
                b.token(),
                !live && b.state().equals("DRAFT") ? "EXPIRED" : b.state(),
                live,
                b.count(),
                b.annualFee(),
                b.paid(),
                b.expiresAt(),
                RegisterPage.of(rows, query));
    }

    @Transactional(timeout = 20)
    public boolean commit(UUID token) {
        var a = actor();
        var b = owned(token, a.id(), true);
        if (b.state().equals("COMMITTED")) return true;
        if (!b.state().equals("DRAFT")) throw new IllegalArgumentException("import.error.closed");
        if (!b.expiresAt().isAfter(Instant.now())) {
            db.update(
                    "update saccos_import_batches set state='EXPIRED',payload='[]'::jsonb where"
                            + " id=?",
                    b.id());
            audit.record(
                    a.id(),
                    "client.import.expire",
                    "saccos_import_batches",
                    b.id(),
                    "Expired before import");
            return false;
        }
        var rows = payload(b);
        var refs =
                saccos.lockImportReferences(
                        rows.stream()
                                .map(PreparedRow::yearId)
                                .collect(java.util.stream.Collectors.toSet()),
                        rows.stream()
                                .map(PreparedRow::statusId)
                                .collect(java.util.stream.Collectors.toSet()));
        for (var row : rows)
            if (!row.yearLabel().equals(refs.years().get(row.yearId()))
                    || !row.statusCode().equalsIgnoreCase(refs.statuses().get(row.statusId())))
                throw new IllegalArgumentException("import.error.changed");
        var forms = rows.stream().map(this::form).toList();
        if (forms.stream()
                                .map(SaccosForm::getAnnualFee)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .compareTo(b.annualFee())
                        != 0
                || forms.stream()
                                .map(SaccosForm::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .compareTo(b.paid())
                        != 0) throw new IllegalArgumentException("import.error.changed");
        var ids = saccos.createImportedSaccos(forms);
        for (int i = 0; i < ids.size(); i++)
            db.update(
                    "insert into saccos_import_clients(batch_id,ordinal,client_id) values (?,?,?)",
                    b.id(),
                    i + 1,
                    ids.get(i));
        db.update(
                "update saccos_import_batches set state='COMMITTED',committed_at=current_timestamp"
                        + " where id=?",
                b.id());
        audit.record(
                a.id(),
                "client.import.commit",
                "saccos_import_batches",
                b.id(),
                "Clients=" + ids.size() + "; SHA256=" + b.digest());
        return true;
    }

    @Transactional(timeout = 10)
    public void discard(UUID token) {
        var a = actor();
        var b = owned(token, a.id(), true);
        if (Set.of("CANCELLED", "EXPIRED").contains(b.state())) return;
        if (!b.state().equals("DRAFT")) throw new IllegalArgumentException("import.error.closed");
        db.update(
                "update saccos_import_batches set state='CANCELLED',payload='[]'::jsonb where id=?",
                b.id());
        audit.record(
                a.id(),
                "client.import.discard",
                "saccos_import_batches",
                b.id(),
                "Draft discarded");
    }
}
