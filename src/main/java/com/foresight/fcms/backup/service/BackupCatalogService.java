package com.foresight.fcms.backup.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BackupCatalogService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;

    public BackupCatalogService(JdbcTemplate db, AccessService access, AuditService audit) {
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public com.foresight.fcms.shared.dto.RegisterPage<Backup> list(
            com.foresight.fcms.shared.dto.RegisterQuery q) {
        access.require("backup.manage");
        q.validate();
        return com.foresight.fcms.shared.dto.RegisterPage.of(
                db.query(
                        "select id,status,created_at,size_bytes,error_code from system_backups"
                            + " where cast(id as text) like ? escape '!' and (?='' or status=?) and"
                            + " (?::date is null or created_at>=?::date) and (?::date is null or"
                            + " created_at<(?::date+interval '1 day')) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "id",
                                                "status",
                                                "status",
                                                "date",
                                                "created_at",
                                                "size",
                                                "size_bytes"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Backup(
                                        r.getObject(1, UUID.class),
                                        r.getString(2),
                                        r.getObject(3, java.time.OffsetDateTime.class),
                                        r.getObject(4, Long.class),
                                        r.getString(5)),
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional
    public UUID request() {
        var a = access.require("backup.manage");
        if (db.queryForObject(
                        "select count(*) from system_backups where status='READY'", Long.class)
                >= 20) throw new IllegalArgumentException("error.backupLimit");
        UUID id = UUID.randomUUID();
        db.update(
                "insert into system_backups(id,status,requested_by) values (?,'QUEUED',?)",
                id,
                a.id());
        audit.record(a.id(), "backup.request", "system_backups", null, "Backup " + id);
        return id;
    }

    @Transactional
    public UUID claim() {
        db.update(
                "update system_backups set"
                        + " status='FAILED',finished_at=current_timestamp,error_code='INTERRUPTED'"
                        + " where status='RUNNING' and started_at<current_timestamp-interval '15"
                        + " minutes'");
        var ids =
                db.queryForList(
                        "with pending as (select id from system_backups where status='QUEUED' order"
                            + " by created_at for update skip locked limit 1) update system_backups"
                            + " b set status='RUNNING',started_at=current_timestamp from pending p"
                            + " where b.id=p.id returning b.id",
                        UUID.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    @Transactional
    public void finish(UUID id, Long size, String failure) {
        db.update(
                "update system_backups set"
                    + " status=?,size_bytes=?,error_code=?,finished_at=current_timestamp where id=?"
                    + " and status='RUNNING'",
                failure == null ? "READY" : "FAILED",
                size,
                failure,
                id);
    }

    @Transactional(readOnly = true)
    public void authorizeDownload(UUID id) {
        access.require("backup.manage");
        if (db.queryForObject(
                        "select count(*) from system_backups where id=? and status='READY'",
                        Long.class,
                        id)
                != 1) throw new IllegalArgumentException("error.missing");
    }

    public record Backup(
            UUID id, String status, java.time.OffsetDateTime createdAt, Long size, String error) {}
}
