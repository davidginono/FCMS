package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.foresight.fcms.client.saccos.service.SaccosStorageService;
import com.foresight.fcms.upload.UploadInspectionService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.*;

class ContractStorageTests {
    @TempDir Path directory;

    @Test
    void contentInspectionPrecedesStorageAndRejectsSpoofedContent() throws Exception {
        var inspection = mock(UploadInspectionService.class);
        var storage =
                new SaccosStorageService(
                        directory.toString(), inspection, new SimpleMeterRegistry());
        byte[] pdf = pdf();
        String stored =
                storage.storeContract(
                        new MockMultipartFile(
                                "contract", "../../evidence.pdf", "application/pdf", pdf));
        assertTrue(stored.matches("[0-9a-f-]{36}\\.pdf"));
        assertArrayEquals(pdf, Files.readAllBytes(directory.resolve(stored)));
        verify(inspection).inspect(pdf);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        storage.storeContract(
                                new MockMultipartFile("contract", "wrong.pdf", "text/html", pdf)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        storage.storeContract(
                                new MockMultipartFile(
                                        "contract",
                                        "wrong.pdf",
                                        "application/pdf",
                                        "%PDF-not-a-document".getBytes())));
        doThrow(new IllegalArgumentException("error.uploadRejected")).when(inspection).inspect(pdf);
        assertEquals(
                "error.uploadRejected",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        storage.storeContract(
                                                new MockMultipartFile(
                                                        "contract",
                                                        "rejected.pdf",
                                                        "application/pdf",
                                                        pdf)))
                        .getMessage());
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
        assertThrows(IllegalArgumentException.class, () -> storage.loadContract("../outside.pdf"));
        assertTrue(storage.loadContract(stored).exists());
    }

    @Test
    void rollbackCleansOnlyNewFilesAndUncertainCommitsRetainEvidence() throws Exception {
        var inspection = mock(UploadInspectionService.class);
        var storage =
                new SaccosStorageService(
                        directory.toString(), inspection, new SimpleMeterRegistry());
        String committed =
                storage.storeContract(
                        new MockMultipartFile("contract", "prior.pdf", "application/pdf", pdf()));
        for (int outcome :
                new int[] {
                    TransactionSynchronization.STATUS_ROLLED_BACK,
                    TransactionSynchronization.STATUS_COMMITTED,
                    TransactionSynchronization.STATUS_UNKNOWN
                }) {
            String candidate =
                    storage.storeContract(
                            new MockMultipartFile(
                                    "contract", "next.pdf", "application/pdf", pdf()));
            TransactionSynchronizationManager.initSynchronization();
            try {
                storage.discardOnRollback(candidate);
                for (var callback : TransactionSynchronizationManager.getSynchronizations())
                    callback.afterCompletion(outcome);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            assertEquals(
                    outcome != TransactionSynchronization.STATUS_ROLLED_BACK,
                    Files.exists(directory.resolve(candidate)));
            assertTrue(
                    Files.exists(directory.resolve(committed)),
                    "Previously committed contracts must remain retained");
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            storage.storeContract(
                                    new MockMultipartFile(
                                            "contract", "blocked.pdf", "application/pdf", pdf())));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private byte[] pdf() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var document = new com.lowagie.text.Document();
        com.lowagie.text.pdf.PdfWriter.getInstance(document, bytes);
        document.open();
        document.add(new com.lowagie.text.Paragraph("Synthetic retained contract"));
        document.close();
        return bytes.toByteArray();
    }
}
