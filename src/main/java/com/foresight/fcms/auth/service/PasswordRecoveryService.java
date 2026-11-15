package com.foresight.fcms.auth.service;

import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.notifications.service.MailOutboxService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PasswordRecoveryService {
    private final SessionRevocationService sessions;
    private final JdbcTemplate db;
    private final MailOutboxService mail;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final org.springframework.context.MessageSource messages;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    public PasswordRecoveryService(
            SessionRevocationService sessions,
            JdbcTemplate db,
            MailOutboxService mail,
            PasswordEncoder passwords,
            AuditService audit,
            org.springframework.context.MessageSource messages) {
        this.sessions = sessions;
        this.db = db;
        this.mail = mail;
        this.passwords = passwords;
        this.audit = audit;
        this.messages = messages;
    }

    @Transactional
    public void request(String email) {
        request(email, Locale.ENGLISH);
    }

    @Transactional
    public void request(String email, Locale locale) {
        var ids =
                db.queryForList(
                        "select id from users where lower(email)=lower(?) and status_id=1",
                        Long.class,
                        email);
        if (ids.isEmpty()) return;
        long id = ids.getFirst();
        db.queryForList("select id from users where id=? for update", id);
        var recent =
                db.queryForObject(
                        "select count(*) from password_challenges where user_id=? and"
                                + " created_at>current_timestamp-interval '15 minutes'",
                        Long.class,
                        id);
        if (recent >= 3) return;
        db.update(
                "update password_challenges set used_at=current_timestamp where user_id=? and"
                        + " used_at is null",
                id);
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        String otp = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1000000));
        db.update(
                "insert into password_challenges(id,user_id,token_hash,otp_hash,expires_at) values"
                        + " (?,?,?,?,current_timestamp+interval '15 minutes')",
                UUID.randomUUID(),
                id,
                hash(token),
                passwords.encode(otp));
        mail.enqueue(
                email,
                messages.getMessage("mail.recovery.subject", null, language(locale)),
                messages.getMessage(
                        "mail.recovery.body", new Object[] {token, otp}, language(locale)));
        audit.record(id, "password.request", "users", id, "Recovery requested");
    }

    private static Locale language(Locale locale) {
        return locale != null && "sw".equals(locale.getLanguage())
                ? Locale.forLanguageTag("sw")
                : Locale.ENGLISH;
    }

    @Transactional
    public boolean reset(String token, String otp, String password) {
        if (password == null || password.length() < 12 || password.length() > 72)
            throw new IllegalArgumentException("error.password");
        var rows =
                db.queryForList(
                        "select id,user_id,otp_hash from password_challenges where token_hash=? and"
                            + " used_at is null and expires_at>current_timestamp and attempts<5 for"
                            + " update",
                        hash(token));
        if (rows.isEmpty()) return false;
        var c = rows.getFirst();
        db.update("update password_challenges set attempts=attempts+1 where id=?", c.get("id"));
        if (!passwords.matches(otp, (String) c.get("otp_hash"))) return false;
        long id = ((Number) c.get("user_id")).longValue();
        if (db.update(
                        "update users set password=?,version=version+1 where id=? and status_id=1",
                        passwords.encode(password),
                        id)
                != 1) return false;
        db.update(
                "update password_challenges set used_at=current_timestamp where user_id=? and"
                        + " used_at is null",
                id);
        sessions.revoke(db.queryForObject("select email from users where id=?", String.class, id));
        audit.record(id, "password.reset", "users", id, "Password reset completed");
        return true;
    }

    private static String hash(String v) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
