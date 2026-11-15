package com.foresight.fcms.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class LegacyAwarePasswordEncoder implements PasswordEncoder {

    public static final String DEVELOPMENT_PASSWORD = "ChangeMe123!";
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final boolean production;

    public LegacyAwarePasswordEncoder() {
        this(false);
    }

    public LegacyAwarePasswordEncoder(boolean production) {
        this.production = production;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (production && rawPassword != null && DEVELOPMENT_PASSWORD.contentEquals(rawPassword)) {
            throw new IllegalArgumentException("error.passwordDefault");
        }
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        if (production && DEVELOPMENT_PASSWORD.contentEquals(rawPassword)) {
            return false;
        }
        if (looksLikeBcrypt(encodedPassword)) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }

        return rawPassword.toString().equals(encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword == null
                || !looksLikeBcrypt(encodedPassword)
                || bcrypt.upgradeEncoding(encodedPassword);
    }

    private boolean looksLikeBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
