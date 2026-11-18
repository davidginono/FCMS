package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.saccos.dto.*;
import com.foresight.fcms.client.saccos.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fcms.mail.initial-delay=3600000",
            "fcms.mail.interval=3600000",
            "fcms.backup.interval=3600000"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
class SaccosImportIntegrationTests {
    static final String SCHEMA = "csv_import_" + UUID.randomUUID().toString().replace("-", "");
    static final String PASSWORD = "ImportVerification123!";
    static final String HEADER =
            "Saccos_Name,Entry_Date,Region,financial_year,payment_status,Amount,Annual_fee\r\n";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired SaccosImportService imports;
    @Autowired SaccosService saccos;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    String email;
    long owner;

    @BeforeEach
    void owner() {
        OperationalTestClaims.enable(db);
        email = "csv-" + UUID.randomUUID() + "@test.invalid";
        owner = user(email, 1);
        actor(email);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long user(String address, int role) {
        return db.queryForObject(
                "insert into users(first_name,last_name,email,password,status_id,role_id) values"
                        + " ('Synthetic','Importer',?,?,1,?) returning id",
                Long.class,
                address,
                passwords.encode(PASSWORD),
                role);
    }

    void actor(String address) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(address, "", List.of()));
    }

    String row(String name) {
        return name + ",2026-11-01,Mwanza,2026,PAID,100.11,200.25\r\n";
    }

    MockMultipartFile file(String data) {
        return new MockMultipartFile(
                "file", "clients.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8));
    }

    UUID stage(String data) {
        return imports.stage(file(data), UUID.randomUUID());
    }

    long count(String table) {
        return db.queryForObject("select count(*) from " + table, Long.class);
    }

    long batch(UUID token) {
        return db.queryForObject(
                "select id from saccos_import_batches where token=?", Long.class, token);
    }

    @Test
    void previewAndImportRetainAllRowsExactTotalsMissingDatesAndCanonicalAudit() {
        long before = count("saccos");
        var token = stage(HEADER + row("First Client") + row("Second Client"));
        var q = new RegisterQuery();
        q.setSize(10);
        var preview = imports.preview(token, q);
        assertEquals(before, count("saccos"));
        assertEquals(2, preview.rowCount());
        assertTrue(preview.importable());
        assertEquals(new BigDecimal("400.50"), preview.annualFee());
        assertEquals(new BigDecimal("200.22"), preview.paid());
        assertTrue(preview.records().rows().stream().allMatch(r -> r.clientId() == null));
        assertTrue(
                preview.records().rows().getFirst().facts().stream()
                        .anyMatch(f -> f.label().equals("client.datePaid") && f.value().isEmpty()));
        assertTrue(imports.commit(token));
        var result = imports.preview(token, q);
        assertEquals("COMMITTED", result.state());
        assertFalse(result.importable());
        assertEquals(before + 2, count("saccos"));
        for (var row : result.records().rows()) {
            assertNotNull(row.clientId());
            assertEquals(
                    new BigDecimal("100.11"),
                    db.queryForObject(
                            "select amount from saccos where id=?",
                            BigDecimal.class,
                            row.clientId()));
            assertNull(
                    db.queryForObject(
                            "select date_paid from saccos where id=?",
                            java.sql.Date.class,
                            row.clientId()));
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from audit_events where actor_id=? and"
                                + " action='client.create' and resource='saccos' and resource_id=?",
                            Integer.class,
                            owner,
                            row.clientId()));
        }
        assertEquals(
                2,
                db.queryForObject(
                        "select count(*) from saccos_import_clients where batch_id=?",
                        Integer.class,
                        batch(token)));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from audit_events where action='client.import.commit' and"
                                + " resource_id=?",
                        Integer.class,
                        batch(token)));
    }

    @Test
    void archivedImportedClientRetainsItsReviewedFactsWithoutAnUnavailableDetailsLink() {
        var token = stage(HEADER + row("Retained Archived Import"));
        assertTrue(imports.commit(token));
        long id =
                imports.preview(token, new RegisterQuery()).records().rows().getFirst().clientId();
        saccos.hideSaccos(id);
        var preview = imports.preview(token, new RegisterQuery());
        var row = preview.records().rows().getFirst();
        assertEquals("COMMITTED", preview.state());
        assertNull(row.clientId());
        assertEquals("Retained Archived Import", row.name());
        assertEquals(new BigDecimal("100.11"), row.paid());
        assertTrue(imports.commit(token));
        assertEquals(
                id,
                db.queryForObject(
                        "select client_id from saccos_import_clients where batch_id=?",
                        Long.class,
                        batch(token)));
    }

    @Test
    void invalidRowsReferencesAndFileTypesNeverStageOrCreateClients() {
        long clients = count("saccos"),
                batches = count("saccos_import_batches"),
                audit = count("audit_events");
        for (String data :
                List.of(
                        HEADER + row("Private Name").replace("100.11", "-0.01"),
                        HEADER + row("Private Name").replace(",2026,", ",9999,"),
                        HEADER + row("Private Name").replace("2026-11-01", "2026-02-29")))
            assertThrows(IllegalArgumentException.class, () -> stage(data));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        imports.stage(
                                new MockMultipartFile(
                                        "file",
                                        "payload.csv",
                                        "application/pdf",
                                        (HEADER + row("Client")).getBytes(StandardCharsets.UTF_8)),
                                UUID.randomUUID()));
        assertEquals(clients, count("saccos"));
        assertEquals(batches, count("saccos_import_batches"));
        assertEquals(audit, count("audit_events"));
    }

    @Test
    void bothUploadAndCommitRetriesReturnOneRetainedResult() {
        var request = UUID.randomUUID();
        String data = HEADER + row("Retry Client");
        long before = count("saccos");
        var token = imports.stage(file(data), request);
        assertEquals(token, imports.stage(file(data), request));
        assertThrows(
                IllegalArgumentException.class,
                () -> imports.stage(file(data.replace("100.11", "100.12")), request));
        assertTrue(imports.commit(token));
        var ids =
                imports.preview(token, new RegisterQuery()).records().rows().stream()
                        .map(SaccosImportService.Row::clientId)
                        .toList();
        assertTrue(imports.commit(token));
        assertEquals(
                ids,
                imports.preview(token, new RegisterQuery()).records().rows().stream()
                        .map(SaccosImportService.Row::clientId)
                        .toList());
        assertEquals(before + 1, count("saccos"));
        assertEquals(token, imports.stage(file(data), request));
    }

    @Test
    void claimsAreIndependentOfRoleNamesAndTokensCannotCrossOwners() {
        var token = stage(HEADER + row("Owned Client"));
        long before = count("saccos");
        String other = "other-" + UUID.randomUUID() + "@test.invalid";
        long id = user(other, 2);
        actor(other);
        assertThrows(
                AccessDeniedException.class, () -> imports.preview(token, new RegisterQuery()));
        db.update("insert into user_claims(user_id,claim) values (?,'client.import')", id);
        assertThrows(AccessDeniedException.class, () -> imports.formAccess());
        db.update("insert into user_claims(user_id,claim) values (?,'client.write')", id);
        db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin'),(?,'client.read')", id, id);
        assertDoesNotThrow(() -> imports.formAccess());
        assertThrows(
                IllegalArgumentException.class, () -> imports.preview(token, new RegisterQuery()));
        assertThrows(IllegalArgumentException.class, () -> imports.commit(token));
        assertThrows(IllegalArgumentException.class, () -> imports.discard(token));
        assertTrue(imports.drafts().isEmpty());
        assertEquals(before, count("saccos"));
        UUID own = stage(HEADER + row("Custom Claims Client"));
        assertTrue(imports.commit(own));
        actor(email);
        db.update("update users set status_id=2 where id=?", owner);
        assertThrows(AccessDeniedException.class, () -> imports.commit(token));
    }

    @Test
    void concurrentCommitsCreateExactlyOneBatchResult() throws Exception {
        var token = stage(HEADER + row("Concurrent Client"));
        long before = count("saccos");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> command =
                    () -> {
                        actor(email);
                        try {
                            barrier.await(10, TimeUnit.SECONDS);
                            return imports.commit(token);
                        } finally {
                            clear();
                        }
                    };
            var first = executor.submit(command);
            var second = executor.submit(command);
            assertTrue(first.get(30, TimeUnit.SECONDS));
            assertTrue(second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(before + 1, count("saccos"));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from audit_events where action='client.import.commit' and"
                                + " resource_id=?",
                        Integer.class,
                        batch(token)));
    }

    @Test
    void reviewedReferencesAndClientVisibilityStayStableUntilTheWholeTransactionCommits()
            throws Exception {
        var token = stage(HEADER + row("Locked Reference Client"));
        long clients = count("saccos");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var updateStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var commit =
                    executor.submit(() -> {
                        actor(email);
                        try {
                            return new org.springframework.transaction.support.TransactionTemplate(
                                            transactions)
                                    .execute(status -> {
                                        boolean result = imports.commit(token);
                                        locked.countDown();
                                        try {
                                            if (!release.await(10, TimeUnit.SECONDS))
                                                throw new IllegalStateException(
                                                        "Fixture release timeout");
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException("Fixture interrupted");
                                        }
                                        return result;
                                    });
                        } finally {
                            clear();
                        }
                    });
            Future<Integer> rename = null;
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS));
                assertEquals(clients, count("saccos"));
                assertEquals("DRAFT", imports.preview(token, new RegisterQuery()).state());
                rename =
                        executor.submit(() -> {
                            updateStarted.countDown();
                            return db.update(
                                    "/* csv_import_ref_wait */ update financial_year set"
                                            + " fyear='2026 after commit' where id=1");
                        });
                assertTrue(updateStarted.await(5, TimeUnit.SECONDS));
                boolean waiting = false;
                for (int i = 0; i < 40 && !waiting; i++) {
                    waiting =
                            Boolean.TRUE.equals(
                                    db.queryForObject(
                                            "select exists(select 1 from pg_stat_activity where"
                                                    + " datname=current_database() and"
                                                    + " wait_event_type='Lock' and query like '/*"
                                                    + " csv_import_ref_wait */%')",
                                            Boolean.class));
                    if (!waiting) Thread.sleep(50);
                }
                assertTrue(
                        waiting,
                        "The reference writer must wait on the import transaction's database lock");
                assertFalse(rename.isDone());
            } finally {
                release.countDown();
            }
            assertTrue(commit.get(20, TimeUnit.SECONDS));
            assertNotNull(rename);
            assertEquals(1, rename.get(20, TimeUnit.SECONDS));
        } finally {
            db.update("update financial_year set fyear='2026' where id=1");
        }
        assertEquals(clients + 1, count("saccos"));
        assertEquals("COMMITTED", imports.preview(token, new RegisterQuery()).state());
    }

    @Test
    void aLateCanonicalFailureRollsBackEveryClientAssociationAndAudit() {
        var token = stage(HEADER + row("First Atomic Client") + row("FAIL-LAST-IMPORT"));
        long clients = count("saccos"), audit = count("audit_events");
        db.execute(
                "create function csv_fixture_failure() returns trigger language plpgsql as $$ begin"
                        + " if new.saccos_name='FAIL-LAST-IMPORT' then raise exception 'Synthetic"
                        + " failure' using errcode='23514'; end if;return new;end;$$");
        db.execute(
                "create trigger csv_fixture_failure before insert on saccos for each row execute"
                        + " function csv_fixture_failure()");
        try {
            assertThrows(
                    org.springframework.dao.DataAccessException.class, () -> imports.commit(token));
            assertEquals(clients, count("saccos"));
            assertEquals(audit, count("audit_events"));
            assertEquals(
                    0,
                    db.queryForObject(
                            "select count(*) from saccos_import_clients where batch_id=?",
                            Integer.class,
                            batch(token)));
            assertEquals("DRAFT", imports.preview(token, new RegisterQuery()).state());
        } finally {
            db.execute("drop trigger csv_fixture_failure on saccos");
            db.execute("drop function csv_fixture_failure()");
        }
        assertTrue(imports.commit(token));
        assertEquals(clients + 2, count("saccos"));
    }

    @Test
    void expiryAndDiscardClearOnlyDraftPayloadAndNeverCreateClients() {
        UUID original = stage(HEADER + row("Expiry Client")), expired = UUID.randomUUID();
        long before = count("saccos");
        db.update(
                "insert into"
                    + " saccos_import_batches(token,owner_id,request_key,digest,row_count,payload,total_annual_fee,total_paid,created_at,expires_at)"
                    + " select"
                    + " ?,owner_id,?,digest,row_count,payload,total_annual_fee,total_paid,current_timestamp-interval"
                    + " '26 hours',current_timestamp-interval '1 hour' from saccos_import_batches"
                    + " where token=?",
                expired,
                UUID.randomUUID(),
                original);
        assertFalse(imports.preview(expired, new RegisterQuery()).importable());
        assertFalse(imports.commit(expired));
        assertEquals("EXPIRED", imports.preview(expired, new RegisterQuery()).state());
        assertEquals(
                "[]",
                db.queryForObject(
                        "select payload::text from saccos_import_batches where token=?",
                        String.class,
                        expired));
        imports.discard(original);
        imports.discard(original);
        var closed = imports.preview(original, new RegisterQuery());
        assertEquals("CANCELLED", closed.state());
        assertTrue(closed.records().rows().isEmpty());
        assertEquals(new BigDecimal("100.11"), closed.paid());
        assertThrows(IllegalArgumentException.class, () -> imports.commit(original));
        assertEquals(before, count("saccos"));
    }

    @Test
    void referenceChangesAndDatabaseTamperingCannotChangeTheReviewedCommand() {
        var token = stage(HEADER + row("Reviewed Client"));
        long before = count("saccos");
        db.update("update financial_year set fyear='2026 changed' where id=1");
        try {
            assertThrows(IllegalArgumentException.class, () -> imports.commit(token));
            assertEquals(before, count("saccos"));
        } finally {
            db.update("update financial_year set fyear='2026' where id=1");
        }
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update saccos_import_batches set total_paid=0 where token=?",
                                token));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update saccos_import_batches set"
                                        + " state='COMMITTED',committed_at=current_timestamp where"
                                        + " token=?",
                                token));
        assertTrue(imports.commit(token));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.update("delete from saccos_import_batches where token=?", token));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "update saccos_import_clients set ordinal=2 where batch_id=?",
                                batch(token)));
        assertThrows(IllegalArgumentException.class, () -> imports.discard(token));
    }

    @Test
    void paginationDoesNotChangeWholeBatchTotalsOrTheCommitScopeAndDraftQuotaRecovers() {
        StringBuilder data = new StringBuilder(HEADER);
        for (int i = 0; i < 26; i++) data.append(row("Paged Client " + i));
        var token = stage(data.toString());
        var q = new RegisterQuery();
        q.setSize(10);
        q.setPage(2);
        var page = imports.preview(token, q);
        assertEquals(6, page.records().rows().size());
        assertEquals(new BigDecimal("2602.86"), page.paid());
        assertEquals(26, page.rowCount());
        assertTrue(imports.commit(token));
        assertEquals(
                26,
                db.queryForObject(
                        "select count(*) from saccos_import_clients where batch_id=?",
                        Integer.class,
                        batch(token)));
        var drafts = new ArrayList<UUID>();
        for (int i = 0; i < 10; i++) drafts.add(stage(HEADER + row("Quota Client " + i)));
        assertEquals(10, imports.drafts().size());
        assertThrows(IllegalArgumentException.class, () -> stage(HEADER + row("Blocked Draft")));
        imports.discard(drafts.getFirst());
        assertNotNull(stage(HEADER + row("Recovered Draft")));
        assertEquals(10, imports.drafts().size());
    }

    @Test
    void oversizedCsvReportsFailBeforeAnyAttachmentIsWrittenAndLegacyRoutesStayCanonical()
            throws Exception {
        String prefix = "ExportBound-" + UUID.randomUUID();
        for (String table : List.of("saccos", "amcos")) {
            String name = table.equals("saccos") ? "saccos_name" : "client_name";
            db.update(
                    "insert into "
                            + table
                            + " ("
                            + name
                            + ",region,financial_year_id,client_status_id)"
                            + " select ? || i::text,'Mwanza',1,1 from generate_series(1,1001) i",
                    prefix);
        }
        var web = browser();
        for (String family : List.of("saccos", "amcos")) {
            for (String kind : List.of("/export.csv")) {
                var response =
                        web.send(
                                request(
                                                "/clients/"
                                                        + family
                                                        + kind
                                                        + "?search="
                                                        + prefix
                                                        + "&financialYearId=1")
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(400, response.statusCode());
                assertTrue(
                        response.headers()
                                .firstValue("content-type")
                                .orElse("")
                                .contains("text/html"));
                assertTrue(response.headers().firstValue("content-disposition").isEmpty());
                assertFalse(response.body().contains("Saccos Name,Entry Date"));
            }
        }
        var upload =
                web.send(
                        request("/upload_tamplate.php").GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        assertEquals(302, upload.statusCode());
        assertTrue(
                upload.headers()
                        .firstValue("location")
                        .orElseThrow()
                        .endsWith("/clients/saccos/import"));
        var download =
                web.send(
                        request("/download_tamplate.php?fyear_id=1").GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        assertEquals(302, download.statusCode());
        assertTrue(
                download.headers()
                        .firstValue("location")
                        .orElseThrow()
                        .endsWith("/clients/saccos/export.csv?financialYearId=1"));
    }

    @Test
    void importedFormulaTextRemainsLiteralInEveryClientCsvWithoutChangingStoredNames()
            throws Exception {
        String name = "=1+1";
        var token = stage(HEADER + row(name));
        assertTrue(imports.commit(token));
        long id =
                imports.preview(token, new RegisterQuery()).records().rows().getFirst().clientId();
        assertEquals(
                name,
                db.queryForObject("select saccos_name from saccos where id=?", String.class, id));
        db.update(
                "insert into amcos(client_name,region,financial_year_id,client_status_id) values"
                    + " (?,'Mwanza',1,1)",
                name);
        var web = browser();
        for (String family : List.of("saccos", "amcos"))
            for (String kind : List.of("/export.csv")) {
                var response =
                        web.send(
                                request(
                                                "/clients/"
                                                        + family
                                                        + kind
                                                        + "?financialYearId=1&search=%3D1%2B1")
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("\"'=1+1\""), family + kind);
                assertFalse(response.body().contains("\"=1+1\""));
            }
    }

    HttpRequest.Builder request(String path) {
        var uri = URI.create("http://127.0.0.1:" + port + "/").resolve(path);
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(port, uri.getPort());
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60));
    }

    String value(String html, String name) {
        var m = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find(), name);
        return m.group(1);
    }

    HttpClient browser() throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        var login = web.send(request("/login").GET().build(), HttpResponse.BodyHandlers.ofString());
        String body =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                        + "&_csrf="
                        + URLEncoder.encode(value(login.body(), "_csrf"), StandardCharsets.UTF_8);
        assertEquals(
                302,
                web.send(
                                request("/login")
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build(),
                                HttpResponse.BodyHandlers.discarding())
                        .statusCode());
        return web;
    }

    HttpResponse<String> upload(HttpClient web, String csrf, String key, String csv)
            throws Exception {
        String boundary = "csv-" + UUID.randomUUID();
        var body = new StringBuilder();
        if (csrf != null)
            body.append("--")
                    .append(boundary)
                    .append("\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n")
                    .append(csrf)
                    .append("\r\n");
        body.append("--")
                .append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"requestKey\"\r\n\r\n")
                .append(key)
                .append("\r\n--")
                .append(boundary)
                .append(
                        "\r\n"
                                + "Content-Disposition: form-data; name=\"file\";"
                                + " filename=\"clients.csv\"\r\n"
                                + "Content-Type: text/csv\r\n\r\n")
                .append(csv)
                .append("\r\n--")
                .append(boundary)
                .append("--\r\n");
        return web.send(
                request("/clients/saccos/import")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body.toString(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void nativeMultipartPreviewCommitCsrfAndLocalizedViewsUseTheRetainedPayload() throws Exception {
        var web = browser();
        long before = count("saccos");
        var page =
                web.send(
                        request("/clients/saccos/import?lang=sw").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Kiolezo cha CSV"));
        String csrf = value(page.body(), "_csrf"), key = value(page.body(), "requestKey");
        assertEquals(403, upload(web, null, key, HEADER + row("Native Client")).statusCode());
        var invalid = upload(web, csrf, key, HEADER + row("Native Client").replace("100.11", "-1"));
        assertEquals(422, invalid.statusCode());
        assertTrue(invalid.body().contains("Amount"));
        assertEquals(before, count("saccos"));
        var uploaded = upload(web, csrf, key, HEADER + row("Native Client"));
        assertEquals(302, uploaded.statusCode());
        String destination = uploaded.headers().firstValue("location").orElseThrow();
        String location = URI.create(destination).getPath();
        var preview =
                web.send(request(destination).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, preview.statusCode());
        assertTrue(preview.body().contains("Native Client"));
        assertTrue(preview.body().contains("100.11"));
        assertEquals(before, count("saccos"));
        assertEquals(
                403,
                web.send(
                                request(location + "/commit")
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .build(),
                                HttpResponse.BodyHandlers.discarding())
                        .statusCode());
        var committed =
                web.send(
                        request(location + "/commit")
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "_csrf="
                                                        + URLEncoder.encode(
                                                                value(preview.body(), "_csrf"),
                                                                StandardCharsets.UTF_8)
                                                        + "&amount=999999&owner_id=999"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(302, committed.statusCode());
        assertEquals(before + 1, count("saccos"));
        var token = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        var result = imports.preview(token, new RegisterQuery());
        assertEquals(new BigDecimal("100.11"), result.paid());
        var downloaded =
                web.send(
                        request("/clients/saccos/import/template.csv").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, downloaded.statusCode());
        assertTrue(
                downloaded.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertTrue(downloaded.body().contains("financial_year,payment_status"));
        Files.createDirectories(Path.of(".verification"));
        Files.writeString(Path.of(".verification/csv-import-schema.txt"), SCHEMA);
    }
}
