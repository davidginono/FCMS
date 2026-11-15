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
                + " client,coalesce(m.first_name||' '||m.last_name,'') as"
                + " creator,coalesce(p.first_name||' '||p.last_name,'') as preparer from work_items"
                + " w join iam_staff_directory u on u.id=w.owner_id left join"
                + " client_saccos_directory c on c.id=w.client_id left join iam_staff_directory m"
                + " on m.id=w.created_by left join iam_staff_directory p on p.id=w.prepared_by";

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
                r.getLong("version"),
                r.getLong("created_by"),
                r.getString("creator"),
                r.getObject("prepared_by", Long.class),
                r.getString("preparer"),
                null);
    }

    @Transactional(readOnly = true)
    public RegisterPage<WorkView> list(WorkKind k, RegisterQuery q) {
        var a = access.require(k.claim + ".read");
        q.validate();
        boolean created =
                q instanceof WorkQuery workQuery && workQuery.getDateBasis().equals("CREATED");
        if (created && k != WorkKind.TASK) throw new IllegalArgumentException("error.invalid");
        String dateColumn =
                created ? "(w.created_at at time zone 'Africa/Dar_es_Salaam')" : "w.start_at";
        boolean all = a.has(k.claim + ".read.all");
        String sql =
                SELECT
                        + " where w.kind=? and (? or w.owner_id=? or exists(select 1 from"
                        + " work_participants p where p.work_id=w.id and p.user_id=?) or (? and"
                        + " w.created_by=?)) and (?::bigint is null or w.owner_id=?::bigint or (?"
                        + " and exists(select 1 from work_participants selected where"
                        + " selected.work_id=w.id and selected.user_id=?::bigint))) and (?::bigint"
                        + " is null or w.client_id=?::bigint) and lower(w.subject||'"
                        + " '||w.description) like ? escape '!' and (?='' or w.status=?) and"
                        + " (?::date is null or "
                        + dateColumn
                        + ">=?::date) and (?::date is null or "
                        + dateColumn
                        + "<(?::date+1)) order by "
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
                        canAssign(a, k),
                        a.id(),
                        q.getOwnerId(),
                        q.getOwnerId(),
                        k == WorkKind.REPORT,
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
                        canAssign(a, k),
                        a.id())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> options(WorkKind k, WorkForm form) {
        var actor = access.require(k.claim + ".write");
        if (form.getOwnerId() == null) form.setOwnerId(actor.id());
        var selected = new HashSet<Long>();
        if (form.getParticipants() != null)
            form.getParticipants().stream()
                    .filter(Objects::nonNull)
                    .limit(20)
                    .forEach(selected::add);
        if (form.getOwnerId() != null) selected.add(form.getOwnerId());
        var known = users.selected(selected);
        var choices = new LinkedHashMap<Long, UserDirectoryService.Choice>();
        users.staff("").forEach(c -> choices.put(c.id(), c));
        known.forEach(c -> choices.put(c.id(), c));
        return Map.of(
                "staff",
                List.copyOf(choices.values()),
                "selectedStaff",
                known.stream()
                        .filter(c ->
                                form.getParticipants() != null
                                        && form.getParticipants().contains(c.id()))
                        .toList(),
                "clients",
                k == WorkKind.LEAVE || !actor.has("client.read")
                        ? List.of()
                        : clients.saccos("", form.getClientId()));
    }

    @Transactional(readOnly = true)
    public @jakarta.validation.Valid WorkForm form(WorkKind k, long id) {
        var actor = access.require(k.claim + ".write");
        var w = find(k, id);
        if (!mayPrepare(actor, k, w))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (!Set.of("DRAFT", "REJECTED", "PLANNED", "IN_PROGRESS").contains(w.status()))
            throw new IllegalArgumentException("error.invalid");
        return retainedForm(w);
    }

    private WorkForm retainedForm(WorkView w) {
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
                        "select user_id from work_participants where work_id=? order by user_id"
                                + " limit 21",
                        Long.class,
                        w.id()));
        if (f.getParticipants().size() > 20)
            throw new IllegalArgumentException("error.narrowReport");
        return f;
    }

    private boolean canAssign(AccessService.Actor actor, WorkKind kind) {
        return kind == WorkKind.TASK && actor.has("task.manage")
                || kind == WorkKind.REPORT && actor.has("staff-report.assign");
    }

    private boolean mayPrepare(AccessService.Actor actor, WorkKind kind, WorkView record) {
        return record.ownerId() == actor.id()
                || kind == WorkKind.TASK && actor.has("task.manage")
                || kind == WorkKind.REPORT
                        && actor.has("staff-report.assign")
                        && (record.createdBy() == actor.id() || actor.has("staff-report.read.all"));
    }

    private boolean involved(AccessService.Actor actor, WorkView record) {
        return record.ownerId() == actor.id()
                || record.createdBy() == actor.id()
                || Objects.equals(record.preparedBy(), actor.id())
                || record.kind().equals("REPORT")
                        && Boolean.TRUE.equals(
                                db.queryForObject(
                                        "select exists(select 1 from work_participants where"
                                                + " work_id=? and user_id=?)",
                                        Boolean.class,
                                        record.id(),
                                        actor.id()));
    }

    private String creationHash(WorkKind kind, WorkForm form, long owner, Set<Long> team) {
        return com.foresight.fcms.shared.command.CommandFingerprint.of(
                kind.name(),
                Long.toString(owner),
                Objects.toString(form.getClientId(), ""),
                form.getSubject().trim(),
                form.getDescription(),
                form.getStartAt().toString(),
                form.getEndAt().toString(),
                kind == WorkKind.REPORT
                                && (form.getWorkType() == null || form.getWorkType().isBlank())
                        ? "INDIVIDUAL"
                        : Objects.toString(form.getWorkType(), ""),
                form.getOutcome(),
                form.getExplanation(),
                form.getAllowance().stripTrailingZeros().toPlainString(),
                form.getAmount().stripTrailingZeros().toPlainString(),
                new TreeSet<>(team).toString());
    }

    @Transactional
    public long save(WorkKind kind, Long id, @jakarta.validation.Valid WorkForm form) {
        var actor = access.require(kind.claim + ".write");
        WorkView existing = id == null ? null : find(kind, id);
        if (existing != null && !mayPrepare(actor, kind, existing))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (existing != null
                && !Set.of("DRAFT", "REJECTED", "PLANNED", "IN_PROGRESS")
                        .contains(existing.status()))
            throw new IllegalArgumentException("error.invalid");
        if (form.getEndAt().isBefore(form.getStartAt()))
            throw new IllegalArgumentException("error.dates");
        long owner =
                canAssign(actor, kind) && form.getOwnerId() != null
                        ? form.getOwnerId()
                        : actor.id();
        if (existing != null && !canAssign(actor, kind)) owner = existing.ownerId();
        if (kind == WorkKind.REPORT && form.getOwnerId() != null && form.getOwnerId() != owner)
            actor.require("staff-report.assign");
        if (canAssign(actor, kind)) users.requireActive(List.of(owner));
        if (form.getClientId() != null) clients.requireSaccos(form.getClientId());
        if (kind == WorkKind.TASK && form.getClientId() == null)
            throw new IllegalArgumentException("error.invalid");
        if (kind == WorkKind.REPORT
                && "INCOMPLETE".equals(form.getOutcome())
                && form.getExplanation().isBlank())
            throw new IllegalArgumentException("error.invalid");
        if (!Set.of("", "COMPLETED", "INCOMPLETE", "IN_PROGRESS").contains(form.getOutcome()))
            throw new IllegalArgumentException("error.invalid");
        if (kind == WorkKind.REPORT) {
            if (form.getWorkType() == null || form.getWorkType().isBlank())
                form.setWorkType("INDIVIDUAL");
            if (!Set.of("INDIVIDUAL", "GROUP").contains(form.getWorkType()))
                throw new IllegalArgumentException("error.invalid");
        }
        Set<Long> team = new HashSet<>(form.getParticipants());
        if (kind == WorkKind.REPORT && form.getWorkType().equals("GROUP")) {
            team.add(owner);
            if (team.size() < 2) throw new IllegalArgumentException("error.invalid");
            users.requireActive(team);
        } else team.clear();
        if (id == null) {
            String hash = creationHash(kind, form, owner, team);
            db.query(
                    "select pg_advisory_xact_lock(hashtextextended(?,0))",
                    (r, n) -> 0,
                    "fcms.work-create:"
                            + kind.name()
                            + ":"
                            + actor.id()
                            + ":"
                            + form.getRequestKey());
            var retained =
                    db.query(
                            "select id,creation_hash from work_items where created_by=? and kind=?"
                                    + " and request_key=?",
                            (r, n) -> new Object[] {r.getLong(1), r.getString(2)},
                            actor.id(),
                            kind.name(),
                            form.getRequestKey());
            if (!retained.isEmpty()) {
                var previous = retained.getFirst();
                if (previous[1] != null && hash.equals(previous[1])) return (long) previous[0];
                if (previous[1] == null) {
                    var old = find(kind, (long) previous[0]);
                    var oldForm = retainedForm(old);
                    if (hash.equals(
                            creationHash(
                                    kind,
                                    oldForm,
                                    old.ownerId(),
                                    new HashSet<>(oldForm.getParticipants())))) return old.id();
                }
                throw new IllegalArgumentException("work.requestConflict");
            }
            id =
                    db.queryForObject(
                            "insert into"
                                + " work_items(kind,owner_id,client_id,subject,description,start_at,end_at,work_type,outcome,explanation,allowance,amount,status,created_by,request_key,prepared_by,creation_hash)"
                                + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id",
                            Long.class,
                            kind.name(),
                            owner,
                            form.getClientId(),
                            form.getSubject().trim(),
                            form.getDescription(),
                            form.getStartAt(),
                            form.getEndAt(),
                            form.getWorkType(),
                            form.getOutcome(),
                            form.getExplanation(),
                            form.getAllowance(),
                            form.getAmount(),
                            kind == WorkKind.TASK ? "PLANNED" : "DRAFT",
                            actor.id(),
                            form.getRequestKey(),
                            actor.id(),
                            hash);
        } else if (db.update(
                        "update work_items set"
                            + " owner_id=?,client_id=?,subject=?,description=?,start_at=?,end_at=?,work_type=?,outcome=?,explanation=?,allowance=?,amount=?,prepared_by=?,version=version+1,updated_at=current_timestamp"
                            + " where id=? and kind=? and version=?",
                        owner,
                        form.getClientId(),
                        form.getSubject().trim(),
                        form.getDescription(),
                        form.getStartAt(),
                        form.getEndAt(),
                        form.getWorkType(),
                        form.getOutcome(),
                        form.getExplanation(),
                        form.getAllowance(),
                        form.getAmount(),
                        actor.id(),
                        id,
                        kind.name(),
                        form.getVersion())
                != 1) throw new OptimisticLockingFailureException("stale");
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
        audit.record(
                actor.id(),
                kind.claim + ".save",
                "work_items",
                id,
                kind.name()
                        + "; beforeOwner="
                        + (existing == null ? "" : existing.ownerId())
                        + "; owner="
                        + owner);
        return id;
    }

    @Transactional
    public void transition(WorkKind k, long id, long version, String action, String comment) {
        var a = access.current();
        if (comment != null && comment.length() > 1000)
            throw new IllegalArgumentException("error.invalid");
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
        } else if (k == WorkKind.REPORT && action.equals("cancel")) {
            a.require("staff-report.write");
            if (!mayPrepare(a, k, w)
                    || !Set.of("DRAFT", "REJECTED", "SUBMITTED").contains(w.status())
                    || comment == null
                    || comment.isBlank()
                    || comment.length() > 1000) throw new IllegalArgumentException("error.invalid");
            next = "CANCELLED";
        } else if (action.equals("submit")) {
            a.require(k.claim + ".write");
            if (!mayPrepare(a, k, w) || !Set.of("DRAFT", "REJECTED").contains(w.status()))
                throw new IllegalArgumentException();
            next = "SUBMITTED";
        } else {
            a.require(k.claim + ".review");
            if (involved(a, w)
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
        audit.record(
                a.id(),
                k.claim + "." + action,
                "work_items",
                id,
                "before="
                        + w.status()
                        + "; after="
                        + next
                        + "; reason="
                        + (comment == null ? "" : comment)
                        + "; priorReview="
                        + w.reviewComment());
        if (k == WorkKind.LEAVE)
            mail.enqueue(
                    users.leaveRecipient(w.ownerId()),
                    "FCMS leave " + id + " - " + next,
                    "Leave " + id + ": " + next);
    }

    public record Commands(boolean prepare, boolean submit, boolean review, boolean cancel) {}

    public record Details(WorkView record, List<String> participants, Commands commands) {}

    private Commands commands(AccessService.Actor actor, WorkKind kind, WorkView record) {
        boolean editable =
                Set.of("DRAFT", "REJECTED", "PLANNED", "IN_PROGRESS").contains(record.status());
        boolean prepare =
                actor.has(kind.claim + ".write") && mayPrepare(actor, kind, record) && editable;
        return new Commands(
                prepare,
                prepare && kind != WorkKind.TASK,
                kind != WorkKind.TASK
                        && actor.has(kind.claim + ".review")
                        && record.status().equals("SUBMITTED")
                        && !involved(actor, record),
                kind == WorkKind.REPORT
                        && actor.has("staff-report.write")
                        && mayPrepare(actor, kind, record)
                        && Set.of("DRAFT", "REJECTED", "SUBMITTED").contains(record.status()));
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Details details(WorkKind kind, long id) {
        var record = find(kind, id);
        return new Details(
                record,
                participantNames(id),
                commands(access.require(kind.claim + ".read"), kind, record));
    }

    @Transactional(readOnly = true)
    public java.util.List<String> participants(WorkKind kind, long id) {
        find(kind, id);
        return participantNames(id);
    }

    private List<String> participantNames(long id) {
        var names =
                db.queryForList(
                        "select u.first_name||' '||u.last_name from work_participants p join"
                            + " iam_staff_directory u on u.id=p.user_id where p.work_id=? order by"
                            + " u.first_name,u.id limit 21",
                        String.class,
                        id);
        if (names.size() > 20) throw new IllegalArgumentException("error.narrowReport");
        return List.copyOf(names);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filters(WorkKind kind, RegisterQuery query) {
        var actor = access.require(kind.claim + ".read");
        var staff = new LinkedHashMap<Long, UserDirectoryService.Choice>();
        if (actor.has(kind.claim + ".read.all")) {
            users.staff("").forEach(c -> staff.put(c.id(), c));
            if (query.getOwnerId() != null)
                users.selected(Set.of(query.getOwnerId())).forEach(c -> staff.put(c.id(), c));
        }
        return Map.of(
                "filterStaff",
                List.copyOf(staff.values()),
                "filterClients",
                kind == WorkKind.LEAVE || !actor.has("client.read")
                        ? List.of()
                        : clients.saccos("", query.getClientId()));
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
                canAssign(a, k),
                a.id());
    }

    private record Member(long workId, long staffId, String name) {}

    private List<WorkView> activityRows(List<WorkView> records, Long selectedStaff) {
        if (records.isEmpty()) return List.of();
        var members =
                db.query(
                        "select p.work_id,u.id,u.first_name||' '||u.last_name from"
                            + " work_participants p join iam_staff_directory u on u.id=p.user_id"
                            + " where p.work_id=any (?) and (?::bigint is null or"
                            + " p.user_id=?::bigint) order by p.work_id,u.first_name,u.id limit"
                            + " 1001",
                        (r, n) -> new Member(r.getLong(1), r.getLong(2), r.getString(3)),
                        (Object) records.stream().map(WorkView::id).toArray(Long[]::new),
                        selectedStaff,
                        selectedStaff);
        if (members.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        var byWork = new HashMap<Long, List<Member>>();
        for (var member : members)
            byWork.computeIfAbsent(member.workId(), id -> new ArrayList<>()).add(member);
        var rows = new ArrayList<WorkView>();
        for (var record : records) {
            var team =
                    record.workType().equals("GROUP")
                            ? byWork.getOrDefault(record.id(), List.of())
                            : List.<Member>of();
            if (!team.isEmpty())
                for (var member : team) rows.add(record.forActivityStaff(member.name()));
            else if (selectedStaff == null || record.ownerId() == selectedStaff) rows.add(record);
            if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        }
        return List.copyOf(rows);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
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
                if (!page.hasNext())
                    return kind == WorkKind.REPORT
                            ? activityRows(rows, query.getOwnerId())
                            : java.util.List.copyOf(rows);
            }
            throw new IllegalArgumentException("error.narrowReport");
        } finally {
            query.setPage(original);
            query.setSize(size);
        }
    }
}
