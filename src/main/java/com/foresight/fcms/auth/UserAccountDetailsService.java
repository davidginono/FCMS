package com.foresight.fcms.auth;

import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public UserAccountDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserAccount account = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String role = normalizeRole(account.getRole().getRoleName());
        boolean active = account.getStatus() != null && account.getStatus().isActive();

        return User.withUsername(account.getEmail())
            .password(account.getPassword())
            .authorities(new SimpleGrantedAuthority("ROLE_" + role))
            .disabled(!active)
            .build();
    }

    private String normalizeRole(String roleName) {
        String value = roleName == null ? "USER" : roleName.trim().toUpperCase(Locale.ROOT);
        if ("USERS".equals(value)) {
            return "USER";
        }
        return value;
    }
}
