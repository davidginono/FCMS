package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.service.PasswordRecoveryService;
import com.foresight.fcms.notifications.MailDispatcher;
import com.foresight.fcms.notifications.service.MailOutboxService;

import io.micrometer.core.instrument.MeterRegistry;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(MailDeliveryTests.LocalMail.class)
class MailDeliveryTests {
    static final String SCHEMA = "mail_delivery_" + UUID.randomUUID().toString().replace("-", "");
    static final SmtpSink SMTP = createSink();
    static final AtomicBoolean TRANSPORT_IN_TRANSACTION = new AtomicBoolean();
    static final AtomicInteger TRANSPORT_CALLS = new AtomicInteger();
    static final Map<String, String> RETAINED = new LinkedHashMap<>();

    static SmtpSink createSink() {
        try {
            return new SmtpSink();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("fcms.mail.interval", () -> "3600000");
        p.add("fcms.mail.initial-delay", () -> "3600000");
        p.add("fcms.backup.interval", () -> "3600000");
        p.add("fcms.mail.from", () -> "noreply@test.invalid");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalMail {
        @Bean
        FlywayMigrationStrategy retainedMail() {
            return flyway -> {
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .target("21")
                        .load()
                        .migrate();
                var db = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                for (String state : List.of("SENT", "FAILED", "PENDING", "SENDING")) {
                    db.update(
                            "insert into"
                                + " mail_outbox(recipient,subject,body,status,attempts,available_at)"
                                + " values ('migration@test.invalid',?,'Synthetic retained"
                                + " secret',?,?,current_timestamp+interval '1 day')",
                            state,
                            state,
                            state.equals("FAILED") ? 5 : 1);
                }
                db.query(
                        "select subject,(to_jsonb(m)-'body')::text from mail_outbox m order by id",
                        r -> {
                            RETAINED.put(r.getString(1), r.getString(2));
                        });
                flyway.migrate();
            };
        }

        @Bean
        @Primary
        JavaMailSender localSmtpTransport() {
            var sender =
                    new JavaMailSenderImpl() {
                        @Override
                        public void send(SimpleMailMessage message) {
                            TRANSPORT_CALLS.incrementAndGet();
                            if (TransactionSynchronizationManager.isActualTransactionActive())
                                TRANSPORT_IN_TRANSACTION.set(true);
                            super.send(message);
                        }
                    };
            sender.setHost("127.0.0.1");
            sender.setPort(SMTP.port());
            var properties = new Properties();
            properties.setProperty("mail.smtp.auth", "false");
            properties.setProperty("mail.smtp.starttls.enable", "false");
            properties.setProperty("mail.smtp.connectiontimeout", "1000");
            properties.setProperty("mail.smtp.timeout", "1000");
            properties.setProperty("mail.smtp.writetimeout", "1000");
            sender.setJavaMailProperties(properties);
            return sender;
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired PasswordRecoveryService recovery;
    @Autowired MailDispatcher dispatcher;
    @Autowired MailOutboxService outbox;
    @Autowired PasswordEncoder passwords;
    @Autowired MeterRegistry metrics;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    @BeforeEach
    void prepare() {
        SMTP.mode = Mode.ACCEPT;
        SMTP.received.clear();
        TRANSPORT_CALLS.set(0);
        TRANSPORT_IN_TRANSACTION.set(false);
        SMTP.connections.set(0);
        SMTP.mailFrom.set(0);
    }

    @AfterEach
    void protocolHealthy() {
        assertNull(SMTP.failure.get());
        assertFalse(TRANSPORT_IN_TRANSACTION.get());
    }

    @AfterAll
    void closeReceiver() throws Exception {
        SMTP.close();
    }

    record Pending(long id, String recipient, long user) {}

    Pending recoveryMail() {
        String recipient = "smtp-" + UUID.randomUUID() + "@test.invalid";
        long user =
                db.queryForObject(
                        "insert into users(first_name,last_name,email,password,status_id,role_id)"
                                + " values ('SMTP','Fixture',?,?,1,2) returning id",
                        Long.class,
                        recipient,
                        passwords.encode("BeforeDelivery123!"));
        recovery.request(recipient);
        return new Pending(
                db.queryForObject(
                        "select id from mail_outbox where recipient=?", Long.class, recipient),
                recipient,
                user);
    }

    String status(long id) {
        return db.queryForObject("select status from mail_outbox where id=?", String.class, id);
    }

    String body(long id) {
        return db.queryForObject("select body from mail_outbox where id=?", String.class, id);
    }

    int attempts(long id) {
        return db.queryForObject("select attempts from mail_outbox where id=?", Integer.class, id);
    }

    @Test
    @Order(1)
    void migrationRedactsOnlyTerminalBodiesAndRetainsEveryMetadataField() {
        for (String state : RETAINED.keySet()) {
            assertEquals(
                    RETAINED.get(state),
                    db.queryForObject(
                            "select (to_jsonb(m)-'body')::text from mail_outbox m where subject=?",
                            String.class,
                            state));
            assertEquals(
                    Set.of("SENT", "FAILED").contains(state) ? "" : "Synthetic retained secret",
                    db.queryForObject(
                            "select body from mail_outbox where subject=?", String.class, state));
        }
    }

    @Test
    @Order(2)
    void recoveryEmailTravelsThroughActualSmtpAndItsBodyIsClearedAfterAcknowledgement()
            throws Exception {
        var pending = recoveryMail();
        assertEquals("PENDING", status(pending.id()));
        double sent = metrics.counter("fcms.mail.sent").count();
        dispatcher.deliver();
        var received = SMTP.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertTrue(received.from().contains("noreply@test.invalid"));
        assertTrue(received.recipient().contains(pending.recipient()));
        assertTrue(received.data().contains("Subject: FCMS password recovery"));
        var token = Pattern.compile("Token: ([A-Za-z0-9_-]+)").matcher(received.data());
        var otp = Pattern.compile("OTP: ([0-9]{6})").matcher(received.data());
        assertTrue(token.find());
        assertTrue(otp.find());
        assertEquals("SENT", status(pending.id()));
        assertEquals("", body(pending.id()));
        assertEquals(1, attempts(pending.id()));
        assertNotNull(
                db.queryForObject(
                        "select sent_at from mail_outbox where id=?",
                        java.sql.Timestamp.class,
                        pending.id()));
        assertEquals(sent + 1, metrics.counter("fcms.mail.sent").count());
        assertEquals(1, TRANSPORT_CALLS.get());
        assertTrue(recovery.reset(token.group(1), otp.group(1), "AfterDeliveryPassword123!"));
        assertTrue(
                passwords.matches(
                        "AfterDeliveryPassword123!",
                        db.queryForObject(
                                "select password from users where id=?",
                                String.class,
                                pending.user())));
        assertFalse(recovery.reset(token.group(1), otp.group(1), "ReplayNotAccepted123!"));
        dispatcher.deliver();
        assertEquals(1, TRANSPORT_CALLS.get());
        assertTrue(SMTP.received.isEmpty());
    }

    @Test
    @Order(3)
    void smtpRejectionRetriesFiveTimesThenRedactsTheBodyAndStops() {
        var pending = recoveryMail();
        SMTP.mode = Mode.REJECT;
        double failed = metrics.counter("fcms.mail.failed").count();
        for (int n = 1; n <= 5; n++) {
            db.update(
                    "update mail_outbox set available_at=current_timestamp-interval '1 second'"
                            + " where id=?",
                    pending.id());
            dispatcher.deliver();
            assertEquals(n, attempts(pending.id()));
            assertEquals(n == 5 ? "FAILED" : "PENDING", status(pending.id()));
            if (n < 5) assertFalse(body(pending.id()).isEmpty());
        }
        assertEquals("", body(pending.id()));
        assertEquals(failed + 5, metrics.counter("fcms.mail.failed").count());
        dispatcher.deliver();
        assertEquals(5, TRANSPORT_CALLS.get());
        assertTrue(SMTP.received.isEmpty());
        assertEquals(
                pending.recipient(),
                db.queryForObject(
                        "select recipient from mail_outbox where id=?",
                        String.class,
                        pending.id()));
    }

    @Test
    @Order(4)
    void smtpTimeoutReturnsTheLeaseToRetryWithoutHoldingADatabaseTransaction() {
        var pending = recoveryMail();
        SMTP.mode = Mode.SILENT;
        long start = System.nanoTime();
        dispatcher.deliver();
        assertTrue(
                Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);
        assertEquals("PENDING", status(pending.id()));
        assertEquals(1, attempts(pending.id()));
        assertFalse(body(pending.id()).isEmpty());
        assertEquals(1, TRANSPORT_CALLS.get());
        assertTrue(SMTP.received.isEmpty());
    }

    @Test
    @Order(5)
    void concurrentWorkersClaimDisjointBoundedBatchesAndRejectStaleAcknowledgements()
            throws Exception {
        var ids = new HashSet<Long>();
        for (int i = 0; i < 12; i++)
            ids.add(
                    db.queryForObject(
                            "insert into mail_outbox(recipient,subject,body) values"
                                    + " ('batch@test.invalid','Synthetic batch','Synthetic body')"
                                    + " returning id",
                            Long.class));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = new ArrayList<Future<List<MailOutboxService.Mail>>>();
            for (int i = 0; i < 2; i++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return outbox.claim();
                                }));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var a = jobs.get(0).get(10, TimeUnit.SECONDS);
            var b = jobs.get(1).get(10, TimeUnit.SECONDS);
            assertTrue(a.size() <= 10);
            assertTrue(b.size() <= 10);
            assertEquals(12, a.size() + b.size());
            var claimed = new HashSet<Long>();
            for (var mail : java.util.stream.Stream.concat(a.stream(), b.stream()).toList()) {
                assertTrue(claimed.add(mail.id()));
                assertEquals(1, mail.attempt());
            }
            assertEquals(ids, claimed);
        }
        long id = ids.iterator().next();
        db.update(
                "update mail_outbox set available_at=current_timestamp-interval '1 second' where"
                        + " id=?",
                id);
        var reclaimed = outbox.claim();
        assertEquals(1, reclaimed.size());
        assertEquals(2, reclaimed.getFirst().attempt());
        outbox.sent(id, 1);
        assertEquals("SENDING", status(id));
        assertFalse(body(id).isEmpty());
        outbox.failed(id, 2);
        assertEquals("PENDING", status(id));
        assertFalse(body(id).isEmpty());
        assertEquals(0, TRANSPORT_CALLS.get());
    }

    @Test
    @Order(6)
    void exhaustedCrashLeaseIsRedactedAndCannotBeAcknowledgedLater() {
        long id =
                db.queryForObject(
                        "insert into"
                            + " mail_outbox(recipient,subject,body,status,attempts,available_at)"
                            + " values ('expired@test.invalid','Expired fixture','Synthetic"
                            + " secret','SENDING',5,current_timestamp-interval '1 second')"
                            + " returning id",
                        Long.class);
        assertTrue(outbox.claim().isEmpty());
        assertEquals("FAILED", status(id));
        assertEquals("", body(id));
        outbox.sent(id, 5);
        assertEquals("FAILED", status(id));
        assertNull(
                db.queryForObject(
                        "select sent_at from mail_outbox where id=?",
                        java.sql.Timestamp.class,
                        id));
    }

    @Test
    @Order(7)
    void kiswahiliHttpRequestStoresAndDeliversLocalizedMailWithoutAccountEnumeration()
            throws Exception {
        String email = "smtp-sw-" + UUID.randomUUID() + "@test.invalid";
        db.update(
                "insert into users(first_name,last_name,email,password,status_id,role_id) select"
                        + " 'SMTP','Locale',?,password,1,2 from users where id=1",
                email);
        var http =
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .cookieHandler(
                                new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                        .build();
        String base = "http://127.0.0.1:" + port;
        var form =
                http.send(
                        java.net.http.HttpRequest.newBuilder(
                                        URI.create(base + "/password/forgot?lang=sw"))
                                .timeout(Duration.ofSeconds(30))
                                .GET()
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, form.statusCode());
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(form.body());
        assertTrue(csrf.find());
        String payload =
                "_csrf="
                        + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8)
                        + "&email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8);
        var request =
                java.net.http.HttpRequest.newBuilder(URI.create(base + "/password/forgot"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded");
        var accepted =
                http.send(
                        request.POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, accepted.statusCode());
        long id =
                db.queryForObject(
                        "select id from mail_outbox where recipient=?", Long.class, email);
        assertEquals(
                "Kurejesha nenosiri la FCMS",
                db.queryForObject("select subject from mail_outbox where id=?", String.class, id));
        assertTrue(body(id).contains("Tokeni: "));
        assertTrue(body(id).contains("Nambari ya uthibitisho: "));
        assertTrue(body(id).contains("Muda: dakika 15."));
        String unknown = "unknown-" + UUID.randomUUID() + "@test.invalid";
        payload =
                "_csrf="
                        + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8)
                        + "&email="
                        + URLEncoder.encode(unknown, StandardCharsets.UTF_8);
        var hidden =
                http.send(
                        request.POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(accepted.statusCode(), hidden.statusCode());
        assertEquals(
                accepted.headers().firstValue("location"), hidden.headers().firstValue("location"));
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from mail_outbox where recipient=?",
                        Integer.class,
                        unknown));
        dispatcher.deliver();
        var received = SMTP.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertTrue(received.data().contains("Subject: Kurejesha nenosiri la FCMS"));
        assertTrue(received.data().contains("Nambari ya uthibitisho: "));
        assertEquals("SENT", status(id));
        assertEquals("", body(id));
    }

    @Test
    @Order(8)
    void productionMailDefaultsRefusePlaintextRelayBeforeSendingTheEnvelopeOrBody() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(
                        org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                org.springframework.boot.mail.autoconfigure
                                        .MailSenderAutoConfiguration.class))
                .withPropertyValues(
                        "spring.mail.host=127.0.0.1",
                        "spring.mail.port=" + SMTP.port(),
                        "spring.mail.username=",
                        "spring.mail.password=",
                        "spring.mail.properties.mail.smtp.auth=false")
                .withInitializer(
                        context -> {
                            var loader =
                                    new org.springframework.boot.env.YamlPropertySourceLoader();
                            try {
                                for (String name :
                                        List.of("application-prod.yml", "application.yml"))
                                    for (var source :
                                            loader.load(
                                                    name,
                                                    new org.springframework.core.io
                                                            .ClassPathResource(name)))
                                        context.getEnvironment()
                                                .getPropertySources()
                                                .addLast(source);
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var sender = context.getBean(JavaMailSenderImpl.class);
                            assertEquals(
                                    "true",
                                    sender.getJavaMailProperties()
                                            .getProperty("mail.smtp.ssl.checkserveridentity"));
                            var message = new SimpleMailMessage();
                            message.setFrom("noreply@test.invalid");
                            message.setTo("tls-test@test.invalid");
                            message.setSubject("TLS fixture");
                            message.setText("Synthetic body");
                            var denied =
                                    assertThrows(
                                            org.springframework.mail.MailSendException.class,
                                            () -> sender.send(message));
                            assertTrue(denied.getMessage().contains("STARTTLS"));
                            assertTrue(SMTP.connections.get() > 0);
                            assertEquals(0, SMTP.mailFrom.get());
                            assertTrue(SMTP.received.isEmpty());
                        });
    }

    enum Mode {
        ACCEPT,
        REJECT,
        SILENT
    }

    record Received(String from, String recipient, String data) {}

    static final class SmtpSink implements AutoCloseable {
        final ServerSocket listener = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        final BlockingQueue<Received> received = new ArrayBlockingQueue<>(4);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger mailFrom = new AtomicInteger();
        final ExecutorService worker =
                Executors.newSingleThreadExecutor(
                        Thread.ofPlatform().daemon().name("fixture-smtp").factory());
        volatile Mode mode = Mode.ACCEPT;
        volatile Socket active;

        SmtpSink() throws IOException {
            worker.submit(this::accept);
        }

        int port() {
            return listener.getLocalPort();
        }

        void accept() {
            while (!listener.isClosed()) {
                try (var socket = listener.accept()) {
                    active = socket;
                    serve(socket);
                } catch (SocketException e) {
                    if (!listener.isClosed() && !e.getMessage().contains("reset"))
                        failure.compareAndSet(null, e);
                } catch (IOException | RuntimeException e) {
                    if (!listener.isClosed()) failure.compareAndSet(null, e);
                } finally {
                    active = null;
                }
            }
        }

        void serve(Socket socket) throws IOException {
            connections.incrementAndGet();
            socket.setSoTimeout(5000);
            if (mode == Mode.SILENT) {
                while (socket.getInputStream().read() != -1) {}
                return;
            }
            var input =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(), StandardCharsets.US_ASCII));
            var output =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.US_ASCII));
            reply(output, "220 fixture SMTP");
            String from = "", recipient = "", line;
            while ((line = input.readLine()) != null) {
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO"))
                    reply(output, "250 fixture");
                else if (command.startsWith("MAIL FROM:")) {
                    mailFrom.incrementAndGet();
                    from = line.substring(10).trim();
                    reply(output, "250 sender");
                } else if (command.startsWith("RCPT TO:")) {
                    recipient = line.substring(8).trim();
                    reply(
                            output,
                            mode == Mode.REJECT
                                    ? "450 temporary fixture rejection"
                                    : "250 recipient");
                } else if (command.equals("DATA")) {
                    reply(output, "354 end with dot");
                    var data = new StringBuilder();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        data.append(line.startsWith("..") ? line.substring(1) : line).append('\n');
                        if (data.length() > 32768)
                            throw new IOException("Fixture message exceeded its bound");
                    }
                    if (!received.offer(new Received(from, recipient, data.toString())))
                        throw new IOException("Fixture queue exceeded its bound");
                    reply(output, "250 accepted");
                } else if (command.equals("QUIT")) {
                    reply(output, "221 closing");
                    return;
                } else if (command.equals("RSET") || command.equals("NOOP"))
                    reply(output, "250 ok");
                else reply(output, "500 unsupported fixture command");
            }
        }

        void reply(BufferedWriter out, String line) throws IOException {
            out.write(line);
            out.write("\r\n");
            out.flush();
        }

        @Override
        public void close() throws Exception {
            listener.close();
            Socket socket = active;
            if (socket != null) socket.close();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
