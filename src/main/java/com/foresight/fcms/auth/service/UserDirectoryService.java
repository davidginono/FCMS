package com.foresight.fcms.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserDirectoryService {
    private final JdbcTemplate db;
    private final AccessService access;

    public UserDirectoryService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Choice> staff(String search) {
        var a = access.current();
        if (!(a.has("task.manage")
                || a.has("expense.assign")
                || a.has("staff-report.assign")
                || a.has("iam.user.manage")
                || a.has("staff-report.write")
                || a.has("task.read.all")
                || a.has("leave.read.all")
                || a.has("staff-report.read.all")))
            return db.query(
                    "select id,first_name||' '||last_name from users where id=?",
                    (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                    a.id());
        return db.query(
                "select id,first_name||' '||last_name from users where status_id=1 and"
                    + " lower(first_name||' '||last_name) like ? order by first_name,id limit 50",
                (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                "%" + (search == null ? "" : search.toLowerCase(java.util.Locale.ROOT)) + "%");
    }

    @Transactional(readOnly = true)
    public List<Choice> selected(java.util.Collection<Long> ids) {
        var a = access.current();
        if (!(a.has("task.manage")
                || a.has("staff-report.write")
                || a.has("expense.assign")
                || a.has("staff-report.assign")
                || a.has("task.read.all")
                || a.has("leave.read.all")
                || a.has("staff-report.read.all")))
            return staff("").stream().filter(c -> ids.contains(c.id())).toList();
        if (ids.size() > 21 || ids.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException();
        if (ids.isEmpty()) return List.of();
        return db.query(
                "select id,first_name||' '||last_name from users where id=any (?) order by"
                        + " first_name,id",
                (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                (Object) ids.toArray(Long[]::new));
    }

    @Transactional(readOnly = true)
    public void requireActive(java.util.Collection<Long> ids) {
        var a = access.current();
        if (!a.has("task.manage")
                && !a.has("staff-report.write")
                && !a.has("expense.assign")
                && !a.has("staff-report.assign"))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        if (ids.size() > 20 || ids.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException();
        var distinct = new java.util.HashSet<>(ids);
        if (distinct.isEmpty()) return;
        Long active =
                db.queryForObject(
                        "select count(*) from users where status_id=1 and id = any (?)",
                        Long.class,
                        (Object) distinct.toArray(Long[]::new));
        if (active == null || active != distinct.size()) throw new IllegalArgumentException();
    }

    @Transactional(readOnly = true)
    public String leaveRecipient(long owner) {
        var a = access.current();
        if (!(owner == a.id() && a.has("leave.write")) && !a.has("leave.review"))
            throw new org.springframework.security.access.AccessDeniedException("denied");
        return db.queryForObject(
                "select coalesce(nullif(leave_email,''),email) from users where id=?",
                String.class,
                owner);
    }

    @Transactional(readOnly = true)
    public com.foresight.fcms.shared.dto.ChoicePage<Choice> search(String search, int page) {
        var a = access.current();
        if (page < 0 || page > 1000 || search.length() > 100) throw new IllegalArgumentException();
        if (!(a.has("task.manage")
                || a.has("expense.assign")
                || a.has("staff-report.assign")
                || a.has("iam.user.manage")
                || a.has("staff-report.write")
                || a.has("task.read.all")
                || a.has("leave.read.all")
                || a.has("staff-report.read.all")))
            return com.foresight.fcms.shared.dto.ChoicePage.from(
                    page == 0 ? staff(search) : java.util.List.of(), page);
        var rows =
                db.query(
                        "select id,first_name||' '||last_name from users where status_id=1 and"
                                + " lower(first_name||' '||last_name) like ? order by first_name,id"
                                + " limit 26 offset ?",
                        (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                        "%" + search.toLowerCase(java.util.Locale.ROOT) + "%",
                        page * 25);
        return com.foresight.fcms.shared.dto.ChoicePage.from(rows, page);
    }

    public record Choice(long id, String name) {}
}
