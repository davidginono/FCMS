package com.foresight.fcms.client.saccos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class SaccosStorageService {

    private final Path contractDirectory;
    private final com.foresight.fcms.upload.UploadInspectionService inspections;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final java.util.concurrent.Semaphore work = new java.util.concurrent.Semaphore(2);
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SaccosStorageService.class);

    public SaccosStorageService(
            @Value("${fcms.storage.contract-dir:uploads/contracts}") String contractDirectory,
            com.foresight.fcms.upload.UploadInspectionService inspections,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        this.contractDirectory = Path.of(contractDirectory).toAbsolutePath().normalize();
        this.inspections = inspections;
        this.metrics = metrics;
    }

    public String storeContract(MultipartFile file) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
            throw new IllegalStateException("Contract processing requires no database transaction");
        if (file == null || file.isEmpty()) {
            return null;
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                || file.getSize() > 5L * 1024 * 1024)
            throw new IllegalArgumentException("error.invalid");
        String media = file.getContentType();
        if (media != null
                && !java.util.Set.of("application/pdf", "application/octet-stream")
                        .contains(media.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("error.invalid");
        if (!work.tryAcquire()) throw new IllegalArgumentException("error.uploadBusy");
        String storedName = null;
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(5 * 1024 * 1024 + 1);
            }
            if (bytes.length < 5
                    || bytes.length > 5 * 1024 * 1024
                    || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                            .equals("%PDF-")) throw new IllegalArgumentException("error.invalid");
            inspections.inspect(bytes);
            try (var reader = new com.lowagie.text.pdf.PdfReader(bytes)) {
                if (reader.getNumberOfPages() < 1
                        || reader.getNumberOfPages() > 500
                        || reader.isEncrypted())
                    throw new IllegalArgumentException("error.invalid");
            } catch (IOException e) {
                throw new IllegalArgumentException("error.invalid");
            }
            Files.createDirectories(contractDirectory);
            storedName = UUID.randomUUID() + ".pdf";
            Path destination = contractDirectory.resolve(storedName);
            Files.write(destination, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            return storedName;
        } catch (IOException ex) {
            discardUncommitted(storedName);
            throw new IllegalArgumentException("error.uploadUnavailable");
        } catch (RuntimeException ex) {
            discardUncommitted(storedName);
            throw ex;
        } finally {
            work.release();
        }
    }

    /** Bind a newly staged file to the owning metadata transaction. Old contracts are retained. */
    public void discardOnRollback(String storedName) {
        if (storedName == null) return;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) discardUncommitted(storedName);
                                else if (status == STATUS_UNKNOWN) {
                                    metrics.counter("fcms.upload.cleanup", "result", "retained")
                                            .increment();
                                    log.warn("Contract retained after uncertain metadata commit");
                                }
                            }
                        });
    }

    public void discardUncommitted(String storedName) {
        if (storedName == null || !storedName.matches("[0-9a-f-]{36}\\.pdf")) return;
        try {
            Files.deleteIfExists(contractDirectory.resolve(storedName));
        } catch (IOException e) {
            metrics.counter("fcms.upload.cleanup", "result", "failed").increment();
            log.warn("Unable to remove uncommitted contract {}", storedName);
        }
    }

    public Resource loadContract(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("error.missing");
        }
        Path file = contractDirectory.resolve(storedName).normalize();
        if (!file.getParent().equals(contractDirectory)
                || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("error.missing");
        }
        return new FileSystemResource(file);
    }
}
