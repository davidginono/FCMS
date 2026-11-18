package com.foresight.fcms.auth.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.dto.*;
import com.foresight.fcms.auth.service.UserPermissionService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@org.springframework.validation.annotation.Validated
@Service
public class UserAdministrationService {
    private final SessionRevocationService sessions;
    private final JdbcTemplate db;
    private final AccessService access;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final UserPermissionService permissions;

    public UserAdministrationService(
            SessionRevocationService sessions,
            JdbcTemplate db,
            AccessService access,
            AuditService audit,
            PasswordEncoder encoder,
            UserPermissionService permissions) {
        this.sessions = sessions;
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.encoder = encoder;
        this.permissions = permissions;
    }

    private static final String SELECT =
            "select"
                + " u.id,u.first_name,u.last_name,u.email,u.role_id,r.role_name,u.status_id,s.status_name,u.version"
                + " from users u join roles r on r.role_id=u.role_id join account_status s on"
                + " s.id=u.status_id";

    private UserView row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new UserView(
                r.getLong(1),
                r.getString(2),
                r.getString(3),
                r.getString(4),
                r.getLong(5),
                r.getString(6),
                r.getLong(7),
                r.getString(8),
                r.getLong(9));
    }

    @Transactional(readOnly = true)
    public RegisterPage<UserView> list(RegisterQuery q) {
        access.require("iam.user.read");
        return RegisterPage.of(
                db.query(
                        SELECT
                                + " where lower(u.first_name||' '||u.last_name||' '||u.email) like"
                                + " ? escape '!' and (?='' or s.status_name=?) order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "u.id",
                                                "name",
                                                "u.first_name",
                                                "email",
                                                "u.email",
                                                "status",
                                                "s.status_name",
                                                "role",
                                                "r.role_name"))
                                + " limit ? offset ?",
                        this::row,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public UserView find(long id) {
        access.require("iam.user.read");
        return db.query(SELECT + " where u.id=?", this::row, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public @jakarta.validation.Valid UserForm form(long id) {
        var u = find(id);
        var f = new UserForm();
        f.setFirstName(u.firstName());
        f.setLastName(u.lastName());
        f.setEmail(u.email());
        f.setRoleId(u.roleId());
        f.setStatusId(u.statusId());
        f.setVersion(u.version());
        f.setClaims(new ArrayList<>(permissions.find(id).selected().stream().sorted().toList()));
        f.setPermissionsSubmitted(true);
        return f;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> options() {
        access.require("iam.user.manage");
        return Map.of(
                "roles",
                db.queryForList(
                        "select r.role_id as id,r.role_name as name,"
                                + " coalesce(string_agg(rc.claim,' ' order by rc.claim),'') as defaults"
                                + " from roles r left join role_claims rc on rc.role_id=r.role_id"
                                + " group by r.role_id,r.role_name order by case r.role_name"
                                + " when 'Accounts Clerk' then 1 when 'Staff' then 2"
                                + " when 'Administrator' then 3 when 'Director' then 4 else 5 end,"
                                + " r.role_id limit 100"),
                "statuses",
                db.queryForList(
                        "select id,status_name as name from account_status order by id limit 100"),
                "availableClaims",
                db.queryForList("select name from claims order by name limit 100", String.class));
    }

    @Transactional
    public long save(Long id, @jakarta.validation.Valid UserForm f) {
        var a = access.require("iam.user.manage");
        // Serializes administrator changes so concurrent edits cannot disable every administrator.
        db.queryForList("select role_id from roles order by role_id for update");
        if (db.queryForObject(
                                "select count(*) from roles where role_id=?",
                                Long.class,
                                f.getRoleId())
                        != 1
                ) throw new FieldValidationException("error.invalid", "roleId");
        if (db.queryForObject(
                                "select count(*) from account_status where id=?",
                                Long.class,
                                f.getStatusId())
                        != 1) throw new FieldValidationException("error.invalid", "statusId");
        Set<String> wanted = new HashSet<>(f.getClaims());
        var known = new HashSet<>(db.queryForList("select name from claims", String.class));
        if (!known.containsAll(wanted)) throw new FieldValidationException("error.invalid", "claims");
        var before = id == null
                ? new UserPermissionService.Permissions(Set.of(), Set.of())
                : permissions.find(id);
        var grants = new HashSet<>(wanted);
        var denied = new HashSet<String>();
        if (f.isPermissionsSubmitted()) {
            var defaults = new HashSet<>(db.queryForList(
                    "select claim from role_claims where role_id=?", String.class, f.getRoleId()));
            grants.removeAll(defaults);
            denied.addAll(defaults);
            denied.removeAll(wanted);
        }
        if (id == null) {
            if (f.getPassword() == null || f.getPassword().length() < 12)
                throw new FieldValidationException("error.password", "password");
            id =
                    db.queryForObject(
                            "insert into"
                                + " users(first_name,last_name,email,password,role_id,status_id)"
                                + " values (?,?,?,?,?,?) returning id",
                            Long.class,
                            f.getFirstName().trim(),
                            f.getLastName().trim(),
                            f.getEmail().trim().toLowerCase(Locale.ROOT),
                            encoder.encode(f.getPassword()),
                            f.getRoleId(),
                            f.getStatusId());
        } else {
            if (id == a.id() && f.getStatusId() != 1L)
                throw new FieldValidationException("error.selfDisable", "statusId");
            String previousEmail =
                    db.queryForObject("select email from users where id=?", String.class, id);
            sessions.revoke(previousEmail);
            int changed =
                    db.update(
                            "update users set"
                                + " first_name=?,last_name=?,email=?,role_id=?,status_id=?,version=version+1"
                                + " where id=? and version=?",
                            f.getFirstName().trim(),
                            f.getLastName().trim(),
                            f.getEmail().trim().toLowerCase(Locale.ROOT),
                            f.getRoleId(),
                            f.getStatusId(),
                            id,
                            f.getVersion());
            if (changed != 1) throw new OptimisticLockingFailureException("stale");
            if (f.getPassword() != null && !f.getPassword().isBlank()) {
                if (f.getPassword().length() < 12)
                    throw new IllegalArgumentException("error.password");
                db.update(
                        "update users set password=? where id=?",
                        encoder.encode(f.getPassword()),
                        id);
            }
        }
        db.update("delete from user_claims where user_id=?", id);
        for (String claim : grants)
            db.update("insert into user_claims(user_id,claim) values (?,?)", id, claim);
        // Older open forms and internal grant-only callers must not silently reset exclusions.
        if (f.isPermissionsSubmitted()) {
            db.update("delete from user_claim_denials where user_id=?", id);
            for (String claim : denied)
                db.update("insert into user_claim_denials(user_id,claim) values (?,?)", id, claim);
        }
        long managers =
                db.queryForObject(
                        "select count(*) from users u where u.status_id=1 and not exists("
                            + " select 1 from (values ('workspace.admin'),('iam.user.read'),"
                            + " ('iam.user.manage')) needed(claim) where"
                            + " (not exists(select 1 from role_claims r where r.role_id=u.role_id"
                            + " and r.claim=needed.claim) and not exists(select 1 from user_claims c"
                            + " where c.user_id=u.id and c.claim=needed.claim)) or exists("
                            + " select 1 from user_claim_denials d where d.user_id=u.id"
                            + " and d.claim=needed.claim))",
                        Long.class);
        if (managers == 0) throw new IllegalArgumentException("error.lastAdmin");
        var after = permissions.find(id);
        var changedClaims = new TreeSet<>(before.assigned());
        changedClaims.addAll(before.denied());
        changedClaims.addAll(after.assigned());
        changedClaims.addAll(after.denied());
        var previousSelection = before.selected();
        var currentSelection = after.selected();
        for (String claim : changedClaims) {
            boolean wasSelected = previousSelection.contains(claim), isSelected = currentSelection.contains(claim);
            boolean wasDenied = before.denied().contains(claim), isDenied = after.denied().contains(claim);
            if (wasSelected != isSelected || wasDenied != isDenied)
                audit.record(a.id(), "iam.user.permission.change", "users", id,
                        claim + ": selected=" + wasSelected + ", excluded=" + wasDenied
                                + " -> selected=" + isSelected + ", excluded=" + isDenied);
        }
        audit.record(
                a.id(),
                "iam.user.save",
                "users",
                id,
                "Account and effective claim bundles updated");
        return id;
    }
}
