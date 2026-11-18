package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.category.dto.*;
import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.category.service.ClientCategoryService;
import com.foresight.fcms.client.dto.ClientRegisterPresentation;
import com.foresight.fcms.client.saccos.service.SaccosService;
import com.foresight.fcms.client.service.ClientLifecycleService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fcms.mail.initial-delay=86400000",
            "fcms.mail.interval=86400000",
            "fcms.backup.interval=86400000",
            "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientRegisterParityPostgresTests {
    static final String SCHEMA = "clients_" + UUID.randomUUID().toString().replace("-", "");
    static final String CLERK = "client-clerk@test.invalid", STAFF = "client-staff@test.invalid";
    static final String CONTRACTS = ".verification/" + SCHEMA + "/contracts";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("fcms.storage.contract-dir", () -> CONTRACTS);
    }

    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ClientCategoryService categories;
    @Autowired SaccosService saccos;
    @Autowired ClientLifecycleService lifecycle;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    @BeforeAll
    void setup() {
        for (var role : Map.of(CLERK, "Accounts Clerk", STAFF, "Staff").entrySet())
            db.update(
                    "insert into users(first_name,last_name,email,password,status_id,role_id)"
                            + " values('Client','Test',?,?,1,(select role_id from roles where"
                            + " role_name=?))",
                    role.getKey(),
                    passwords.encode("Verification123!"),
                    role.getValue());
    }

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @BeforeEach
    void actor() {
        actor(CLERK);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    ClientCategoryForm form(ClientCategory category, String name) {
        var f = categories.newForm(category);
        f.setClientName(name);
        f.setRegion("Test");
        return f;
    }

    @Test
    void categoryFieldsArePersistedWithoutInventedAssignments() {
        for (var category : ClientCategory.values()) {
            var f = form(category, "Fields " + category);
            f.setWithholdingTax(new BigDecimal("22.11"));
            f.setCommission(new BigDecimal("33.22"));
            f.setProgrammerCost(new BigDecimal("44.33"));
            f.setBulkSmsFee(new BigDecimal("55.44"));
            f.setHostingFee(new BigDecimal("66.55"));
            f.setOperationalStatus("INACTIVE");
            f.setAnnualFee(new BigDecimal("999999999999.99"));
            long id = categories.createClient(category, f, null);
            var r = categories.findClient(category, id);
            assertEquals(0, r.annualFee().compareTo(f.getAnnualFee()));
            assertEquals(
                    category.isWithholdingTaxSupported() ? f.getWithholdingTax() : BigDecimal.ZERO,
                    r.withholdingTax().stripTrailingZeros());
            assertEquals(
                    category.isCommissionSupported() ? f.getCommission() : BigDecimal.ZERO,
                    r.commission().stripTrailingZeros());
            assertEquals(
                    category.isBulkSmsSupported() ? f.getBulkSmsFee() : BigDecimal.ZERO,
                    r.bulkSmsFee().stripTrailingZeros());
            assertEquals(
                    category.isHostingSupported() ? f.getHostingFee() : BigDecimal.ZERO,
                    r.hostingFee().stripTrailingZeros());
            if (category == ClientCategory.MICROFINANCE)
                assertEquals(0, r.programmerCost().signum());
            else assertEquals(0, r.programmerCost().compareTo(f.getProgrammerCost()));
            assertEquals(
                    category.isOperationalStatusSupported() ? "INACTIVE" : null,
                    r.operationalStatus());
            assertEquals(category.isFinancialYearSupported(), r.financialYearId() != null);
            var edit = categories.formFor(category, id);
            long year = 1L;
            edit.setFinancialYearId(year);
            edit.setOperationalStatus("ACTIVE");
            edit.setProgrammerCost(new BigDecimal("7.89"));
            categories.updateClient(category, id, edit, null);
            var updated = categories.findClient(category, id);
            assertEquals(
                    category.isFinancialYearEditable() ? Long.valueOf(year) : r.financialYearId(),
                    updated.financialYearId());
            assertEquals(0, updated.programmerCost().compareTo(new BigDecimal("7.89")));
            assertThrows(
                    OptimisticLockingFailureException.class,
                    () -> categories.updateClient(category, id, edit, null));
            if (category.isOperationalStatusSupported())
                assertTrue(
                        db.queryForObject(
                                "select exists(select 1 from audit_events where resource=? and"
                                        + " resource_id=? and action='client.status.update' and"
                                        + " detail='before=INACTIVE; after=ACTIVE')",
                                Boolean.class,
                                category.getTableName(),
                                id));
        }
    }

    @Test
    void othersIgnoreYearFiltersAndKeepHistoricValuesOutOfInputs() {
        long id =
                categories.createClient(
                        ClientCategory.OTHERS,
                        form(ClientCategory.OTHERS, "Other all dates"),
                        null);
        db.update("update other_clients set financial_year_id=1 where id=?", id);
        var f = new ClientCategoryFilter();
        f.setFinancialYearId(999999L);
        f.setSearch("Other all dates");
        var list = categories.findClients(ClientCategory.OTHERS, f, PageRequest.of(0, 10));
        assertEquals(1, list.page().getTotalElements());
        assertNull(f.getFinancialYearId());
        assertFalse(
                ClientRegisterPresentation.columns(ClientCategory.OTHERS).stream()
                        .anyMatch(c -> c.key().equals("financialYear")));
        var edit = categories.formFor(ClientCategory.OTHERS, id);
        edit.setFinancialYearId(999999L);
        categories.updateClient(ClientCategory.OTHERS, id, edit, null);
        assertEquals(
                1,
                db.queryForObject(
                        "select financial_year_id from other_clients where id=?", Long.class, id));
        var missing = form(ClientCategory.AMCOS, "Missing year");
        missing.setFinancialYearId(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> categories.createClient(ClientCategory.AMCOS, missing, null));
    }

    @Test
    void lifecycleSeparatesInactivityFromDeletionAndRetainsReferences() {
        for (var category : ClientCategory.values()) {
            var f = form(category, "Protected " + category);
            f.setAnnualFee(new BigDecimal("0.01"));
            long id = categories.createClient(category, f, null);
            var state = lifecycle.find(category.getSlug(), id);
            assertTrue(state.blocked());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> lifecycle.change(category.getSlug(), id, state.version(), "delete"));
            if (category.isOperationalStatusSupported()) {
                lifecycle.change(category.getSlug(), id, state.version(), "deactivate");
                assertEquals("INACTIVE", categories.findClient(category, id).operationalStatus());
                assertEquals(
                        f.getPaymentStatusId(),
                        categories.findClient(category, id).paymentStatusId());
                assertThrows(
                        OptimisticLockingFailureException.class,
                        () ->
                                lifecycle.change(
                                        category.getSlug(), id, state.version(), "deactivate"));
            } else assertFalse(state.canDeactivate());
            long unused =
                    categories.createClient(category, form(category, "Unused " + category), null);
            assertFalse(lifecycle.find(category.getSlug(), unused).blocked());
            lifecycle.change(category.getSlug(), unused, 0, "delete");
            assertEquals(
                    0,
                    db.queryForObject(
                            "select count(*) from " + category.getTableName() + " where id=?",
                            Integer.class,
                            unused));
        }
        var f = saccos.newForm();
        f.setSaccosName("Linked saccos");
        f.setRegion("Test");
        long id = saccos.createSaccos(f, null);
        db.execute(
                "create table client_parity_links(id bigint primary key,client_id bigint references"
                        + " saccos(id))");
        db.update("insert into client_parity_links values(1,?)", id);
        assertTrue(lifecycle.find("saccos", id).blocked());
        assertThrows(
                IllegalArgumentException.class, () -> lifecycle.change("saccos", id, 0, "delete"));
        lifecycle.change("saccos", id, 0, "deactivate");
        assertEquals("INACTIVE", saccos.findSaccos(id).operationalStatus());
        assertTrue(db.queryForObject("select visible from saccos where id=?", Boolean.class, id));
        var edit = saccos.formFor(id);
        long original = edit.getFinancialYearId();
        edit.setFinancialYearId(1L);
        edit.setOperationalStatus("ACTIVE");
        saccos.updateSaccos(id, edit, null);
        assertEquals(original, saccos.formFor(id).getFinancialYearId());
        actor(STAFF);
        assertThrows(AccessDeniedException.class, () -> lifecycle.find("saccos", id));
    }

    @Test
    void eachDeclaredColumnHasABoundedServerSort() {
        for (var category : ClientCategory.values()) {
            var filter = new ClientCategoryFilter();
            for (var column : ClientRegisterPresentation.columns(category)) {
                filter.setSort(column.key());
                for (String direction : List.of("asc", "desc")) {
                    filter.setDirection(direction);
                    assertTrue(
                            categories
                                            .findClients(category, filter, PageRequest.of(0, 10))
                                            .page()
                                            .getContent()
                                            .size()
                                    <= 10);
                }
            }
        }
        var filter = new com.foresight.fcms.client.saccos.dto.SaccosFilter();
        for (var column : ClientRegisterPresentation.saccosColumns()) {
            filter.setSort(column.key());
            assertTrue(
                    saccos.findSaccos(filter, PageRequest.of(0, 10, saccos.registerSort(filter)))
                                    .page()
                                    .getContent()
                                    .size()
                            <= 10);
        }
    }

    @Test
    void competingLifecycleCommandsCannotBothCommit() throws Exception {
        long id =
                categories.createClient(
                        ClientCategory.AMCOS,
                        form(ClientCategory.AMCOS, "Concurrent lifecycle"),
                        null);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (String command : List.of("delete", "deactivate"))
                results.add(
                        pool.submit(
                                () -> {
                                    actor(CLERK);
                                    ready.countDown();
                                    start.await();
                                    try {
                                        lifecycle.change("amcos", id, 0, command);
                                        return true;
                                    } catch (IllegalArgumentException
                                            | OptimisticLockingFailureException expected) {
                                        return false;
                                    } finally {
                                        clear();
                                    }
                                }));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (var result : results)
                if (result.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(
                    1,
                    db.queryForObject(
                            "select count(*) from audit_events where resource='amcos' and"
                                    + " resource_id=? and action in"
                                    + " ('client.delete','client.status.update')",
                            Integer.class,
                            id));
        }
    }

    MockMultipartFile pdf() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        var doc = new com.lowagie.text.Document();
        com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new com.lowagie.text.Paragraph("Synthetic contract"));
        doc.close();
        return new MockMultipartFile(
                "contractUpload", "contract.pdf", "application/pdf", out.toByteArray());
    }

    @Test
    void contractUpdatesAreIndependentVersionedAndRetainOldFiles() throws Exception {
        for (var category : ClientCategory.values()) {
            var f = form(category, "Contract " + category);
            f.setAnnualFee(new BigDecimal("123.45"));
            long id = categories.createClient(category, f, null);
            lifecycle.updateContract(category.getSlug(), id, 0, pdf());
            var r = categories.findClient(category, id);
            String old = r.contractFile();
            assertEquals(0, r.annualFee().compareTo(f.getAnnualFee()));
            assertTrue(categories.contract(category, id).exists());
            lifecycle.updateContract(category.getSlug(), id, 1, pdf());
            assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(CONTRACTS, old)));
            assertThrows(
                    OptimisticLockingFailureException.class,
                    () -> lifecycle.updateContract(category.getSlug(), id, 1, pdf()));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            lifecycle.updateContract(
                                    category.getSlug(),
                                    id,
                                    2,
                                    new MockMultipartFile(
                                            "contractUpload",
                                            "bad.pdf",
                                            "application/pdf",
                                            "invalid".getBytes())));
            assertEquals(2, lifecycle.find(category.getSlug(), id).version());
        }
    }

    @Test
    void nativeMultipartContractCommandsValidateFilesAndReturnFeedback() throws Exception {
        var web = session(CLERK);
        for (String slug : List.of("saccos", "amcos", "unions", "microfinance", "others")) {
            long id;
            if (slug.equals("saccos")) {
                var f = saccos.newForm();
                f.setSaccosName("Native contract");
                f.setRegion("Test");
                id = saccos.createSaccos(f, null);
            } else {
                var category = ClientCategory.fromSlug(slug);
                id =
                        categories.createClient(
                                category, form(category, "Native contract " + slug), null);
            }
            String path = "/clients/" + slug + "/" + id + "/contract";
            var edit = get(web, path + "/edit");
            assertEquals(200, edit.statusCode());
            var bad = contractPost(web, path, csrf(edit.body()), "invalid".getBytes());
            assertEquals(200, bad.statusCode());
            assertTrue(bad.body().contains("id=\"contractUpload-errors\""));
            var saved = contractPost(web, path, csrf(bad.body()), pdf().getBytes());
            assertEquals(302, saved.statusCode());
            var detail = get(web, saved.headers().firstValue("location").orElseThrow());
            assertEquals(200, detail.statusCode());
            assertTrue(detail.body().contains("Contract updated successfully."));
            assertEquals(200, get(web, path).statusCode());
            var stale =
                    contractPost(
                            web, path, csrf(get(web, path + "/edit").body()), pdf().getBytes());
            assertEquals(200, stale.statusCode());
            assertTrue(stale.body().contains("fcms-alert"));
            assertEquals(1, lifecycle.find(slug, id).version());
        }
    }

    HttpResponse<String> contractPost(HttpClient web, String path, String csrf, byte[] bytes)
            throws Exception {
        String boundary = "client-test-" + UUID.randomUUID();
        var body = new java.io.ByteArrayOutputStream();
        for (var field : Map.of("_csrf", csrf, "version", "0").entrySet())
            body.write(
                    ("--"
                                    + boundary
                                    + "\r\nContent-Disposition: form-data; name=\""
                                    + field.getKey()
                                    + "\"\r\n\r\n"
                                    + field.getValue()
                                    + "\r\n")
                            .getBytes(StandardCharsets.UTF_8));
        body.write(
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"contractUpload\";"
                                + " filename=\"contract.pdf\"\r\n"
                                + "Content-Type: application/pdf\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(HttpClient web, String path) throws Exception {
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port).resolve(path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String csrf(String html) {
        var m = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(m.find());
        return m.group(1);
    }

    HttpResponse<String> post(HttpClient web, String path, Map<String, String> fields)
            throws Exception {
        String data =
                fields.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(data))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpClient session(String email) throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var login = get(web, "/login");
        assertEquals(
                302,
                post(
                                web,
                                "/login",
                                Map.of(
                                        "email",
                                        email,
                                        "password",
                                        "Verification123!",
                                        "_csrf",
                                        csrf(login.body())))
                        .statusCode());
        return web;
    }

    @Test
    void nativeCreateEditRegistersAndLifecycleRenderForAllFiveCategories() throws Exception {
        var web = session(CLERK);
        for (String slug : List.of("saccos", "amcos", "unions", "microfinance", "others")) {
            String route = "/clients/" + slug;
            var create = get(web, route + "/new");
            assertEquals(200, create.statusCode(), slug);
            String html = create.body();
            boolean other = slug.equals("others");
            assertEquals(!other, html.contains("id=\"financialYearId\""));
            assertEquals(!other, html.contains("id=\"operationalStatus\""));
            assertEquals(other, html.contains("id=\"contractUpload\""));
            assertEquals(
                    slug.equals("saccos") || slug.equals("unions"),
                    html.contains("id=\"withholdingTax\""));
            assertEquals(!slug.equals("microfinance"), html.contains("id=\"programmerCost\""));
            assertFalse(html.contains("id=\"datePaid\""));
            assertFalse(html.contains("id=\"essFee\""));
            var fields = new LinkedHashMap<String, String>();
            fields.put("_csrf", csrf(html));
            fields.put(slug.equals("saccos") ? "saccosName" : "clientName", "HTTP " + slug);
            fields.put("region", "Test");
            fields.put("entryDate", "2026-11-01");
            fields.put("expireDate", "2026-10-31");
            fields.put("annualFee", "100.01");
            fields.put("financialYearId", "2");
            fields.put("paymentStatusId", "2");
            fields.put("operationalStatus", "ACTIVE");
            var invalid = post(web, route, fields);
            assertEquals(200, invalid.statusCode());
            assertTrue(invalid.body().contains("id=\"expireDate.errors\""), slug);
            assertTrue(invalid.body().contains("HTTP " + slug));
            fields.put("expireDate", "2027-11-01");
            fields.put("_csrf", csrf(invalid.body()));
            var saved = post(web, route, fields);
            assertEquals(302, saved.statusCode(), saved.body());
            String destination = saved.headers().firstValue("location").orElseThrow();
            assertTrue(destination.contains(route + "?"));
            assertTrue(destination.contains("_notice="));
            if (!other) assertTrue(destination.contains("financialYearId=2"));
            var list = get(web, destination);
            assertEquals(200, list.statusCode());
            assertTrue(list.body().contains("Client created successfully."), slug);
            String table =
                    slug.equals("saccos") ? "saccos" : ClientCategory.fromSlug(slug).getTableName();
            long id =
                    db.queryForObject(
                            "select id from "
                                    + table
                                    + " where "
                                    + (slug.equals("saccos") ? "saccos_name" : "client_name")
                                    + "=?",
                            Long.class,
                            "HTTP " + slug);
            assertEquals(200, get(web, route + "/" + id).statusCode());
            var edit = get(web, route + "/" + id + "/edit");
            assertEquals(200, edit.statusCode());
            assertEquals(
                    slug.equals("microfinance"), edit.body().contains("id=\"financialYearId\""));
            assertFalse(edit.body().contains("id=\"contractUpload\""));
            assertEquals(200, get(web, route + "/" + id + "/contract/edit").statusCode());
            var confirm = get(web, route + "/" + id + "/lifecycle");
            assertEquals(200, confirm.statusCode());
            assertFalse(confirm.body().contains("value=\"delete\""));
            if (!other) {
                var deactivated =
                        post(
                                web,
                                route + "/" + id + "/lifecycle",
                                Map.of(
                                        "version",
                                        "0",
                                        "action",
                                        "deactivate",
                                        "_csrf",
                                        csrf(confirm.body())));
                assertEquals(302, deactivated.statusCode());
                assertTrue(
                        get(web, deactivated.headers().firstValue("location").orElseThrow())
                                .body()
                                .contains("Client marked inactive."));
            }
            var sorted = get(web, route + "?financialYearId=2&sort=programmerCost&direction=desc");
            assertEquals(200, sorted.statusCode());
            assertTrue(sorted.body().contains("data-table-sort-key=\"programmerCost\""));
            assertTrue(sorted.body().contains(route + "/" + id + "/contract/edit"));
            assertTrue(list.body().contains("HTTP " + slug));
            var sw = get(web, route + "/new?lang=sw");
            assertEquals(200, sw.statusCode());
            assertTrue(sw.body().contains("Hali ya malipo"));
            assertEquals(200, get(web, route + "?lang=en").statusCode());
            assertEquals(200, get(web, route + "/print.pdf?financialYearId=2").statusCode());
            assertEquals(200, get(web, route + "/export.csv?financialYearId=2").statusCode());
            assertEquals(403, post(web, route, Map.of("clientName", "No CSRF")).statusCode());
        }
        assertEquals(403, get(session(STAFF), "/clients/amcos").statusCode());
    }
}
