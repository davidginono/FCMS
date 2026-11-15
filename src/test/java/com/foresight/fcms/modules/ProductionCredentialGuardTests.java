package com.foresight.fcms.modules;

import static org.assertj.core.api.Assertions.assertThat;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;
import com.foresight.fcms.auth.service.ProductionCredentialGuardService;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionCredentialGuardTests {
    final String schema = "bootstrap_guard_" + UUID.randomUUID().toString().replace("-", "");
    final LegacyAwarePasswordEncoder passwords = new LegacyAwarePasswordEncoder();
    JdbcTemplate db;
    String seedHash;

    @BeforeAll
    void migrate() {
        var dataSource =
                new DriverManagerDataSource(
                        System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + schema,
                        System.getenv("FCMS_DB_USERNAME"),
                        System.getenv("FCMS_DB_PASSWORD"));
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();
        db = new JdbcTemplate(dataSource);
        seedHash = db.queryForObject("select password from users where id=1", String.class);
    }

    @BeforeEach
    void reset() {
        db.update("update users set status_id=2,version=version+1");
        db.update(
                "update users set password=?,email='admin@fcms.local',status_id=1,version=version+1"
                    + " where id=1",
                seedHash);
    }

    ApplicationContextRunner context(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(c -> c.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(ProductionCredentialGuardService.class)
                .withBean(JdbcTemplate.class, () -> db);
    }

    void blocked() {
        String before =
                db.queryForObject(
                        "select jsonb_agg(to_jsonb(u) order by id)::text from users u",
                        String.class);
        context("prod")
                .run(
                        c -> {
                            assertThat(c).hasFailed();
                            Throwable cause = c.getStartupFailure();
                            while (cause.getCause() != null) cause = cause.getCause();
                            assertThat(cause)
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessage(
                                            "Production startup refused: rotate or disable the"
                                                + " development bootstrap credential");
                            assertThat(cause.getMessage())
                                    .doesNotContain(
                                            seedHash,
                                            LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                            "admin@fcms.local");
                        });
        assertThat(
                        db.queryForObject(
                                "select jsonb_agg(to_jsonb(u) order by id)::text from users u",
                                String.class))
                .isEqualTo(before);
    }

    void allowed() {
        context("prod")
                .run(
                        c ->
                                assertThat(c)
                                        .hasNotFailed()
                                        .hasSingleBean(ProductionCredentialGuardService.class));
    }

    @Test
    void productionRejectsOriginalRenamedAndRehashedBootstrapCredentials() {
        blocked();
        db.update(
                "update users set email='renamed-bootstrap@test.invalid',version=version+1 where"
                    + " id=1");
        blocked();
        db.update(
                "update users set password=?,version=version+1 where id=1",
                passwords.encode(LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD));
        blocked();
    }

    @Test
    void productionAcceptsDisabledOrChangedBootstrapCredential() {
        db.update("update users set status_id=2,version=version+1 where id=1");
        allowed();
        db.update(
                "update users set password=?,status_id=1,version=version+1 where id=1",
                passwords.encode("SyntheticChangedPassword123!"));
        allowed();
    }

    @Test
    void enabledCopiesOfThePublishedHashOrPlaintextAlsoBlockStartup() {
        db.update(
                "update users set password=?,version=version+1 where id=1",
                passwords.encode("SyntheticChangedPassword123!"));
        long copy =
                db.queryForObject(
                        "insert into users(first_name,last_name,email,password,status_id,role_id)"
                            + " values ('Bootstrap','Fixture',?,?,1,2) returning id",
                        Long.class,
                        "copy-" + UUID.randomUUID() + "@test.invalid",
                        seedHash);
        blocked();
        db.update(
                "update users set password=?,version=version+1 where id=?",
                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                copy);
        blocked();
        db.update("update users set status_id=2,version=version+1 where id=?", copy);
        allowed();
    }

    @Test
    void developmentProfileKeepsItsLocalBootstrapWorkflow() {
        context("default")
                .run(
                        c ->
                                assertThat(c)
                                        .hasNotFailed()
                                        .doesNotHaveBean(ProductionCredentialGuardService.class));
    }
}
