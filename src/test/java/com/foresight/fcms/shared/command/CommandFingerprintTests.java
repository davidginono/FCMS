package com.foresight.fcms.shared.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandFingerprintTests {
    @Test
    void framingSeparatesAmbiguousBoundariesAndPreservesExactUnicode() {
        assertNotEquals(CommandFingerprint.of("ab", "c"), CommandFingerprint.of("a", "bc"));
        assertNotEquals(CommandFingerprint.of("1:a", ""), CommandFingerprint.of("1:", "a"));
        assertEquals(
                CommandFingerprint.of("Kiswahili 日本語", "123.45"),
                CommandFingerprint.of("Kiswahili 日本語", "123.45"));
        assertNotEquals(CommandFingerprint.of("123.45"), CommandFingerprint.of("123.46"));
        assertEquals(64, CommandFingerprint.of("bounded").length());
    }
}
