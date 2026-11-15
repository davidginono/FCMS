package com.foresight.fcms.client.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class ClientArchiveService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;

    public ClientArchiveService(JdbcTemplate db, AccessService access, AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    // These identifiers are owned by the client module and never come from request text.
    private static final Map<String, Register> REGISTERS =
            Map.of(
                    "saccos", new Register("saccos", "saccos_name", "client.category.saccos"),
                    "amcos", new Register("amcos", "client_name", "client.category.amcos"),
                    "unions", new Register("unions", "client_name", "client.category.unions"),
                    "microfinance",
                            new Register(
                                    "microfinance_clients",
                                    "client_name",
                                    "client.category.microfinance_clients"),
                    "others",
                            new Register(
                                    "other_clients",
                                    "client_name",
                                    "client.category.other_clients"));

    public Register register(String slug) {
        var register = REGISTERS.get(slug);
        if (register == null) throw new IllegalArgumentException("error.invalid");
        return register;
    }

    private void requireAccess() {
        access.require("client.read");
        access.require("client.write");
    }

    private String select(Register r) {
        return "select c.id,c."
                + r.nameColumn()
                + " name,c.region,coalesce(y.fyear,'')"
                + " financial_year,c.amount,c.version,c.visible,c.updated_at from "
                + r.table()
                + " c left join financial_year y on y.id=c.financial_year_id";
    }

    private ClientRecord row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new ClientRecord(
                r.getLong("id"),
                r.getString("name"),
                r.getString("region"),
                r.getString("financial_year"),
                r.getBigDecimal("amount"),
                r.getLong("version"),
                r.getBoolean("visible"),
                r.getTimestamp("updated_at").toInstant());
    }

    @Transactional(readOnly = true)
    public RegisterPage<ClientRecord> list(String slug, RegisterQuery q) {
        requireAccess();
        var r = register(slug);
        q.validate();
        return RegisterPage.of(
                db.query(
                        select(r)
                                + " where not c.visible and lower(c."
                                + r.nameColumn()
                                + "||' '||coalesce(c.region,'')) like ? escape '!' order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "c.id",
                                                "name",
                                                "c." + r.nameColumn(),
                                                "region",
                                                "c.region",
                                                "year",
                                                "y.fyear",
                                                "amount",
                                                "c.amount",
                                                "date",
                                                "c.updated_at"))
                                + " limit ? offset ?",
                        this::row,
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public ClientRecord find(String slug, long id) {
        requireAccess();
        var r = register(slug);
        return db.query(select(r) + " where c.id=?", this::row, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional
    public void visibility(String slug, long id, long version, boolean visible, String reason) {
        var actor = access.require("client.write");
        access.require("client.read");
        var r = register(slug);
        if (version < 0 || reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("error.invalid");
        db.queryForList("select id from " + r.table() + " where id=? for update", id);
        var record = find(slug, id);
        if (record.visible() == visible && record.version() == version + 1) return;
        if (record.visible() == visible || record.version() != version)
            throw new OptimisticLockingFailureException("stale");
        if (db.update(
                        "update "
                                + r.table()
                                + " set visible=?,version=version+1,updated_at=current_timestamp"
                                + " where id=? and version=? and visible=?",
                        visible,
                        id,
                        version,
                        !visible)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(
                actor.id(),
                visible ? "client.restore" : "client.archive",
                r.table(),
                id,
                "visible=" + record.visible() + "->" + visible + "; " + reason.trim());
    }

    public record Register(String table, String nameColumn, String label) {}

    public record ClientRecord(
            long id,
            String name,
            String region,
            String year,
            BigDecimal amount,
            long version,
            boolean visible,
            Instant updatedAt) {}
}
