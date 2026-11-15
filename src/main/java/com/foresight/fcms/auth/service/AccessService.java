package com.foresight.fcms.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class AccessService {
    private final JdbcTemplate db;

    public AccessService(JdbcTemplate db) {
        this.db = db;
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
        Set<String> claims =
                new HashSet<>(
                        db.queryForList(
                                "select rc.claim from users u join role_claims rc on"
                                    + " rc.role_id=u.role_id where u.id=? union select claim from"
                                    + " user_claims where user_id=?",
                                String.class,
                                id,
                                id));
        return new Actor(id, Set.copyOf(claims));
    }

    public Actor require(String claim) {
        var a = current();
        a.require(claim);
        return a;
    }

    public record Actor(long id, Set<String> claims) {
        public boolean has(String c) {
            return claims.contains(c);
        }

        public void require(String c) {
            if (!has(c)) throw new AccessDeniedException("Access denied");
        }
    }
}
