package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@Import(ProductionAuthenticationTests.PreparedBootstrap.class)
class ProductionAuthenticationTests {
    static final String SCHEMA = "production_auth_" + UUID.randomUUID().toString().replace("-", "");
    static final String EMAIL = "production-fixture@test.invalid",
            PASSWORD = "BootstrapVerification123!";
    static String publishedHash;
    static final LegacyAwarePasswordEncoder fixturePasswords = new LegacyAwarePasswordEncoder();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration");
        p.add("fcms.backup.interval", () -> "3600000");
        p.add("fcms.mail.interval", () -> "3600000");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PreparedBootstrap {
        @Bean
        FlywayMigrationStrategy provisionSyntheticAdministrator() {
            return flyway -> {
                flyway.migrate();
                var db = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                publishedHash =
                        db.queryForObject("select password from users where id=1", String.class);
                db.update(
                        "update users set email=?,password=?,version=version+1 where id=1",
                        EMAIL,
                        fixturePasswords.encode(PASSWORD));
            };
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired com.foresight.fcms.auth.service.PasswordRecoveryService recovery;

    @Value("${local.server.port}")
    int port;

    final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    URI uri(String route) {
        return URI.create("http://127.0.0.1:" + port + route);
    }

    HttpResponse<String> get(String path, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(45));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> login(String password, String csrf, String cookie) throws Exception {
        String body =
                "email="
                        + URLEncoder.encode(EMAIL, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(password, StandardCharsets.UTF_8);
        if (csrf != null) body += "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        return http.send(
                HttpRequest.newBuilder(uri("/login"))
                        .timeout(Duration.ofSeconds(45))
                        .header("Cookie", cookie)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String cookie(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie").stream()
                .filter(s -> s.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
    }

    String csrf(String html) {
        var match = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(match.find());
        return match.group(1);
    }

    @Test
    void productionFiltersRejectPublishedCredentialsAndKeepCsrfAndSecureSessions()
            throws Exception {
        try {
            for (String hash :
                    List.of(
                            publishedHash,
                            fixturePasswords.encode(
                                    LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD),
                            LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD)) {
                // Simulates a later reactivation or offline credential import in this disposable
                // schema.
                db.update(
                        "update users set password=?,status_id=1,version=version+1 where id=1",
                        hash);
                var form = get("/login", null);
                assertEquals(200, form.statusCode());
                assertTrue(
                        form.headers().allValues("set-cookie").stream()
                                .anyMatch(
                                        s ->
                                                s.contains("Secure")
                                                        && s.contains("HttpOnly")
                                                        && s.contains("SameSite=Lax")));
                String session = cookie(form);
                var rejected =
                        login(
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                csrf(form.body()),
                                session);
                assertEquals(302, rejected.statusCode());
                assertTrue(
                        rejected.headers()
                                .firstValue("location")
                                .orElseThrow()
                                .endsWith("/login?error"));
                assertEquals(302, get("/dashboard", session).statusCode());
                assertFalse(
                        get("/login?error", session)
                                .body()
                                .contains(LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD));
            }
        } finally {
            db.update(
                    "update users set password=?,version=version+1 where id=1",
                    fixturePasswords.encode(PASSWORD));
        }
        var form = get("/login", null);
        String session = cookie(form);
        assertEquals(403, login(PASSWORD, null, session).statusCode());
        var accepted = login(PASSWORD, csrf(form.body()), session);
        assertEquals(302, accepted.statusCode());
        assertTrue(accepted.headers().firstValue("location").orElseThrow().endsWith("/dashboard"));
        String authenticated = cookie(accepted);
        assertNotEquals(session, authenticated);
        assertEquals(200, get("/dashboard", authenticated).statusCode());
    }

    @Test
    void rejectedRecoveryPasswordKeepsTheChallengeAndShowsLocalizedFormErrors() throws Exception {
        recovery.request(EMAIL);
        String mail =
                db.queryForObject(
                        "select body from mail_outbox where recipient=? order by id desc limit 1",
                        String.class,
                        EMAIL);
        var tokenMatcher = Pattern.compile("Token: ([A-Za-z0-9_-]+)").matcher(mail);
        var otpMatcher = Pattern.compile("OTP: ([0-9]{6})").matcher(mail);
        assertTrue(tokenMatcher.find());
        assertTrue(otpMatcher.find());
        String token = tokenMatcher.group(1), otp = otpMatcher.group(1);
        String before = db.queryForObject("select password from users where id=1", String.class);
        try {
            assertThrows(IllegalArgumentException.class, () -> recovery.reset(token, otp, "x"));
            for (String language : List.of("en", "sw")) {
                var form = get("/password/reset?lang=" + language, null);
                String session = cookie(form);
                var rejected =
                        reset(
                                token,
                                otp,
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                csrf(form.body()),
                                session);
                assertEquals(200, rejected.statusCode());
                assertTrue(
                        rejected.body()
                                .contains(
                                        language.equals("sw")
                                                ? "Chagua nenosiri jipya tofauti."
                                                : "Choose a different new password."));
                assertTrue(rejected.body().contains(token));
                assertEquals(
                        before,
                        db.queryForObject("select password from users where id=1", String.class));
                assertEquals(
                        0,
                        db.queryForObject(
                                "select attempts from password_challenges where user_id=1 order by"
                                        + " created_at desc limit 1",
                                Integer.class));
                assertNull(
                        db.queryForObject(
                                "select used_at from password_challenges where user_id=1 order by"
                                        + " created_at desc limit 1",
                                java.sql.Timestamp.class));
            }
            var form = get("/password/reset", null);
            String session = cookie(form);
            var accepted =
                    reset(token, otp, "RecoveryVerification123!", csrf(form.body()), session);
            assertEquals(302, accepted.statusCode());
            assertTrue(
                    accepted.headers()
                            .firstValue("location")
                            .orElseThrow()
                            .endsWith("/login?passwordChanged"));
            assertTrue(
                    fixturePasswords.matches(
                            "RecoveryVerification123!",
                            db.queryForObject(
                                    "select password from users where id=1", String.class)));
            assertNotNull(
                    db.queryForObject(
                            "select used_at from password_challenges where user_id=1 order by"
                                    + " created_at desc limit 1",
                            java.sql.Timestamp.class));
            assertFalse(recovery.reset(token, otp, "DifferentReplayPassword123!"));
        } finally {
            db.update(
                    "update users set password=?,version=version+1 where id=1",
                    fixturePasswords.encode(PASSWORD));
        }
    }

    HttpResponse<String> reset(
            String token, String otp, String password, String csrf, String cookie)
            throws Exception {
        String body =
                "token="
                        + token
                        + "&otp="
                        + otp
                        + "&password="
                        + URLEncoder.encode(password, StandardCharsets.UTF_8)
                        + "&confirmPassword="
                        + URLEncoder.encode(password, StandardCharsets.UTF_8)
                        + "&_csrf="
                        + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        return http.send(
                HttpRequest.newBuilder(uri("/password/reset"))
                        .timeout(Duration.ofSeconds(45))
                        .header("Cookie", cookie)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rejectedAdministratorAndSelfPasswordChangesKeepAccountAndSessionIntact() throws Exception {
        var signIn = get("/login", null);
        var signedIn = login(PASSWORD, csrf(signIn.body()), cookie(signIn));
        String session = cookie(signedIn);
        String before =
                db.queryForObject("select to_jsonb(u)::text from users u where id=1", String.class);
        assertTrue(get("/admin/users/new", session).body().contains("Create user"));
        var details = get("/admin/users/1", session);
        assertTrue(details.body().contains("User details"));
        assertTrue(details.body().contains("erp-resource-section"));
        var form = get("/admin/users/1/edit", session);
        assertEquals(200, form.statusCode());
        assertTrue(form.body().contains("Edit user"));
        String body =
                "_csrf="
                        + csrf(form.body())
                        + "&firstName=Migration&lastName=Admin&email="
                        + URLEncoder.encode(EMAIL, StandardCharsets.UTF_8)
                        + "&roleId=1&statusId=1&version="
                        + db.queryForObject("select version from users where id=1", Long.class)
                        + "&password="
                        + URLEncoder.encode(
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                StandardCharsets.UTF_8);
        var rejected = post("/admin/users/1", body, session);
        assertEquals(200, rejected.statusCode());
        assertTrue(rejected.body().contains("Choose a different new password."));
        assertEquals(
                before,
                db.queryForObject(
                        "select to_jsonb(u)::text from users u where id=1", String.class));
        assertEquals(200, get("/dashboard", session).statusCode());
        form = get("/account/password", session);
        body =
                "_csrf="
                        + csrf(form.body())
                        + "&currentPassword="
                        + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                        + "&newPassword="
                        + URLEncoder.encode(
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                StandardCharsets.UTF_8)
                        + "&confirmPassword="
                        + URLEncoder.encode(
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                StandardCharsets.UTF_8);
        rejected = post("/account/password", body, session);
        assertEquals(200, rejected.statusCode());
        assertTrue(rejected.body().contains("Choose a different new password."));
        assertEquals(
                before,
                db.queryForObject(
                        "select to_jsonb(u)::text from users u where id=1", String.class));
        assertEquals(200, get("/dashboard", session).statusCode());
    }

    HttpResponse<String> post(String route, String body, String session) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri(route))
                        .timeout(Duration.ofSeconds(45))
                        .header("Cookie", session)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
