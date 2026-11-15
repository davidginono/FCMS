package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.upload.UploadInspectionService;
import com.foresight.fcms.upload.UploadScanProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

class UploadInspectionServiceTests {
    private final byte[] fixture =
            "Synthetic upload inspection fixture".getBytes(StandardCharsets.US_ASCII);

    @Test
    void localScannerReceivesFramedBytesAndOnlyAnExplicitCleanReplyIsAccepted() throws Exception {
        for (String reply :
                new String[] {
                    "stream: OK\0",
                    "stream: Synthetic FOUND\0",
                    "stream: ERROR\0",
                    "stream: OK",
                    "x".repeat(1024)
                }) {
            try (var server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                    var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var received =
                        executor.submit(
                                () -> {
                                    try (var socket = server.accept()) {
                                        socket.setSoTimeout(3000);
                                        var in = new DataInputStream(socket.getInputStream());
                                        assertArrayEquals(
                                                "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII),
                                                in.readNBytes(10));
                                        int size = in.readInt();
                                        assertEquals(fixture.length, size);
                                        assertArrayEquals(fixture, in.readNBytes(size));
                                        assertEquals(0, in.readInt());
                                        socket.getOutputStream()
                                                .write(reply.getBytes(StandardCharsets.US_ASCII));
                                    }
                                    return true;
                                });
                var metrics = new SimpleMeterRegistry();
                var service = service(server.getLocalPort(), Duration.ofSeconds(2), metrics);
                if (reply.equals("stream: OK\0")) {
                    service.inspect(fixture);
                    assertEquals(
                            1,
                            metrics.get("fcms.upload.scan")
                                    .tag("result", "clean")
                                    .counter()
                                    .count());
                } else {
                    var failure =
                            assertThrows(
                                    IllegalArgumentException.class, () -> service.inspect(fixture));
                    assertEquals(
                            reply.contains("FOUND")
                                    ? "error.uploadRejected"
                                    : "error.uploadUnavailable",
                            failure.getMessage());
                }
                assertTrue(received.get(4, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void scanningHasOneOverallDeadlineAndAdmissionIsBounded() throws Exception {
        try (var server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accepted = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            for (int n = 0; n < 2; n++)
                executor.submit(
                        () -> {
                            try (var socket = server.accept()) {
                                accepted.countDown();
                                release.await(3, TimeUnit.SECONDS);
                            }
                            return true;
                        });
            var metrics = new SimpleMeterRegistry();
            var service = service(server.getLocalPort(), Duration.ofSeconds(1), metrics);
            var first =
                    executor.submit(
                            () ->
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () -> service.inspect(fixture)));
            var second =
                    executor.submit(
                            () ->
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () -> service.inspect(fixture)));
            try {
                assertTrue(accepted.await(3, TimeUnit.SECONDS));
                assertEquals(
                        "error.uploadBusy",
                        assertThrows(IllegalArgumentException.class, () -> service.inspect(fixture))
                                .getMessage());
                assertEquals(
                        "error.uploadUnavailable", first.get(3, TimeUnit.SECONDS).getMessage());
                assertEquals(
                        "error.uploadUnavailable", second.get(3, TimeUnit.SECONDS).getMessage());
                assertEquals(
                        2,
                        metrics.get("fcms.upload.scan")
                                .tag("result", "unavailable")
                                .counter()
                                .count());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void productionCannotDisableScanningAndNoScanCanHoldADatabaseTransaction() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThrows(
                IllegalStateException.class,
                () ->
                        new UploadInspectionService(
                                new UploadScanProperties(false, 3310, Duration.ofSeconds(1)),
                                production,
                                new SimpleMeterRegistry()));
        var service =
                new UploadInspectionService(
                        new UploadScanProperties(false, 3310, Duration.ofSeconds(1)),
                        new MockEnvironment(),
                        new SimpleMeterRegistry());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> service.inspect(fixture));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> service.inspect(new byte[5 * 1024 * 1024 + 1]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadScanProperties(true, 0, Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UploadScanProperties(true, 3310, Duration.ofMillis(1)));
        service.inspect(fixture);
    }

    private UploadInspectionService service(
            int port, Duration timeout, SimpleMeterRegistry metrics) {
        return new UploadInspectionService(
                new UploadScanProperties(true, port, timeout), new MockEnvironment(), metrics);
    }
}
