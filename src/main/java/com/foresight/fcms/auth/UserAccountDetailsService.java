package com.foresight.fcms.auth;

import com.foresight.fcms.auth.service.UserPermissionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private final JdbcTemplate db;
    private final UserPermissionService permissions;

    public UserAccountDetailsService(JdbcTemplate db, UserPermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var rows =
                db.query(
                        "select u.id,u.email,u.password,s.status_name from users u join"
                            + " account_status s on s.id=u.status_id where lower(u.email)=lower(?)",
                        (r, n) ->
                                new Login(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        "active".equalsIgnoreCase(r.getString(4))),
                        email);
        if (rows.isEmpty()) throw new UsernameNotFoundException("User not found");
        var a = rows.getFirst();
        return User.withUsername(a.email())
                .password(a.password())
                .authorities(permissions.find(a.id()).effective().toArray(String[]::new))
                .disabled(!a.active())
                .build();
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String password) {
        db.update(
                "update users set password=?,version=version+1 where lower(email)=lower(?)",
                password,
                user.getUsername());
        return loadUserByUsername(user.getUsername());
    }

    private record Login(long id, String email, String password, boolean active) {}
}
