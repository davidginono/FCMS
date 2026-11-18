package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.UserAccountDetailsService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.dashboard.DashboardService;
import com.foresight.fcms.finance.expense.dto.*;
import com.foresight.fcms.finance.expense.service.*;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.foresight.fcms.workforce.dto.WorkForm;
import com.foresight.fcms.workforce.model.WorkKind;
import com.foresight.fcms.workforce.service.WorkService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"fcms.mail.initial-delay=3600000", "fcms.mail.interval=3600000",
                "fcms.backup.interval=3600000"})
@EnabledIfEnvironmentVariable(named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffWorkspaceIntegrationTests {
    static final String SCHEMA = "staff_workspace_" + UUID.randomUUID().toString().replace("-", "");
    static final String PASSWORD = "WorkspaceVerification123!";
    static final String STAFF = "workspace-staff@test.invalid";
    static final String OTHER = "workspace-other@test.invalid";
    static final String ADMIN = "workspace-admin@test.invalid";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired AccessService access;
    @Autowired UserAccountDetailsService logins;
    @Autowired com.foresight.fcms.auth.service.UserAdministrationService users;
    @Autowired WorkService work;
    @Autowired ExpenseService expenses;
    @Autowired ExpenseCategoryService categories;
    @Autowired DashboardService dashboard;
    @Autowired PasswordEncoder passwords;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;

    long staff, other, client, ownExpense, otherExpense, group;
    Map<WorkKind, Long> own = new EnumMap<>(WorkKind.class);
    Map<WorkKind, Long> foreign = new EnumMap<>(WorkKind.class);

    void actor(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    long user(String email, int role) {
        return db.queryForObject("insert into users(first_name,last_name,email,password,status_id,role_id)"
                        + " values ('Workspace','Synthetic',?,?,1,?) returning id", Long.class,
                email, passwords.encode(PASSWORD), role);
    }

    WorkForm form(String subject) {
        var f = new WorkForm();
        f.setSubject(subject);
        f.setDescription(subject);
        f.setClientId(client);
        return f;
    }

    ExpenseForm expense(String description, String amount) {
        var f = new ExpenseForm();
        f.setDate(LocalDate.of(2026, 11, 1));
        f.setCategory("Workspace travel");
        f.setPayee("Synthetic");
        f.setDescription(description);
        f.setAmount(new BigDecimal(amount));
        return f;
    }

    @BeforeAll
    void fixtures() {
        OperationalTestClaims.enable(db);
        staff = user(STAFF, 2);
        other = user(OTHER, 2);
        user(ADMIN, 1);
        client = db.queryForObject("insert into saccos(saccos_name,region,financial_year_id,client_status_id)"
                + " values ('Workspace client','Synthetic',1,2) returning id", Long.class);
        // Even accidental legacy/custom capability grants cannot escape the staff workspace.
        db.update("insert into user_claims(user_id,claim) select ?,name from claims where name not in ('workspace.admin','workspace.accounting','workspace.director')", staff);
        actor(STAFF);
        for (var kind : WorkKind.values()) own.put(kind, work.save(kind, null, form("OWN-" + kind)));
        ownExpense = expenses.save(null, expense("OWN-EXPENSE", "50.25"));
        actor(OTHER);
        for (var kind : WorkKind.values()) foreign.put(kind, work.save(kind, null, form("PRIVATE-" + kind)));
        otherExpense = expenses.save(null, expense("PRIVATE-EXPENSE", "90.75"));
        var f = form("PRIVATE-GROUP");
        f.setWorkType("GROUP");
        f.setParticipants(List.of(staff));
        group = work.save(WorkKind.REPORT, null, f);
        clear();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void databaseAndLoginClaimsShareTheSamePersonalCeiling() {
        actor(STAFF);
        var a = access.current();
        assertTrue(a.has("workspace.staff"));
        for (String claim : List.of("client.read", "task.read.all", "task.manage", "leave.review",
                "staff-report.assign", "staff-report.review", "expense.assign", "expense.approve",
                "iam.user.manage", "accounting.read", "report.read", "backup.manage"))
            assertFalse(a.has(claim), claim);
        assertEquals(a.claims(), logins.loadUserByUsername(STAFF).getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet()));
        actor(ADMIN);
        assertTrue(access.current().has("workspace.admin"));
        assertFalse(access.current().has("workspace.staff"));
    }

    @Test
    void listsDetailsCountsAndExportsAreStrictlyOwnerScoped() {
        actor(STAFF);
        for (var kind : WorkKind.values()) {
            var q = new RegisterQuery();
            assertEquals(List.of(own.get(kind)), work.list(kind, q).rows().stream().map(r -> r.id()).toList());
            assertEquals(1, work.count(kind));
            assertEquals(own.get(kind).longValue(), work.find(kind, own.get(kind)).id());
            assertThrows(IllegalArgumentException.class, () -> work.find(kind, foreign.get(kind)));
            assertTrue(work.report(kind, q).stream().allMatch(r -> r.ownerId() == staff));
            q.setOwnerId(other);
            assertTrue(work.list(kind, q).rows().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> work.find(WorkKind.REPORT, group));
        assertEquals(1, categories.ownCount());
        assertEquals(ownExpense, expenses.find(ownExpense).id());
        assertThrows(IllegalArgumentException.class, () -> expenses.find(otherExpense));
        var q = new ExpenseQuery();
        q.setYear("2026");
        assertEquals(new BigDecimal("50.25"), categories.total(q));
        assertEquals(List.of(ownExpense), expenses.report(q).stream().map(r -> r.id()).toList());
        q.setOwnerId(other);
        assertTrue(expenses.list(q).rows().isEmpty());
        assertEquals(new BigDecimal("0"), categories.total(q));
        var view = dashboard.overview(Long.MAX_VALUE);
        assertEquals(2, view.work().size());
        assertTrue(view.work().stream().allMatch(m -> m.value() == 1));
        assertTrue(view.years().isEmpty());
        assertTrue(view.money().isEmpty());
        assertTrue(view.portfolios().isEmpty());
        assertThrows(AccessDeniedException.class, () -> dashboard.adminOverview(null));
        actor(ADMIN);
        for (var kind : WorkKind.values()) assertEquals(foreign.get(kind).longValue(), work.find(kind, foreign.get(kind)).id());
        assertEquals(otherExpense, expenses.find(otherExpense).id());
        assertThrows(AccessDeniedException.class, () -> dashboard.staffOverview());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void personalMutationsRemainUsableAndCannotApproveOrModifyForeignRecords() {
        actor(STAFF);
        for (var kind : WorkKind.values()) {
            assertThrows(IllegalArgumentException.class, () -> work.form(kind, foreign.get(kind)));
            assertThrows(IllegalArgumentException.class,
                    () -> work.transition(kind, foreign.get(kind), 0, "submit", ""));
        }
        assertThrows(IllegalArgumentException.class, () -> expenses.form(otherExpense));
        assertThrows(IllegalArgumentException.class,
                () -> expenses.transition(otherExpense, 0, "submit", "", "", null));
        var forged = expense("Forged owner", "10.00");
        forged.setOwnerId(other);
        assertThrows(AccessDeniedException.class, () -> expenses.save(null, forged));
        long leave = work.save(WorkKind.LEAVE, null, form("Own approval check"));
        work.transition(WorkKind.LEAVE, leave, 0, "submit", "");
        assertThrows(AccessDeniedException.class,
                () -> work.transition(WorkKind.LEAVE, leave, 1, "approve", "Self approval"));
        actor(ADMIN);
        work.transition(WorkKind.LEAVE, leave, 1, "approve", "Independent approval");
        assertEquals("APPROVED", work.find(WorkKind.LEAVE, leave).status());
        actor(STAFF);
        assertEquals("APPROVED", work.find(WorkKind.LEAVE, leave).status());
    }

    @Test
    void nativeLoginNavigationAndAdministrativeUrlsRespectWorkspaceAndLocalization() throws Exception {
        var staffWeb = login(STAFF, "/staff/dashboard");
        var adminWeb = login(ADMIN, "/dashboard");
        for (String lang : List.of("en", "sw")) {
            var page = get(staffWeb, "/staff/dashboard?lang=" + lang);
            assertEquals(200, page.statusCode());
            String html = page.body();
            String nav = html.substring(html.indexOf("<aside id=\"fcms-sidebar\""), html.indexOf("</aside>"));
            assertTrue(nav.contains("data-staff-workspace"));
            assertEquals(3, Pattern.compile("class=\"app-sidebar-link\"").matcher(nav).results().count());
            assertFalse(nav.contains("/clients/"));
            assertFalse(nav.contains("/accounting/"));
            assertFalse(nav.contains("/admin/users"));
            assertFalse(html.contains("dashboard-year-form"));
            assertFalse(html.contains("data-dashboard-client="));
            assertTrue(html.contains(lang.equals("en") ? "My dashboard" : "Dashibodi yangu"));
        }
        for (String path : List.of("/admin/dashboard", "/admin/users", "/accounting/accounts",
                "/accounting/statements?type=income", "/clients/saccos", "/sales/invoices",
                "/finance/petty-cash", "/backups",
                "/union.php", "/user_microfinance.php"))
            assertEquals(403, get(staffWeb, path).statusCode(), path);
        assertEquals(403, get(adminWeb, "/staff/dashboard").statusCode());
        assertEquals(302, get(adminWeb, "/admin/dashboard").statusCode());
        assertEquals(200, get(staffWeb, "/account").statusCode());
        for (var kind : WorkKind.values()) {
            var register = get(staffWeb, kind.getRoute());
            assertEquals(200, register.statusCode());
            assertTrue(register.body().contains("OWN-" + kind));
            assertFalse(register.body().contains("PRIVATE-"));
            assertEquals(200, get(staffWeb, kind.getRoute() + "/" + own.get(kind)).statusCode());
            assertEquals(400, get(staffWeb, kind.getRoute() + "/" + foreign.get(kind)).statusCode());
            assertEquals(200, get(staffWeb, kind.getRoute() + "/new").statusCode());
        }
        assertTrue(get(staffWeb, "/tasks/new").body().contains("data-choice-url=\"/lookups/clients\""));
        for (String path : List.of("/finance/expenses/new", "/finance/expenses/" + otherExpense,
                "/finance/income", "/reports"))
            assertEquals(410, get(staffWeb, path).statusCode(), path);
        var noCsrf = staffWeb.send(HttpRequest.newBuilder(uri("/leaves/" + own.get(WorkKind.LEAVE)
                        + "/actions/submit")).POST(HttpRequest.BodyPublishers.ofString("version=0")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrf.statusCode());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void staffWithLegacyManagementClaimsCannotReplaceTheLastAdministrator() {
        actor(ADMIN);
        long admin = access.current().id();
        db.update("update users set status_id=2 where id<>? and id<>?", admin, staff);
        var edit = users.form(admin);
        edit.setRoleId(2L);
        // The editable form submits the newly selected role's actual checked permissions.
        edit.setClaims(db.queryForList("select claim from role_claims where role_id=2", String.class));
        assertThrows(IllegalArgumentException.class, () -> users.save(admin, edit));
    }

    @Test
    void explicitWorkspaceGrantStillRequiresCapabilityAndRevocationIsImmediate() throws Exception {
        actor(STAFF);
        HttpClient web;
        db.update("insert into user_claims(user_id,claim) values (?,'workspace.admin')", other);
        try {
            actor(OTHER);
            web = login(OTHER, "/dashboard");
            assertTrue(access.current().has("workspace.admin"));
            assertFalse(access.current().has("iam.user.manage"));
            assertThrows(AccessDeniedException.class, () -> access.require("accounting.read"));
        } finally {
            db.update("delete from user_claims where user_id=? and claim='workspace.admin'", other);
        }
        assertTrue(access.current().has("workspace.staff"));
        assertFalse(access.current().has("workspace.admin"));
        assertEquals(403, get(web, "/admin/dashboard").statusCode());
        assertEquals(302,get(web, "/dashboard").statusCode());
        assertEquals(403,get(web,"/staff/dashboard").statusCode(),"Workspace changes require refreshed login authorities");
        assertTrue(get(login(OTHER,"/staff/dashboard"), "/staff/dashboard").body().contains("data-staff-workspace"));
        db.update("update users set status_id=2 where id=?", other);
        try { assertThrows(AccessDeniedException.class, access::current); }
        finally { db.update("update users set status_id=1 where id=?", other); }
    }

    @Test
    void downloadedPdfsExcludeOtherOwnersAndGroupParticipants() throws Exception {
        var web = login(STAFF, "/staff/dashboard");
        for (String route : List.of("/tasks", "/leaves", "/staff-reports")) {
            var response = web.send(HttpRequest.newBuilder(uri(route + "/print.pdf")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode(), route);
            assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElseThrow());
            var text = new StringBuilder();
            try (var reader = new com.lowagie.text.pdf.PdfReader(response.body())) {
                for (int p = 1; p <= reader.getNumberOfPages(); p++)
                    text.append(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(p));
            }
            assertTrue(text.toString().contains("OWN-"), route);
            assertFalse(text.toString().contains("PRIVATE-"), route);
        }
    }

    @Test
    void nativeStaffFormsCreateAndSubmitLeavesAndReportsWithCsrfAndIdempotency() throws Exception {
        String email = "workspace-native@test.invalid";
        long id = user(email, 2);
        var web = login(email, "/staff/dashboard");
        var otherWeb = login(OTHER, "/staff/dashboard");
        for (String route : List.of("/leaves", "/staff-reports")) {
            String html = get(web, route + "/new").body();
            var fields = new LinkedHashMap<String, String>();
            fields.put("_csrf", field(html, "_csrf"));
            fields.put("requestKey", field(html, "requestKey"));
            fields.put("description", "Native own workflow");
            if (route.equals("/finance/expenses")) {
                fields.put("date", "2026-11-01");
                fields.put("category", "Native travel");
                fields.put("payee", "Synthetic");
                fields.put("amount", "12.34");
                fields.put("financialYearId", "1");
            } else {
                fields.put("subject", "Native own workflow");
                fields.put("startAt", "2026-11-01T08:00");
                fields.put("endAt", "2026-11-01T09:00");
                if (!route.equals("/leaves")) fields.put("clientId", Long.toString(client));
            }
            var created = post(web, route, fields);
            assertEquals(302, created.statusCode(), route);
            String location = created.headers().firstValue("Location").orElseThrow();
            String path = URI.create(location).getPath();
            assertEquals(location, post(web, route, fields).headers().firstValue("Location").orElseThrow());
            long record = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
            assertEquals(id, db.queryForObject(route.equals("/finance/expenses")
                    ? "select user_id from expenses where id=?" : "select owner_id from work_items where id=?",
                    Long.class, record));
            assertEquals(400, get(otherWeb, path).statusCode());
            String detail = get(web, path).body();
            var command = Map.of("_csrf", field(detail, "_csrf"), "version", "0",
                    "requestKey", UUID.randomUUID().toString());
            assertEquals(302, post(web, path + "/actions/" + (route.equals("/tasks") ? "start" : "submit"), command).statusCode());
        }
        var page = get(web, "/account");
        assertEquals(302, post(web, "/logout", Map.of("_csrf", field(page.body(), "_csrf"))).statusCode());
        assertEquals(302, get(web, "/staff/dashboard").statusCode());
    }

    String field(String html, String name) {
        var match = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(match.find(), name);
        return match.group(1);
    }

    HttpResponse<String> post(HttpClient client, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(45)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpClient login(String email, String destination) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var page = get(client, "/login");
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]*)\"").matcher(page.body());
        assertTrue(csrf.find());
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
        var result = client.send(HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, result.statusCode());
        assertEquals(destination, URI.create(result.headers().firstValue("Location").orElseThrow()).getPath());
        return client;
    }
}
