package com.foresight.fcms.auth.service;

import com.foresight.fcms.auth.service.UserPermissionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AccessService {
    private final JdbcTemplate db;
    private final UserPermissionService permissions;

    public AccessService(JdbcTemplate db, UserPermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public Actor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AccessDeniedException("Access denied");
        var ids =
                db.query(
                        "select u.id from users u join account_status s on s.id=u.status_id where"
                                + " lower(u.email)=lower(?) and lower(s.status_name)='active'",
                        (r, n) -> r.getLong(1),
                        auth.getName());
        if (ids.isEmpty()) throw new AccessDeniedException("Access denied");
        long id = ids.getFirst();
        return new Actor(id, permissions.find(id).effective());
    }

    public Actor require(String claim) {
        var a = current();
        a.require(claim);
        return a;
    }

    public record Actor(long id, Set<String> claims) {
        public Actor {
            claims = Set.copyOf(claims);
        }

        public boolean has(String c) {
            return claims.contains(c);
        }

        public void require(String c) {
            if (!has(c)) throw new AccessDeniedException("Access denied");
        }
    }
}
