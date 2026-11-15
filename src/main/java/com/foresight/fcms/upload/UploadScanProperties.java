package com.foresight.fcms.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("fcms.upload-scan")
public record UploadScanProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("3310") int port,
        @DefaultValue("5s") Duration timeout) {
    public UploadScanProperties {
        if (port < 1
                || port > 65535
                || timeout == null
                || timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Invalid upload scanner configuration");
        }
    }
}
