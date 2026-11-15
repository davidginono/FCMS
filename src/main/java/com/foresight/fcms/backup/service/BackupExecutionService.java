package com.foresight.fcms.backup.service;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class BackupExecutionService {
    private final BackupCatalogService catalog;
    private final Path directory;
    private final String executable, url, user, password;
    private final MeterRegistry metrics;

    public BackupExecutionService(
            BackupCatalogService catalog,
            MeterRegistry metrics,
            @Value("${fcms.backup.directory:${user.home}/.fcms/backups}") String directory,
            @Value("${fcms.backup.pg-dump:pg_dump}") String executable,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password) {
        this.catalog = catalog;
        this.metrics = metrics;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.executable = executable;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public void runNext() {
        UUID id = catalog.claim();
        if (id == null) return;
        Process process = null;
        Path file = directory.resolve(id + ".dump");
        try {
            Files.createDirectories(directory);
            if (Files.getFileStore(directory).getUsableSpace() < 1024L * 1024 * 1024)
                throw new java.io.IOException();
            var uri = java.net.URI.create(url.substring("jdbc:".length()));
            var command =
                    new ProcessBuilder(
                            executable,
                            "--host",
                            uri.getHost(),
                            "--port",
                            Integer.toString(uri.getPort() < 0 ? 5432 : uri.getPort()),
                            "--username",
                            user,
                            "--dbname",
                            uri.getPath().substring(1),
                            "--format=custom",
                            "--no-password",
                            "--file",
                            file.toString());
            command.environment().put("PGPASSWORD", password);
            command.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            command.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = command.start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new java.io.IOException();
            }
            if (process.exitValue() != 0) throw new java.io.IOException();
            catalog.finish(id, Files.size(file), null);
            metrics.counter("fcms.backup.completed").increment();
        } catch (Exception e) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            catalog.finish(id, null, "BACKUP_FAILED");
            metrics.counter("fcms.backup.failed").increment();
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Backup {} failed", id);
        }
    }

    public Resource download(UUID id) {
        catalog.authorizeDownload(id);
        Path file = directory.resolve(id + ".dump").normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file))
            throw new IllegalArgumentException("error.missing");
        return new FileSystemResource(file);
    }
}
