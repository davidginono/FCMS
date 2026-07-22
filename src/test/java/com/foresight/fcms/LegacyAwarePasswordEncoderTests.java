package com.foresight.fcms;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
