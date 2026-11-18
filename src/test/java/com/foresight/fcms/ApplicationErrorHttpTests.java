package com.foresight.fcms;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.config.SecurityConfig;
import com.foresight.fcms.shared.controller.ApplicationErrorViewResolver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

@SpringBootTest(
        classes = ApplicationErrorHttpTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "server.servlet.context-path=/fcms",
            "server.servlet.session.tracking-modes=cookie"
        })
class ApplicationErrorHttpTests {
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, ApplicationErrorViewResolver.class, Pages.class,
            com.foresight.fcms.shared.controller.RetiredWorkspaceController.class})
    static class TestApplication {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("fixture@test.invalid")
                            .password(
                                    new com.foresight.fcms.auth.LegacyAwarePasswordEncoder()
                                            .encode("SyntheticPassword123!"))
                            .authorities("workspace.admin")
                            .build());
        }
    }

    @TestComponent
    @RestController
    static class Pages {
        @GetMapping("/dashboard")
        String dashboard() {
            return "Synthetic dashboard";
        }

        @GetMapping("/login")
        org.springframework.web.servlet.ModelAndView login() {
            return new org.springframework.web.servlet.ModelAndView("auth/login");
        }
    }

    @Value("${local.server.port}")
    int port;

    HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    HttpResponse<String> send(HttpClient client, String path, String body, String accept)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fcms" + path))
                        .timeout(Duration.ofSeconds(60))
                        .header("Accept", accept);
        if (body == null) builder.GET();
        else
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), html);
        return "_csrf=" + URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
    }

    @Test
    void retiredAccountingWorkspacesReturnGoneForAllAuthenticatedCommandsAndRetainCsrf() throws Exception {
        var client = client();
        assertEquals(302, send(client, "/accounting/books", null, "text/html").statusCode());
        var login = send(client, "/login", null, "text/html");
        assertEquals(302, send(client, "/login", csrf(login.body())
                + "&email=fixture%40test.invalid&password=SyntheticPassword123%21", "text/html").statusCode());
        String token = csrf(send(client, "/login", null, "text/html").body());
        for (String base : java.util.List.of("/accounting/books", "/accounting/periods", "/accounting/equity-reporting")) {
            for (String suffix : java.util.List.of("", "/new", "/edit", "/1", "/approve", "/1/state", "/1/decision", "/movements", "/movements/1", "/print.pdf", "/export.csv")) {
                String path = base + suffix;
                var gone = send(client, path, null, "text/html");
                assertEquals(410, gone.statusCode(), path);
                assertEquals("", gone.body());
                assertEquals("no-store", gone.headers().firstValue("cache-control").orElseThrow());
                for (String method : java.util.List.of("POST", "PUT", "PATCH", "DELETE")) {
                    var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fcms" + path))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .method(method, HttpRequest.BodyPublishers.ofString(token)).build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(410, response.statusCode(), method + " " + path);
                    assertEquals("", response.body());
                }
            }
            assertEquals(403, send(client, base, "", "application/json").statusCode());
        }
        assertEquals(200, send(client, "/dashboard", null, "text/html").statusCode());
    }

    @Test
    void rejectedLogoutRendersRecoveryAndFreshTokenCanCompleteLogout() throws Exception {
        var client = client();
        var login = send(client, "/login", null, "text/html");
        assertEquals(200, login.statusCode());
        var signedIn =
                send(
                        client,
                        "/login",
                        csrf(login.body())
                                + "&email=fixture%40test.invalid&password=SyntheticPassword123%21",
                        "text/html");
        assertEquals(302, signedIn.statusCode());
        assertEquals(200, send(client, "/dashboard", null, "text/html").statusCode());

        var rejected = send(client, "/logout", "_csrf=stale", "text/html");
        assertEquals(403, rejected.statusCode());
        assertTrue(rejected.body().contains("Logout could not be completed."), rejected.body());
        assertTrue(rejected.body().contains("href=\"/fcms/dashboard\""));
        assertTrue(rejected.body().contains("Try logging out again"));
        assertFalse(rejected.body().contains("Whitelabel"));
        assertEquals(200, send(client, "/dashboard", null, "text/html").statusCode());

        var loggedOut = send(client, "/logout", csrf(rejected.body()), "text/html");
        assertEquals(302, loggedOut.statusCode());
        assertTrue(
                loggedOut.headers().firstValue("location").orElseThrow().endsWith("/login?logout"));
        assertEquals(302, send(client, "/dashboard", null, "text/html").statusCode());
    }

    @Test
    void anonymousErrorsOfferSignInWithoutReplayingLogoutAndJsonRetainsItsStatus()
            throws Exception {
        var client = client();
        var rejected = send(client, "/login", "", "text/html");
        assertEquals(403, rejected.statusCode());
        assertTrue(rejected.body().contains("href=\"/fcms/login\""), rejected.body());
        assertTrue(rejected.body().contains("Access denied."));
        assertFalse(rejected.body().contains("Try logging out again"));
        assertFalse(rejected.body().contains("Whitelabel"));
        var missing = send(client, "/css/nonexistent.css", null, "text/html");
        assertEquals(404, missing.statusCode());
        assertTrue(missing.body().contains("This page could not be found."));
        var json = send(client, "/css/nonexistent.css", null, "application/json");
        assertEquals(404, json.statusCode());
        assertTrue(
                json.headers()
                        .firstValue("content-type")
                        .orElseThrow()
                        .contains("application/json"));
        assertFalse(json.body().contains("<html"));
    }

    @Test
    void expiredLogoutGoesStraightToSignInAndGetCannotLogOutAnActiveSession() throws Exception {
        var expired = client();
        var redirect = send(expired, "/logout", "_csrf=old-session", "text/html");
        assertEquals(302, redirect.statusCode());
        var destination = URI.create(redirect.headers().firstValue("location").orElseThrow());
        assertEquals("/fcms/login", destination.getPath());
        assertEquals("logout", destination.getQuery());
        assertFalse(redirect.body().contains("<html"));
        assertEquals(200, send(expired, "/login?logout", null, "text/html").statusCode());
        assertEquals(302, send(expired, "/dashboard", null, "text/html").statusCode());

        var active = client();
        var login = send(active, "/login", null, "text/html");
        send(
                active,
                "/login",
                csrf(login.body())
                        + "&email=fixture%40test.invalid&password=SyntheticPassword123%21",
                "text/html");
        assertEquals(403, send(active, "/logout", null, "text/html").statusCode());
        assertEquals(200, send(active, "/dashboard", null, "text/html").statusCode());
        // This is the refresh performed by the navbar before its one protected POST.
        var fresh = send(active, "/login", null, "text/html");
        var loggedOut = send(active, "/logout", csrf(fresh.body()), "text/html");
        assertEquals(302, loggedOut.statusCode());
        assertTrue(
                loggedOut.headers().firstValue("location").orElseThrow().endsWith("/login?logout"));
        assertEquals(302, send(active, "/dashboard", null, "text/html").statusCode());
    }
}
