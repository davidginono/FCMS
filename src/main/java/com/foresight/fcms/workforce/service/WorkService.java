package com.foresight.fcms.workforce.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.*;
import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.shared.dto.*;
import com.foresight.fcms.workforce.dto.*;
import com.foresight.fcms.workforce.model.WorkKind;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@org.springframework.validation.annotation.Validated
@Service
public class WorkService {
    private final com.foresight.fcms.notifications.service.MailOutboxService mail;
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final UserDirectoryService users;
    private final ClientDirectoryService clients;

    public WorkService(
            com.foresight.fcms.notifications.service.MailOutboxService mail,
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            UserDirectoryService users,
            ClientDirectoryService clients) {
        this.mail = mail;
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.users = users;
        this.clients = clients;
    }

    private static final String SELECT =
            "select w.*,u.first_name||' '||u.last_name as owner,coalesce(c.saccos_name,'') as"
                    + " client from work_items w join iam_staff_directory u on u.id=w.owner_id left"
                    + " join client_saccos_directory c on c.id=w.client_id";

    private WorkView row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new WorkView(
                r.getLong("id"),
                r.getString("kind"),
                r.getLong("owner_id"),
                r.getString("owner"),
                r.getObject("client_id", Long.class),
                r.getString("client"),
                r.getString("subject"),
                r.getString("description"),
                r.getObject("start_at", java.time.LocalDateTime.class),
                r.getObject("end_at", java.time.LocalDateTime.class),
                r.getString("work_type"),
                r.getString("outcome"),
                r.getString("explanation"),
                r.getBigDecimal("allowance"),
                r.getBigDecimal("amount"),
                r.getString("status"),
                r.getString("review_comment"),
                r.getLong("version"));
    }

    @Transactional(readOnly = true)
    public RegisterPage<WorkView> list(WorkKind k, RegisterQuery q) {
        var a = access.require(k.claim + ".read");
        q.validate();
        boolean all = a.has(k.claim + ".read.all");
        String sql =
                SELECT
                        + " where w.kind=? and (? or w.owner_id=? or exists(select 1 from"
                        + " work_participants p where p.work_id=w.id and p.user_id=?) or (? and"
                        + " w.created_by=?)) and (?::bigint is null or w.owner_id=?::bigint) and"
                        + " (?::bigint is null or w.client_id=?::bigint) and lower(w.subject||'"
                        + " '||w.description) like ? escape '!' and (?='' or w.status=?) and"
                        + " (?::date is null or w.start_at>=?::date) and (?::date is null or"
                        + " w.start_at<(?::date+1)) order by "
                        + q.order(
                                Map.of(
                                        "id",
                                        "w.id",
                                        "owner",
                                        "owner",
                                        "client",
                                        "client",
                                        "subject",
                                        "w.subject",
                                        "startAt",
                                        "w.start_at",
                                        "endAt",
                                        "w.end_at",
                                        "status",
                                        "w.status"))
                        + " limit ? offset ?";
        return RegisterPage.of(
                db.query(
                        sql,
                        this::row,
                        k.name(),
                        all,
                        a.id(),
                        a.id(),
                        k == WorkKind.TASK && a.has("task.manage"),
                        a.id(),
                        q.getOwnerId(),
                        q.getOwnerId(),
                        q.getClientId(),
                        q.getClientId(),
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

    @Transactional(readOnly = true)
    public WorkView find(WorkKind k, long id) {
        var a = access.require(k.claim + ".read");
        return db
                .query(
                        SELECT
                                + " where w.id=? and w.kind=? and (? or w.owner_id=? or"
                                + " exists(select 1 from work_participants p where p.work_id=w.id"
                                + " and p.user_id=?) or (? and w.created_by=?))",
                        this::row,
                        id,
                        k.name(),
                        a.has(k.claim + ".read.all"),
                        a.id(),
                        a.id(),
                        k == WorkKind.TASK && a.has("task.manage"),
                        a.id())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> options(WorkKind k, WorkForm form) {
        access.require(k.claim + ".write");
        var selected = new HashSet<Long>(form.getParticipants());
        if (form.getOwnerId() != null) selected.add(form.getOwnerId());
        var known = users.selected(selected);
        var choices = new LinkedHashMap<Long, UserDirectoryService.Choice>();
        users.staff("").forEach(c -> choices.put(c.id(), c));
        known.forEach(c -> choices.put(c.id(), c));
        return Map.of(
                "staff",
                List.copyOf(choices.values()),
                "selectedStaff",
                known.stream().filter(c -> form.getParticipants().contains(c.id())).toList(),
                "clients",
                k == WorkKind.LEAVE ? List.of() : clients.saccos("", form.getClientId()));
    }

    @Transactional(readOnly = true)
    public @jakarta.validation.Valid WorkForm form(WorkKind k, long id) {
        var w = find(k, id);
        var f = new WorkForm();
        f.setOwnerId(w.ownerId());
        f.setClientId(w.clientId());
        f.setSubject(w.subject());
        f.setDescription(w.description());
        f.setStartAt(w.startAt());
        f.setEndAt(w.endAt());
        f.setWorkType(w.workType());
        f.setOutcome(w.outcome());
        f.setExplanation(w.explanation());
        f.setAllowance(w.allowance());
        f.setAmount(w.amount());
        f.setVersion(w.version());
        f.setParticipants(
                db.queryForList(
                        "select user_id from work_participants where work_id=?", Long.class, id));
        return f;
    }

    @Transactional
    public long save(WorkKind k, Long id, @jakarta.validation.Valid WorkForm f) {
        var a = access.require(k.claim + ".write");
        WorkView existing = id == null ? null : find(k, id);
        if (existing != null
                && existing.ownerId() != a.id()
                && !(k == WorkKind.TASK && a.has("task.manage")))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (f.getEndAt().isBefore(f.getStartAt()))
            throw new IllegalArgumentException("error.dates");
        long owner =
                a.has("task.manage") && k == WorkKind.TASK && f.getOwnerId() != null
                        ? f.getOwnerId()
                        : a.id();
        if (k == WorkKind.TASK && !a.has("task.manage") && existing != null)
            owner = existing.ownerId();
        if (k == WorkKind.TASK && a.has("task.manage"))
            users.requireActive(java.util.List.of(owner));
        if (f.getClientId() != null) clients.requireSaccos(f.getClientId());
        if (k == WorkKind.TASK && f.getClientId() == null)
            throw new IllegalArgumentException("error.invalid");
        if (k == WorkKind.REPORT
                && "INCOMPLETE".equals(f.getOutcome())
                && f.getExplanation().isBlank())
            throw new IllegalArgumentException("error.invalid");
        if (!Set.of("", "COMPLETED", "INCOMPLETE", "IN_PROGRESS").contains(f.getOutcome()))
            throw new IllegalArgumentException("error.invalid");
        java.util.Set<Long> team = new java.util.HashSet<>(f.getParticipants());
        if (k == WorkKind.REPORT && f.getWorkType().equals("GROUP")) {
            team.add(a.id());
            if (team.size() < 2) throw new IllegalArgumentException();
            users.requireActive(team);
        } else team.clear();
        if (id == null) {
            var old =
                    db.queryForList(
                            "select id from work_items where created_by=? and kind=? and"
                                    + " request_key=?",
                            Long.class,
                            a.id(),
                            k.name(),
                            f.getRequestKey());
            if (!old.isEmpty()) return old.getFirst();
            id =
                    db.queryForObject(
                            "insert into"
                                + " work_items(kind,owner_id,client_id,subject,description,start_at,end_at,work_type,outcome,explanation,allowance,amount,status,created_by,request_key)"
                                + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id",
                            Long.class,
                            k.name(),
                            owner,
                            f.getClientId(),
                            f.getSubject().trim(),
                            f.getDescription(),
                            f.getStartAt(),
                            f.getEndAt(),
                            f.getWorkType(),
                            f.getOutcome(),
                            f.getExplanation(),
                            f.getAllowance(),
                            f.getAmount(),
                            k == WorkKind.TASK ? "PLANNED" : "DRAFT",
                            a.id(),
                            f.getRequestKey());
        } else {
            var old = existing;
            if (old.ownerId() != a.id() && !a.has("task.manage"))
                throw new org.springframework.security.access.AccessDeniedException("denied");
            if (!Set.of("DRAFT", "REJECTED", "PLANNED", "IN_PROGRESS").contains(old.status()))
                throw new IllegalArgumentException("error.invalid");
            if (db.update(
                            "update work_items set"
                                + " owner_id=?,client_id=?,subject=?,description=?,start_at=?,end_at=?,work_type=?,outcome=?,explanation=?,allowance=?,amount=?,version=version+1,updated_at=current_timestamp"
                                + " where id=? and kind=? and version=?",
                            owner,
                            f.getClientId(),
                            f.getSubject(),
                            f.getDescription(),
                            f.getStartAt(),
                            f.getEndAt(),
                            f.getWorkType(),
                            f.getOutcome(),
                            f.getExplanation(),
                            f.getAllowance(),
                            f.getAmount(),
                            id,
                            k.name(),
                            f.getVersion())
                    != 1) throw new OptimisticLockingFailureException("stale");
        }
        db.update("delete from work_participants where work_id=?", id);
        final long workId = id;
        db.batchUpdate(
                "insert into work_participants(work_id,user_id) values (?,?)",
                team,
                20,
                (statement, member) -> {
                    statement.setLong(1, workId);
                    statement.setLong(2, member);
                });
        audit.record(a.id(), k.claim + ".save", "work_items", id, k.name());
        return id;
    }

    @Transactional
    public void transition(WorkKind k, long id, long version, String action, String comment) {
        var a = access.current();
        var w = find(k, id);
        String next;
        if (k == WorkKind.TASK) {
            a.require("task.write");
            if (w.ownerId() != a.id() && !a.has("task.manage"))
                throw new org.springframework.security.access.AccessDeniedException("denied");
            next =
                    switch (action) {
                        case "start" -> {
                            if (!w.status().equals("PLANNED")) throw new IllegalArgumentException();
                            yield "IN_PROGRESS";
                        }
                        case "complete" -> {
                            if (!Set.of("PLANNED", "IN_PROGRESS").contains(w.status()))
                                throw new IllegalArgumentException();
                            yield "COMPLETED";
                        }
                        case "cancel" -> {
                            a.require("task.manage");
                            if (!Set.of("PLANNED", "IN_PROGRESS").contains(w.status()))
                                throw new IllegalArgumentException();
                            yield "CANCELLED";
                        }
                        default -> throw new IllegalArgumentException();
                    };
        } else if (action.equals("submit")) {
            a.require(k.claim + ".write");
            if (w.ownerId() != a.id() || !Set.of("DRAFT", "REJECTED").contains(w.status()))
                throw new IllegalArgumentException();
            next = "SUBMITTED";
        } else {
            a.require(k.claim + ".review");
            if (w.ownerId() == a.id()
                    || !w.status().equals("SUBMITTED")
                    || !Set.of("approve", "reject").contains(action)
                    || comment == null
                    || comment.isBlank()
                    || comment.length() > 1000) throw new IllegalArgumentException();
            next = action.equals("approve") ? "APPROVED" : "REJECTED";
        }
        boolean review = Set.of("APPROVED", "REJECTED").contains(next);
        if (db.update(
                        "update work_items set"
                            + " status=?,review_comment=?,reviewed_by=?,reviewed_at=case when ?"
                            + " then current_timestamp else null"
                            + " end,version=version+1,updated_at=current_timestamp where id=? and"
                            + " version=?",
                        next,
                        comment == null ? "" : comment,
                        review ? a.id() : null,
                        review,
                        id,
                        version)
                != 1) throw new OptimisticLockingFailureException("stale");
        audit.record(a.id(), k.claim + "." + action, "work_items", id, next);
        if (k == WorkKind.LEAVE)
            mail.enqueue(
                    users.leaveRecipient(w.ownerId()),
                    "FCMS leave " + id + " - " + next,
                    "Leave " + id + ": " + next);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> participants(WorkKind k, long id) {
        find(k, id);
        return db.queryForList(
                "select u.first_name||' '||u.last_name from work_participants p join"
                        + " iam_staff_directory u on u.id=p.user_id where p.work_id=? order by"
                        + " u.first_name,u.id",
                String.class,
                id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filters(WorkKind kind, Long client) {
        var a = access.require(kind.claim + ".read");
        return Map.of(
                "filterStaff",
                a.has(kind.claim + ".read.all") ? users.staff("") : java.util.List.of(),
                "filterClients",
                kind == WorkKind.LEAVE ? java.util.List.of() : clients.saccos("", client));
    }

    @Transactional(readOnly = true)
    public long count(WorkKind k) {
        var a = access.require(k.claim + ".read");
        return db.queryForObject(
                "select count(*) from work_items w where kind=? and (? or owner_id=?"
                        + " or exists (select 1 from work_participants p where p.work_id=w.id"
                        + " and p.user_id=?) or (? and w.created_by=?))",
                Long.class,
                k.name(),
                a.has(k.claim + ".read.all"),
                a.id(),
                a.id(),
                k == WorkKind.TASK && a.has("task.manage"),
                a.id());
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkView> report(WorkKind kind, RegisterQuery query) {
        access.require(kind.claim + ".read");
        int original = query.getPage();
        int size = query.getSize();
        try {
            query.setSize(100);
            var rows = new java.util.ArrayList<WorkView>();
            for (int p = 0; p <= 10; p++) {
                query.setPage(p);
                var page = list(kind, query);
                rows.addAll(page.rows());
                if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
                if (!page.hasNext()) return java.util.List.copyOf(rows);
            }
            throw new IllegalArgumentException("error.narrowReport");
        } finally {
            query.setPage(original);
            query.setSize(size);
        }
    }
}
