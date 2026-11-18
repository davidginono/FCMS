package com.foresight.fcms.client.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.saccos.service.SaccosStorageService;
import com.foresight.fcms.shared.validation.FieldValidationException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/** Client-owned lifecycle operations. Financial and referenced records cannot be erased. */
@Service
public class ClientLifecycleService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final ClientArchiveService registers;
    private final SaccosStorageService storage;
    private final TransactionTemplate writes;
    private static final Set<String> AMOUNTS =
            Set.of(
                    "annual_fee",
                    "amount",
                    "withholding_tax",
                    "commission",
                    "programmer_cost",
                    "system_install_fee",
                    "training_fee",
                    "android_fee",
                    "android_cost",
                    "system_reinstall_fee",
                    "bulk_sms_fee",
                    "hosting_fee",
                    "ess_fee",
                    "cloud_backup_fee",
                    "cloud_backup_cost");

    public ClientLifecycleService(
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            ClientArchiveService registers,
            SaccosStorageService storage,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.registers = registers;
        this.storage = storage;
        this.writes = new TransactionTemplate(transactions);
    }

    private AccessService.Actor actor() {
        var actor = access.require("client.read");
        actor.require("client.write");
        return actor;
    }

    private Map<String, Object> load(String slug, long id, boolean lock) {
        var r = registers.register(slug);
        var rows =
                db.queryForList(
                        "select * from "
                                + r.table()
                                + " where id=? and visible"
                                + (lock ? " for update" : ""),
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("error.missing");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public ClientAction find(String slug, long id) {
        actor();
        var row = load(slug, id, false);
        return action(slug, row);
    }

    private ClientAction action(String slug, Map<String, Object> row) {
        var r = registers.register(slug);
        String status = (String) row.get("operational_status");
        String contract = (String) row.get("contract_file");
        return new ClientAction(
                ((Number) row.get("id")).longValue(),
                (String) row.get(r.nameColumn()),
                ((Number) row.get("version")).longValue(),
                status,
                contract != null && !contract.isBlank(),
                blocked(r.table(), row),
                status != null && !status.equals("INACTIVE"));
    }

    private boolean blocked(String table, Map<String, Object> row) {
        for (String column : AMOUNTS) {
            Object value = row.get(column);
            if (value != null && new BigDecimal(value.toString()).signum() != 0) return true;
        }
        if (row.get("date_paid") != null
                || row.get("contract_file") != null
                        && !row.get("contract_file").toString().isBlank()) return true;
        // Discover declared references, including future module additions, inside this schema.
        var refs =
                db.queryForList(
                        """
                        select distinct n.nspname schema_name,t.relname table_name,a.attname column_name
                        from pg_constraint f
                        join pg_class t on t.oid=f.conrelid join pg_namespace n on n.oid=t.relnamespace
                        join lateral unnest(f.conkey,f.confkey) k(local_key,target_key) on true
                        join pg_attribute a on a.attrelid=f.conrelid and a.attnum=k.local_key
                        join pg_attribute b on b.attrelid=f.confrelid and b.attnum=k.target_key
                        where f.contype='f' and f.confrelid=to_regclass(format('%I.%I',current_schema(),cast(? as text))) and b.attname='id'
                        limit 65
                        """,
                        table);
        if (refs.size() > 64) return true;
        for (var ref : refs) {
            String schema = identifier(ref.get("schema_name")),
                    name = identifier(ref.get("table_name")),
                    column = identifier(ref.get("column_name"));
            if (Boolean.TRUE.equals(
                    db.queryForObject(
                            "select exists(select 1 from \""
                                    + schema
                                    + "\".\""
                                    + name
                                    + "\" where \""
                                    + column
                                    + "\"=?)",
                            Boolean.class,
                            row.get("id")))) return true;
        }
        return false;
    }

    private String identifier(Object value) {
        String s = value.toString();
        if (!s.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
            throw new IllegalStateException("Unsupported reference identifier");
        return s;
    }

    @Transactional
    public Long change(String slug, long id, long version, String command) {
        var actor = actor();
        var register = registers.register(slug);
        var row = load(slug, id, true);
        if (((Number) row.get("version")).longValue() != version)
            throw new OptimisticLockingFailureException("stale");
        var state = action(slug, row);
        if ("deactivate".equals(command) && state.canDeactivate()) {
            db.update(
                    "update "
                            + register.table()
                            + " set operational_status='INACTIVE',version=version+1,updated_at=current_timestamp"
                            + " where id=?",
                    id);
            audit.record(
                    actor.id(),
                    "client.status.update",
                    register.table(),
                    id,
                    "before=" + state.status() + "; after=INACTIVE");
        } else if ("delete".equals(command) && !state.blocked()) {
            db.update("delete from " + register.table() + " where id=?", id);
            audit.record(
                    actor.id(),
                    "client.delete",
                    register.table(),
                    id,
                    "Unused client deleted; version=" + version);
        } else throw new IllegalArgumentException("error.clientHistory");
        Object year = row.get("financial_year_id");
        return "others".equals(slug) || year == null ? null : ((Number) year).longValue();
    }

    public void updateContract(String slug, long id, long version, MultipartFile file) {
        actor();
        if (file == null || file.isEmpty())
            throw new FieldValidationException("NotNull", "contractUpload");
        // Inspect outside the database transaction; retain all previously committed contracts.
        String stored = storage.storeContract(file);
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            writes.executeWithoutResult(
                    tx -> {
                        storage.discardOnRollback(stored);
                        registered.set(true);
                        var actor = actor();
                        var r = registers.register(slug);
                        var row = load(slug, id, true);
                        if (((Number) row.get("version")).longValue() != version)
                            throw new OptimisticLockingFailureException("stale");
                        db.update(
                                "update "
                                        + r.table()
                                        + " set contract_file=?,version=version+1,updated_at=current_timestamp"
                                        + " where id=?",
                                stored,
                                id);
                        audit.record(
                                actor.id(),
                                "client.contract.update",
                                r.table(),
                                id,
                                "before=" + row.get("contract_file") + "; after=" + stored);
                    });
        } catch (RuntimeException e) {
            if (!registered.get()) storage.discardUncommitted(stored);
            throw e;
        }
    }

    public record ClientAction(
            long id,
            String name,
            long version,
            String status,
            boolean hasContract,
            boolean blocked,
            boolean canDeactivate) {}
}
