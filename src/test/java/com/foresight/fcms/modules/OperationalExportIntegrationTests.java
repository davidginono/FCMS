package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.service.CloudBillingService;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationalExportIntegrationTests {
    static final String SCHEMA =
            "operational_export_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired LedgerService ledger;
    @Autowired CloudBillingService billing;
    @Autowired com.foresight.fcms.finance.expense.service.ExpenseCategoryService expenseCategories;
    @Autowired com.foresight.fcms.finance.expense.service.ExpenseService expenses;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    long staff, other, client;
    final Map<LedgerKind, Long> entries = new EnumMap<>(LedgerKind.class);

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @BeforeAll
    void fixtures() throws Exception {
        OperationalTestClaims.enable(db);
        for (String email : List.of("export-staff@test.invalid", "export-other@test.invalid"))
            db.update(
                    "insert into users(first_name,last_name,email,password,status_id,role_id)"
                            + " values ('Synthetic','Staff',?,?,1,2)",
                    email,
                    passwords.encode("Verification123!"));
        staff =
                db.queryForObject(
                        "select id from users where email='export-staff@test.invalid'", Long.class);
        other =
                db.queryForObject(
                        "select id from users where email='export-other@test.invalid'", Long.class);
        client =
                db.queryForObject(
                        "insert into"
                            + " saccos(saccos_name,region,annual_fee,amount,cloud_backup_fee,cloud_backup_cost,financial_year_id,client_status_id)"
                            + " values ('Synthetic Export"
                            + " Client','Test',100.11,90.09,12.34,1.01,1,1) returning id",
                        Long.class);
        for (String table : List.of("amcos", "unions", "microfinance_clients", "other_clients"))
            db.update(
                    "insert into "
                            + table
                            + "(client_name,region,annual_fee,amount,financial_year_id,client_status_id)"
                            + " values ('Synthetic Export Client','Test',100.11,90.09,1,1)");
        for (String kind : List.of("TASK", "LEAVE", "REPORT"))
            for (long owner : List.of(staff, other))
                db.update(
                        "insert into"
                            + " work_items(kind,owner_id,client_id,subject,description,start_at,end_at,work_type,allowance,amount,status,created_by,request_key)"
                            + " values (?,?,?, ?,?,'2026-11-01 10:30','2026-11-02"
                            + " 17:15','Support',33.37,100.11,'DRAFT',?,?)",
                        kind,
                        owner,
                        client,
                        owner == staff ? "OWN-SUBJECT-731" : "OTHER-SUBJECT-839",
                        owner == staff ? "OWN-ACTIVITY-731" : "OTHER-ACTIVITY-839",
                        owner,
                        UUID.randomUUID());
        actor("admin@fcms.local");
        for (var kind : LedgerKind.values()) {
            var form = new LedgerForm();
            form.setAmount(new BigDecimal("100.11"));
            form.setDescription("Synthetic export movement");
            form.setCategory("Office");
            form.setReference("SYNTHETIC-REF-" + kind);
            if (kind == LedgerKind.BULK) {form.setUnits(13);form.setOperation("STOCK_PURCHASE");}
            entries.put(kind, ledger.post(kind, form));
        }
        SecurityContextHolder.clearContext();
        for (int n = 0; n < 11; n++)
            expense(staff, "Category-" + String.format("%02d", n), "2026", "1.11");
        expense(staff, "Travel", "2026", "12.34");
        expense(staff, "Literal%_category", "2026", "2.22");
        expense(other, "Travel", "2026", "999.99");
        expense(staff, "Travel", "2025", "21.01");
        Files.writeString(Path.of(".verification/operational-export-schema.txt"), SCHEMA);
    }

    void expense(long owner, String category, String year, String amount) {
        db.update(
                "insert into"
                    + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id,request_key)"
                    + " values (?,?,?,?,?,?::numeric,?,1,?)",
                java.sql.Date.valueOf(year + "-11-01"),
                owner,
                category,
                owner == staff ? "OWN-EXPENSE-731" : "OTHER-EXPENSE-839",
                "Synthetic Payee",
                amount,
                year,
                UUID.randomUUID());
    }

    @Test
    void expenseCategoryTotalsAndDrillsHonorYearScopeFiltersAndWholeResultSorting() {
        actor("export-staff@test.invalid");
        var q = new com.foresight.fcms.finance.expense.dto.ExpenseCategoryQuery();
        q.setYear("2026");
        q.setSize(10);
        var first = expenseCategories.summary(q);
        assertEquals(new BigDecimal("26.77"), first.total());
        assertEquals(10, first.records().rows().size());
        assertTrue(first.records().hasNext());
        assertEquals("Category-00", first.records().rows().getFirst().category());
        q.setPage(1);
        var next = expenseCategories.summary(q);
        assertEquals(new BigDecimal("26.77"), next.total());
        assertEquals(3, next.records().rows().size());
        assertFalse(next.records().hasNext());
        assertEquals(13, expenseCategories.export(q).rows().size());
        assertEquals(1, q.getPage());
        q.setPage(0);
        q.setSort("amount");
        q.setDirection("desc");
        assertEquals("Travel", expenseCategories.summary(q).records().rows().getFirst().category());
        q.setSearch("%_");
        assertEquals(new BigDecimal("2.22"), expenseCategories.summary(q).total());
        q.setSearch("");
        q.setCategory("Travel");
        assertEquals(new BigDecimal("12.34"), expenseCategories.summary(q).total());
        assertEquals(1, expenses.report(q).size());
        q.setOwnerId(other);
        assertEquals(0, expenseCategories.summary(q).total().signum());
        assertTrue(expenses.report(q).isEmpty());
        actor("admin@fcms.local");
        q.setOwnerId(null);
        assertEquals(new BigDecimal("1012.33"), expenseCategories.summary(q).total());
        q.setOwnerId(staff);
        assertEquals(new BigDecimal("12.34"), expenseCategories.summary(q).total());
        q.setYear("2025");
        assertEquals(new BigDecimal("21.01"), expenseCategories.summary(q).total());
        q.setYear("2026' OR '1'='1");
        assertEquals(0, expenseCategories.summary(q).total().signum());
    }

    @Test
    void retiredExpenseSummaryDrillAndPdfAreGoneInBothLanguages() throws Exception {
        var staffWeb = browser("export-staff@test.invalid", "Verification123!");
        for (String lang : List.of("en", "sw"))
            for (String path : List.of("/finance/expenses/categories", "/finance/expenses/categories/print.pdf",
                    "/finance/expenses", "/finance/expenses/print.pdf")) {
                var result = staffWeb.send(request(path + "?year=2026&lang=" + lang).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(410, result.statusCode(), path);
                assertTrue(result.body().isEmpty());
            }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void categoryPdfRejectsUnboundedExportsWithoutLosingRegisterPagination() {
        actor("export-staff@test.invalid");
        db.update(
                "insert into"
                    + " expenses(date_created,user_id,category,description,payee,amount,financial_year,status_id)"
                    + " select"
                    + " '2026-11-01'::date,?,'Large-'||n,'Synthetic','Synthetic',0.01,'2026',1 from"
                    + " generate_series(1,1001) n",
                staff);
        var q = new com.foresight.fcms.finance.expense.dto.ExpenseCategoryQuery();
        q.setYear("2026");
        q.setSearch("Large-");
        q.setSize(10);
        assertEquals(10, expenseCategories.summary(q).records().rows().size());
        assertEquals(new BigDecimal("10.01"), expenseCategories.summary(q).total());
        assertThrows(IllegalArgumentException.class, () -> expenseCategories.export(q));
    }

    HttpClient browser(String email, String password) throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        String html =
                web.send(request("/login").GET().build(), HttpResponse.BodyHandlers.ofString())
                        .body();
        var csrf =
                java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(csrf.find());
        String body =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(password, StandardCharsets.UTF_8)
                        + "&_csrf="
                        + csrf.group(1);
        var login =
                web.send(
                        request("/login")
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(302, login.statusCode());
        assertFalse(login.headers().firstValue("location").orElse("").contains("error"));
        return web;
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60));
    }

    String pdf(HttpClient web, String path) throws Exception {
        var response =
                web.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(
                200,
                response.statusCode(),
                path
                        + " "
                        + new String(response.body(), StandardCharsets.UTF_8)
                                .substring(0, Math.min(160, response.body().length)));
        assertTrue(
                response.headers()
                        .firstValue("content-type")
                        .orElse("")
                        .contains("application/pdf"));
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertTrue(
                response.headers().firstValue("content-disposition").orElse("").contains(".pdf"));
        StringBuilder text = new StringBuilder();
        try (var reader = new PdfReader(response.body())) {
            for (int p = 1; p <= reader.getNumberOfPages(); p++)
                text.append(new PdfTextExtractor(reader).getTextFromPage(p));
        }
        return text.toString().replaceAll("\\s+", "");
    }

    @Test
    void workforceExportsEnforceOwnerScopeEvenWithForgedFilters() throws Exception {
        var staffWeb = browser("export-staff@test.invalid", "Verification123!");
        for (String workspace : List.of("tasks", "leaves", "staff-reports"))
            for (String lang : List.of("en", "sw")) {
                String text = pdf(staffWeb, "/" + workspace + "/print.pdf?lang=" + lang);
                assertTrue(text.contains("OWN-ACTIVITY-731"));
                assertFalse(text.contains("OTHER-ACTIVITY-839"));
                String forged =
                        pdf(
                                staffWeb,
                                "/" + workspace + "/print.pdf?ownerId=" + other + "&lang=" + lang);
                assertFalse(forged.contains("OTHER-ACTIVITY-839"));
            }
    }

    @Test
    void allNativeOperationalAndClientExportsRenderInBothLanguages() throws Exception {
        var admin = browser("admin@fcms.local", "ChangeMe123!");
        var paths =
                new ArrayList<String>(
                        List.of(
                                "/tasks/print.pdf?ownerId=" + staff + "&clientId=" + client,
                                "/leaves/print.pdf?ownerId=" + staff,
                                "/staff-reports/print.pdf?ownerId=" + staff,
                                "/clients/saccos/paid/print.pdf?financialYearId=1",
                                "/clients/saccos/unpaid/print.pdf?financialYearId=1"));
        for (var entry : entries.entrySet())
            if (entry.getKey() != LedgerKind.INCOME) paths.add(
                    "/finance/"
                            + entry.getKey().route
                            + "/"
                            + entry.getValue()
                            + "/print.pdf?test=1");
        for (String category : List.of("saccos", "amcos", "unions", "microfinance", "others")) {
            paths.add("/clients/" + category + "/print.pdf?financialYearId=1");
        }
        for (String path : paths)
            for (String lang : List.of("en", "sw")) {
                String rendered = pdf(admin, path + "&lang=" + lang);
                assertFalse(rendered.isBlank(), path);
                if (path.matches(
                        "/clients/(saccos|amcos|unions|microfinance|others)/print\\.pdf.*"))
                    assertTrue(
                            rendered.contains(lang.equals("sw") ? "Imelipwa" : "Paid"),
                            path + " " + lang);
            }
        String scoped = pdf(admin, "/tasks/print.pdf?ownerId=" + staff + "&clientId=" + client);
        assertTrue(scoped.contains("OWN-ACTIVITY-731"));
        assertFalse(scoped.contains("OTHER-ACTIVITY-839"));
    }

    @Test
    void paidAndUnpaidSummaryExportsAreReachableFromTheFilteredRegister() throws Exception {
        var web = browser("admin@fcms.local", "ChangeMe123!");
        for (String status : List.of("1", "2")) {
            var response =
                    web.send(
                            request("/clients/saccos?financialYearId=1&paymentStatusId=" + status)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            var link =
                    java.util.regex.Pattern.compile("<a[^>]*data-table-export=\"payment\"[^>]*>")
                            .matcher(response.body());
            assertTrue(link.find());
            assertFalse(link.group().contains("hidden"));
            assertTrue(
                    link.group()
                            .contains(
                                    status.equals("1") ? "/paid/print.pdf" : "/unpaid/print.pdf"));
            assertTrue(link.group().contains("financialYearId=1"));
        }
        var all =
                web.send(
                        request("/clients/saccos?financialYearId=1").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        var link =
                java.util.regex.Pattern.compile("<a[^>]*data-table-export=\"payment\"[^>]*>")
                        .matcher(all.body());
        assertTrue(link.find());
        assertTrue(link.group().contains("hidden"));
    }

    @Test
    void clientPaymentChoicesAreLocalizedAcrossNativeRegistersReportsAndForms() throws Exception {
        var web = browser("admin@fcms.local", "ChangeMe123!");
        for (String category : List.of("saccos", "amcos", "unions", "microfinance", "others"))
            for (String suffix : List.of("?view=list&", "?view=revenue&", "/new?"))
                for (String lang : List.of("en", "sw")) {
                    String path = "/clients/" + category + suffix + "lang=" + lang;
                    var response =
                            web.send(
                                    request(path).GET().build(),
                                    HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), path);
                    var choices =
                            java.util.regex.Pattern.compile(
                                            "<select[^>]*name=\"paymentStatusId\"[^>]*>(.*?)</select>",
                                            java.util.regex.Pattern.DOTALL)
                                    .matcher(response.body());
                    assertTrue(choices.find(), path);
                    assertTrue(
                            choices.group(1).contains(lang.equals("sw") ? "Imelipwa" : "Paid"),
                            path);
                    assertTrue(
                            choices.group(1).contains(lang.equals("sw") ? "Haijalipwa" : "Unpaid"),
                            path);
                    assertFalse(choices.group(1).matches("(?s).*?>\\s*PAID\\s*</option>.*"), path);
                }
    }

    @Test
    void clientAndFinanceExportsDenyActorsWithoutRequiredClaims() throws Exception {
        var staffWeb = browser("export-staff@test.invalid", "Verification123!");
        for (String path :
                List.of(
                        "/clients/saccos/print.pdf",
                        "/clients/amcos/print.pdf"))
            assertEquals(
                    403,
                    staffWeb.send(
                                    request(path).GET().build(),
                                    HttpResponse.BodyHandlers.discarding())
                            .statusCode(),
                    path);
    }

    @Test
    void billingHonorsYearStatusLiteralSearchAndPreservesTheQuery() {
        actor("admin@fcms.local");
        var query = new RegisterQuery();
        query.setPage(3);
        query.setSize(10);
        query.setStatus("PAID");
        assertEquals(1, billing.report(2026, query).size());
        assertEquals(3, query.getPage());
        assertEquals(10, query.getSize());
        query.setStatus("UNPAID");
        assertTrue(billing.report(2026, query).isEmpty());
        query.setStatus("");
        query.setSearch("%_");
        assertTrue(billing.report(2026, query).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> billing.report(1999, query));
    }

    @Autowired com.foresight.fcms.client.saccos.service.SaccosService saccos;
    @Autowired com.foresight.fcms.client.category.service.ClientCategoryService clients;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void exportedClientRowsUseTheRegistersSafeServerOrder() {
        actor("admin@fcms.local");
        for (String name : List.of("LOW", "HIGH")) {
            String amount = name.equals("LOW") ? "1.01" : "9.09";
            db.update(
                    "insert into"
                        + " saccos(saccos_name,region,annual_fee,amount,financial_year_id,client_status_id)"
                        + " values (?, 'Test',100,?::numeric,1,1)",
                    "SORT-PROOF-" + name,
                    amount);
            db.update(
                    "insert into"
                        + " amcos(client_name,region,annual_fee,amount,financial_year_id,client_status_id)"
                        + " values (?, 'Test',100,?::numeric,1,1)",
                    "SORT-PROOF-" + name,
                    amount);
        }
        var q = new com.foresight.fcms.client.saccos.dto.SaccosFilter();
        q.setFinancialYearId(1L);
        q.setSearch("SORT-PROOF");
        q.setSort("amount");
        q.setDirection("desc");
        assertEquals(
                List.of("SORT-PROOF-HIGH", "SORT-PROOF-LOW"),
                saccos.exportSaccosWithTotals(q).rows().stream().map(r -> r.saccosName()).toList());
        var filter = new com.foresight.fcms.client.category.dto.ClientCategoryFilter();
        filter.setFinancialYearId(1L);
        filter.setSearch("SORT-PROOF");
        filter.setSort("amount");
        filter.setDirection("asc");
        assertEquals(
                List.of("SORT-PROOF-LOW", "SORT-PROOF-HIGH"),
                clients
                        .exportClientsWithTotals(
                                com.foresight.fcms.client.category.model.ClientCategory.AMCOS,
                                filter)
                        .rows()
                        .stream()
                        .map(r -> r.clientName())
                        .toList());
    }
}
