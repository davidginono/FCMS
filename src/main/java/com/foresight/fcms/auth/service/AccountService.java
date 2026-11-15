package com.foresight.fcms.auth.service;

import com.foresight.fcms.audit.service.AuditService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.validation.annotation.Validated
@Service
public class AccountService {
    private final java.nio.file.Path avatarDirectory;
    private final SessionRevocationService sessions;
    private final JdbcTemplate db;
    private final AccessService access;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final com.foresight.fcms.upload.UploadInspectionService inspections;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final org.springframework.transaction.support.TransactionTemplate photoReads;
    private final org.springframework.transaction.support.TransactionTemplate photoWrites;
    private final java.util.concurrent.Semaphore photoWork = new java.util.concurrent.Semaphore(2);
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AccountService.class);

    public AccountService(
            @org.springframework.beans.factory.annotation.Value(
                            "${fcms.storage.avatar-dir:${user.home}/.fcms/avatars}")
                    String directory,
            SessionRevocationService sessions,
            JdbcTemplate db,
            AccessService access,
            PasswordEncoder passwords,
            AuditService audit,
            com.foresight.fcms.upload.UploadInspectionService inspections,
            io.micrometer.core.instrument.MeterRegistry metrics,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.avatarDirectory = java.nio.file.Path.of(directory).toAbsolutePath().normalize();
        this.sessions = sessions;
        this.db = db;
        this.access = access;
        this.passwords = passwords;
        this.audit = audit;
        this.inspections = inspections;
        this.metrics = metrics;
        this.photoReads =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.photoReads.setReadOnly(true);
        this.photoWrites =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public Profile profile() {
        var a = access.require("account.write");
        return db.queryForObject(
                "select first_name,last_name,coalesce(leave_email,'') from users where id=?",
                (r, n) -> new Profile(r.getString(1), r.getString(2), r.getString(3)),
                a.id());
    }

    @Transactional
    public void profile(@jakarta.validation.Valid Profile f) {
        var a = access.require("account.write");
        db.update(
                "update users set first_name=?,last_name=?,leave_email=?,version=version+1 where"
                        + " id=?",
                f.firstName().trim(),
                f.lastName().trim(),
                f.leaveEmail().trim(),
                a.id());
        audit.record(a.id(), "account.profile", "users", a.id(), "Profile updated");
    }

    @Transactional
    public void password(@jakarta.validation.Valid PasswordChange f) {
        var a = access.require("account.write");
        String old =
                db.queryForObject(
                        "select password from users where id=? for update", String.class, a.id());
        if (!passwords.matches(f.currentPassword(), old)
                || !f.newPassword().equals(f.confirmPassword()))
            throw new IllegalArgumentException("error.password");
        db.update(
                "update users set password=?,reset_token=null,otp=null,version=version+1 where"
                        + " id=?",
                passwords.encode(f.newPassword()),
                a.id());
        sessions.revoke(
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName());
        audit.record(a.id(), "account.password", "users", a.id(), "Password changed");
    }

    public void photo(org.springframework.web.multipart.MultipartFile file) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
            throw new IllegalStateException("Photo processing requires no database transaction");
        var before =
                photoReads.execute(
                        status -> {
                            var actor = access.require("account.write");
                            return db.queryForObject(
                                    "select image_path,version from users where id=?",
                                    (r, n) ->
                                            new PhotoSnapshot(
                                                    actor.id(), r.getString(1), r.getLong(2)),
                                    actor.id());
                        });
        java.util.Objects.requireNonNull(before);
        if (file == null || file.isEmpty() || file.getSize() > 2L * 1024 * 1024)
            throw new IllegalArgumentException("error.invalid");
        String original =
                file.getOriginalFilename() == null
                        ? ""
                        : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        boolean png = original.endsWith(".png"),
                jpeg = original.endsWith(".jpg") || original.endsWith(".jpeg");
        if (!png && !jpeg) throw new IllegalArgumentException("error.invalid");
        String media = file.getContentType();
        if (media != null
                && !java.util.Set.of("image/png", "image/jpeg", "application/octet-stream")
                        .contains(media.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("error.invalid");
        if (!photoWork.tryAcquire()) throw new IllegalArgumentException("error.uploadBusy");
        String stored = null;
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(2 * 1024 * 1024 + 1);
            }
            if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024)
                throw new IllegalArgumentException("error.invalid");
            inspections.inspect(bytes);
            java.awt.image.BufferedImage image;
            try (var input =
                    new javax.imageio.stream.MemoryCacheImageInputStream(
                            new java.io.ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("error.invalid");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName();
                    if ((png && !format.equalsIgnoreCase("png"))
                            || (jpeg && !format.equalsIgnoreCase("jpeg"))
                            || reader.getWidth(0) < 1
                            || reader.getHeight(0) < 1
                            || reader.getWidth(0) > 4096
                            || reader.getHeight(0) > 4096)
                        throw new IllegalArgumentException("error.invalid");
                    image = reader.read(0);
                } finally {
                    reader.dispose();
                }
            } catch (javax.imageio.IIOException e) {
                throw new IllegalArgumentException("error.invalid");
            }
            java.nio.file.Files.createDirectories(avatarDirectory);
            stored = java.util.UUID.randomUUID() + ".png";
            var destination = avatarDirectory.resolve(stored);
            try (var output =
                    java.nio.file.Files.newOutputStream(
                            destination, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                if (!javax.imageio.ImageIO.write(image, "png", output))
                    throw new IllegalArgumentException("error.invalid");
            }
            if (java.nio.file.Files.size(destination) > 8L * 1024 * 1024)
                throw new IllegalArgumentException("error.invalid");
            final String candidate = stored;
            String previous =
                    photoWrites.execute(
                            status -> {
                                org.springframework.transaction.support
                                        .TransactionSynchronizationManager.registerSynchronization(
                                        new org.springframework.transaction.support
                                                .TransactionSynchronization() {
                                            @Override
                                            public void afterCompletion(int outcome) {
                                                if (outcome == STATUS_ROLLED_BACK)
                                                    discardPhoto(candidate);
                                                else if (outcome == STATUS_UNKNOWN) {
                                                    metrics.counter(
                                                                    "fcms.upload.cleanup",
                                                                    "result",
                                                                    "retained")
                                                            .increment();
                                                    log.warn(
                                                            "Profile image retained after uncertain"
                                                                + " metadata commit");
                                                }
                                            }
                                        });
                                registered.set(true);
                                var actor = access.require("account.write");
                                if (actor.id() != before.actorId())
                                    throw new org.springframework.security.access
                                            .AccessDeniedException("denied");
                                if (db.update(
                                                "update users set image_path=?,version=version+1"
                                                    + " where id=? and version=?",
                                                candidate,
                                                actor.id(),
                                                before.version())
                                        != 1)
                                    throw new org.springframework.dao
                                            .OptimisticLockingFailureException("stale");
                                audit.record(
                                        actor.id(),
                                        "account.photo",
                                        "users",
                                        actor.id(),
                                        "Profile image replaced");
                                boolean shared =
                                        before.stored() != null
                                                && Boolean.TRUE.equals(
                                                        db.queryForObject(
                                                                "select exists(select 1 from users"
                                                                    + " where image_path=?)",
                                                                Boolean.class,
                                                                before.stored()));
                                return shared ? null : before.stored();
                            });
            discardPhoto(previous);
        } catch (java.io.IOException e) {
            if (!registered.get()) discardPhoto(stored);
            throw new IllegalArgumentException("error.uploadUnavailable");
        } catch (RuntimeException e) {
            if (!registered.get()) discardPhoto(stored);
            throw e;
        } finally {
            photoWork.release();
        }
    }

    private void discardPhoto(String stored) {
        if (stored == null || !stored.matches("[0-9a-f-]{36}\\.png")) return;
        try {
            java.nio.file.Files.deleteIfExists(avatarDirectory.resolve(stored));
        } catch (java.io.IOException e) {
            metrics.counter("fcms.upload.cleanup", "result", "failed").increment();
            log.warn("Unable to remove unused profile image {}", stored);
        }
    }

    private record PhotoSnapshot(long actorId, String stored, long version) {}

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource photo() {
        var actor = access.require("account.write");
        String stored =
                db.queryForObject(
                        "select image_path from users where id=?", String.class, actor.id());
        if (stored == null || !stored.matches("[0-9a-f-]{36}\\.png"))
            throw new IllegalArgumentException("error.missing");
        var file = avatarDirectory.resolve(stored).normalize();
        if (!file.startsWith(avatarDirectory)
                || !java.nio.file.Files.isRegularFile(
                        file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("error.missing");
        return new org.springframework.core.io.FileSystemResource(file);
    }

    @Transactional(readOnly = true)
    public boolean hasPhoto() {
        var actor = access.require("account.write");
        String name =
                db.queryForObject(
                        "select image_path from users where id=?", String.class, actor.id());
        return name != null && name.matches("[0-9a-f-]{36}\\.png");
    }

    public record Profile(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100)
                    String firstName,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100)
                    String lastName,
            @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 100)
                    String leaveEmail) {}

    public record PasswordChange(
            @jakarta.validation.constraints.NotBlank String currentPassword,
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Size(min = 12, max = 72)
                    String newPassword,
            @jakarta.validation.constraints.NotBlank String confirmPassword) {}
}
