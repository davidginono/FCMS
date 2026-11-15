package com.foresight.fcms.upload;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Bounded local clamd INSTREAM inspection; filenames and contents are never logged. */
@Service
public class UploadInspectionService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final UploadScanProperties settings;
    private final MeterRegistry metrics;
    private final Semaphore scans = new Semaphore(2);

    public UploadInspectionService(
            UploadScanProperties settings, Environment environment, MeterRegistry metrics) {
        this.settings = settings;
        this.metrics = metrics;
        if (environment.acceptsProfiles(Profiles.of("prod")) && !settings.enabled()) {
            throw new IllegalStateException("Production requires upload scanning");
        }
    }

    public void inspect(byte[] bytes) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Upload inspection requires no database transaction");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("error.invalid");
        }
        if (!settings.enabled()) {
            result("disabled");
            return;
        }
        if (!scans.tryAcquire()) {
            result("busy");
            throw new IllegalArgumentException("error.uploadBusy");
        }
        try {
            String reply = scan(bytes);
            if (reply.equals("stream: OK")) {
                result("clean");
            } else if (reply.startsWith("stream: ") && reply.endsWith(" FOUND")) {
                result("rejected");
                throw new IllegalArgumentException("error.uploadRejected");
            } else {
                throw new IOException("Invalid scanner response");
            }
        } catch (IOException e) {
            result("unavailable");
            throw new IllegalArgumentException("error.uploadUnavailable");
        } finally {
            scans.release();
        }
    }

    private String scan(byte[] bytes) throws IOException {
        long deadline = System.nanoTime() + settings.timeout().toNanos();
        try (var channel = SocketChannel.open();
                var selector = Selector.open()) {
            channel.configureBlocking(false);
            var key = channel.register(selector, 0);
            // Local-only transport: unencrypted clamd sockets never receive remote hostnames.
            channel.connect(
                    new InetSocketAddress(
                            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), settings.port()));
            while (!channel.finishConnect())
                await(selector, key, SelectionKey.OP_CONNECT, deadline);
            var buffers =
                    new ByteBuffer[] {
                        ByteBuffer.wrap(
                                new byte[] {'z', 'I', 'N', 'S', 'T', 'R', 'E', 'A', 'M', 0}),
                        ByteBuffer.allocate(4).putInt(bytes.length).flip(),
                        ByteBuffer.wrap(bytes),
                        ByteBuffer.allocate(4)
                    };
            while (buffers[3].hasRemaining()) {
                remaining(deadline);
                if (channel.write(buffers) == 0)
                    await(selector, key, SelectionKey.OP_WRITE, deadline);
            }
            var reply = ByteBuffer.allocate(1024);
            while (reply.hasRemaining()) {
                remaining(deadline);
                int start = reply.position();
                int count = channel.read(reply);
                if (count < 0) throw new IOException("Truncated scanner response");
                for (int i = start; i < reply.position(); i++) {
                    if (reply.get(i) == 0) {
                        return new String(reply.array(), 0, i, StandardCharsets.US_ASCII);
                    }
                }
                if (count == 0) await(selector, key, SelectionKey.OP_READ, deadline);
            }
            throw new IOException("Oversized scanner response");
        }
    }

    private static long remaining(long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new java.net.SocketTimeoutException("Upload scanner deadline");
        return nanos;
    }

    private static void await(Selector selector, SelectionKey key, int operation, long deadline)
            throws IOException {
        key.interestOps(operation);
        selector.selectedKeys().clear();
        selector.select(TimeUnit.NANOSECONDS.toMillis(remaining(deadline)) + 1);
    }

    private void result(String result) {
        metrics.counter("fcms.upload.scan", "result", result).increment();
    }
}
