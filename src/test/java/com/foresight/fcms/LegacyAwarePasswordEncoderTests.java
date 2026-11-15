package com.foresight.fcms;

import static org.assertj.core.api.Assertions.assertThat;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;

import org.junit.jupiter.api.Test;

class LegacyAwarePasswordEncoderTests {

    private final LegacyAwarePasswordEncoder encoder = new LegacyAwarePasswordEncoder();

    @Test
    void matchesBcryptPasswords() {
        String encoded = encoder.encode("secret");

        assertThat(encoder.matches("secret", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void matchesLegacyPlainTextPasswords() {
        assertThat(encoder.matches("secret", "secret")).isTrue();
        assertThat(encoder.matches("secret", "different")).isFalse();
    }

    @Test
    void productionRejectsThePublishedCredentialEvenAfterRehashing() {
        var production = new LegacyAwarePasswordEncoder(true);
        String known = LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD;
        assertThat(production.matches(known, encoder.encode(known))).isFalse();
        assertThat(production.matches(known, known)).isFalse();
        assertThat(
                        production.matches(
                                "DifferentStrongPassword123!",
                                encoder.encode("DifferentStrongPassword123!")))
                .isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> production.encode(known))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.passwordDefault");
    }

    @Test
    void developmentRetainsTheDocumentedSeedLogin() {
        assertThat(
                        encoder.matches(
                                LegacyAwarePasswordEncoder.DEVELOPMENT_PASSWORD,
                                "$2a$10$rYKjlzN8caeIEpElikbNwOJ02T.1iFrYadP.bU.azTDIaxzrVZGkK"))
                .isTrue();
    }
}
