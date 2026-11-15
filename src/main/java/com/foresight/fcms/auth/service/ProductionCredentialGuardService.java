package com.foresight.fcms.auth.service;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reject the retained development bootstrap credential before production accepts traffic. */
@Service
@Profile("prod")
@DependsOnDatabaseInitialization
public class ProductionCredentialGuardService implements SmartInitializingSingleton {
    // The V1 bootstrap hash is public development data, not a deployment secret.
    private static final String DEVELOPMENT_HASH =
            "$2a$10$rYKjlzN8caeIEpElikbNwOJ02T.1iFrYadP.bU.azTDIaxzrVZGkK";
    private final JdbcTemplate db;

    public ProductionCredentialGuardService(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // One seed identity plus at most one copied published credential proves the gate.
        // Freshly hashed copies on other identities are denied by the production encoder.
        var candidates =
                db.queryForList(
                        "select u.password from users u join account_status s on s.id=u.status_id"
                                + " where lower(s.status_name)='active' and"
                                + " (u.id=1 or u.password in (?,?)) order by u.id limit 2",
                        String.class,
                        DEVELOPMENT_HASH,
                        LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD);
        var verifier = new LegacyAwarePasswordEncoder();
        if (candidates.stream()
                .anyMatch(
                        hash ->
                                verifier.matches(
                                        LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD, hash))) {
            throw new IllegalStateException(
                    "Production startup refused: rotate or disable the development bootstrap"
                        + " credential");
        }
    }
}
