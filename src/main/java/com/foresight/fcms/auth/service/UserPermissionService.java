package com.foresight.fcms.auth.service;

import com.foresight.fcms.auth.WorkspaceClaims;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/** One permission calculation for authentication, request authorization and account editing. */
@Service
@Transactional(readOnly = true)
public class UserPermissionService {
    private final JdbcTemplate db;

    public UserPermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public Permissions find(long userId) {
        return db.query(
                "select rc.claim,false as denied from users u join role_claims rc on"
                        + " rc.role_id=u.role_id where u.id=? union select claim,false from"
                        + " user_claims where user_id=? union select claim,true from"
                        + " user_claim_denials where user_id=?",
                result -> {
                    var assigned = new HashSet<String>();
                    var denied = new HashSet<String>();
                    while (result.next()) {
                        (result.getBoolean(2) ? denied : assigned).add(result.getString(1));
                    }
                    return new Permissions(assigned, denied);
                },
                userId, userId, userId);
    }

    public record Permissions(Set<String> assigned, Set<String> denied) {
        public Permissions {
            assigned = Set.copyOf(assigned);
            denied = Set.copyOf(denied);
        }

        public Set<String> selected() {
            var selected = new HashSet<>(assigned);
            selected.removeAll(denied);
            return Set.copyOf(selected);
        }

        public Set<String> effective() {
            var effective = new HashSet<>(WorkspaceClaims.effective(selected()));
            // Workspace fallback must not reintroduce an explicitly unchecked permission.
            effective.removeAll(denied);
            return Set.copyOf(effective);
        }
    }
}
