package com.foresight.fcms.shared.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Length-framed, deterministic identity for bounded validated command facts. Never logs payloads.
 */
public final class CommandFingerprint {
    private CommandFingerprint() {}

    public static String of(String... fields) {
        var text = new StringBuilder();
        for (String value : fields) text.append(value.length()).append(':').append(value);
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Request identity unavailable", error);
        }
    }
}
