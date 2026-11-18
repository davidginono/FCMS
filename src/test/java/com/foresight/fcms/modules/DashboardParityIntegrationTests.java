package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.dashboard.DashboardService;
import com.foresight.fcms.finance.expense.dto.ExpenseForm;
import com.foresight.fcms.finance.expense.service.ExpenseService;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.finance.report.service.FinancialReportService;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardParityIntegrationTests {
    static final String SCHEMA =
            "dashboard_parity_" + UUID.randomUUID().toString().replace("-", "");
    static final String PASSWORD = "DashboardVerification123!";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired DashboardService dashboard;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired LedgerService ledger;
    @Autowired ExpenseService expenses;
    @Autowired FinancialReportService finance;
    @Autowired PlatformTransactionManager transactions;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    long owner, other, previous;

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    long user(String name, int role, String... claims) {
        long id =
                db.queryForObject(
                        "insert into users(first_name,last_name,email,password,status_id,role_id)"
                                + " values ('Synthetic',?,?,?,1,?) returning id",
                        Long.class,
                        name,
                        name + "@test.invalid",
                        passwords.encode(PASSWORD),
                        role);
        for (String claim : claims)
            db.update("insert into user_claims(user_id,claim) values (?,?)", id, claim);
        return id;
    }

    long post(LedgerKind kind, int year, String direction, String amount, long units) {
        var f = new LedgerForm();
        f.setDate(LocalDate.of(year, 11, 1));
        f.setDescription("Synthetic dashboard " + kind);
        f.setCategory("Synthetic");
        f.setReference("Dashboard");
        f.setDirection(direction);
        f.setAmount(n(amount));
        f.setUnits(units);
        f.setUnitDirection(direction);
        if(kind==LedgerKind.BULK)f.setOperation(direction.equals("IN")?"STOCK_PURCHASE":"CLIENT_SALE");
        return ledger.post(kind, f);
    }

    void expense(String email, int year, String amount) {
        actor(email);
        var f = new ExpenseForm();
        f.setDate(LocalDate.of(year, 11, 1));
        f.setCategory("Synthetic Travel");
        f.setDescription("Synthetic dashboard");
        f.setPayee("Synthetic");
        f.setAmount(n(amount));
        expenses.save(null, f);
    }

    @BeforeAll
    void fixtures() throws Exception {
        OperationalTestClaims.enable(db);
        previous =
                db.queryForObject("select id from financial_year where fyear='2025'", Long.class);
        db.update("insert into roles(role_id,role_name) values (98,'Synthetic custom claims')");
        owner = user("dashboard-owner", 2);
        other = user("dashboard-other", 2);
        user("dashboard-reviewer", 1);
        user("dashboard-finance", 98, "workspace.admin", "dashboard.read", "finance.read");
        user("dashboard-report", 98, "workspace.admin", "dashboard.read", "report.read");
        user("dashboard-client", 98, "workspace.admin", "dashboard.read", "client.read");
        user("dashboard-denied", 98, "client.read");
        user("dashboard-task", 98, "dashboard.read", "task.read");
        for (String table :
                List.of("saccos", "amcos", "unions", "microfinance_clients", "other_clients")) {
            String name = table.equals("saccos") ? "saccos_name" : "client_name";
            for (int state = 1; state <= 3; state++) {
                String annual = state == 1 ? "200.25" : state == 2 ? "100.00" : "0.00";
                String paid = state == 1 ? "100.11" : state == 2 ? "0.00" : "50.02";
                db.update(
                        "insert into "
                                + table
                                + " ("
                                + name
                                + ",region,financial_year_id,client_status_id,annual_fee,amount,training_fee,system_install_fee,android_fee,system_reinstall_fee)"
                                + " values (?,'Synthetic',1,?,?,?,25.01,3.03,4.04,5.05)",
                        "Synthetic Dashboard " + state,
                        state,
                        n(annual),
                        n(paid));
            }
            db.update(
                    "insert into "
                            + table
                            + " ("
                            + name
                            + ",region,financial_year_id,client_status_id,annual_fee,amount)"
                            + " values ('Synthetic Previous','Synthetic',?,1,10.00,20.00)",
                    previous);
            db.update(
                    "insert into "
                            + table
                            + " ("
                            + name
                            + ",region,financial_year_id,client_status_id,annual_fee,amount,visible)"
                            + " values ('Synthetic Archived','Synthetic',1,1,99999,99999,false)");
        }
        for (String table : List.of("saccos", "amcos", "unions"))
            db.update(
                    "update "
                            + table
                            + " set withholding_tax=case when financial_year_id=? then 1.00 when"
                            + " client_status_id=1 then 10.01 else 0 end",
                    previous);
        db.update(
                "update saccos set cloud_backup_fee=12.34,cloud_backup_cost=1.01 where"
                        + " saccos_name='Synthetic Dashboard 1'");
        for (String kind : List.of("TASK", "LEAVE", "REPORT"))
            for (long id : List.of(owner, other))
                db.update(
                        "insert into"
                            + " work_items(kind,owner_id,subject,description,start_at,end_at,status,created_by,request_key)"
                            + " values (?,?,'Synthetic Dashboard','Synthetic Dashboard','2026-11-01"
                            + " 10:00','2026-11-02 10:00','DRAFT',?,?)",
                        kind,
                        id,
                        id,
                        UUID.randomUUID());
        actor("admin@fcms.local");
        post(LedgerKind.INCOME, 2025, "IN", "999.99", 0);
        long reversed = post(LedgerKind.INCOME, 2026, "IN", "100.11", 0);
        post(LedgerKind.INCOME, 2026, "IN", "70.07", 0);
        actor("dashboard-reviewer@test.invalid");
        ledger.reverse(LedgerKind.INCOME, reversed, UUID.randomUUID(), "Synthetic correction");
        actor("admin@fcms.local");
        post(LedgerKind.BULK, 2026, "IN", "200.25", 100);
        post(LedgerKind.BULK, 2026, "OUT", "50.02", 10);
        post(LedgerKind.PETTY, 2025, "IN", "1000.00", 0);
        post(LedgerKind.PETTY, 2026, "OUT", "300.01", 0);
        expense("dashboard-owner@test.invalid", 2026, "40.04");
        expense("dashboard-other@test.invalid", 2026, "60.06");
        expense("dashboard-owner@test.invalid", 2025, "900.09");
        Files.createDirectories(Path.of(".verification"));
        Files.writeString(Path.of(".verification/dashboard-parity-schema.txt"), SCHEMA);
    }

    DashboardService.Money money(DashboardService.Overview overview, String key) {
        return overview.money().stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void latestYearCountsStatusesAndMoneyExcludePriorAndArchivedClients() {
        actor("admin@fcms.local");
        var view = dashboard.overview(null);
        assertEquals(1, view.selected().id());
        assertEquals("2026", view.selected().label());
        assertEquals(1, view.portfolios().size());
        for (var category : List.of("saccos","amcos","unions","microfinance","others")) {
            var p=dashboard.overview(null,category).portfolios().getFirst();
            boolean allDates=category.equals("others");
            assertEquals(allDates?4:3, p.clients());
            assertEquals(allDates?2:1, p.paid());
            assertEquals(1, p.unpaid());
            assertEquals(1, p.pending());
            assertEquals(0, n(allDates?"310.25":"300.25").compareTo(p.annualFee()));
            assertEquals(0, n(allDates?"170.13":"150.13").compareTo(p.received()));
            assertEquals(
                    0,
                    n(p.category().equals("saccos") ? "140.11" : allDates?"140.12":"150.12")
                            .compareTo(p.annualDifference()));
            assertEquals(0, n("75.03").compareTo(p.fees().getFirst().amount()));
            assertEquals(
                    Set.of("saccos", "amcos", "unions").contains(p.category()) ? 6 : 4,
                    p.fees().size());
        }
        var s = view.portfolios().getFirst();
        assertEquals(1, s.cloudPaid());
        assertEquals(2, s.cloudUnpaid());
        assertEquals(n("12.34"), s.cloudFee());
        assertFalse(view.cloudBilling());
    }

    @Test
    void selectedYearRetainsOverpaymentsAndSignedReversalsAndCanonicalFinancialSummary() {
        actor("admin@fcms.local");
        var latest = dashboard.overview(1L);
        assertNull(money(latest, "income").value(),"Legacy income is excluded until accounting books are ready");
        assertEquals(n("-150.23"), money(latest, "bulk").value());
        assertEquals(n("699.99"), money(latest, "petty").value());
        assertNull(money(latest, "expenses").value(),"Legacy expense requests are excluded from posted accounting");
        assertTrue(latest.money().stream().noneMatch(m->m.key().equals("net")));
        assertEquals(
                "/accounting/statements/income-statement?from=2026-01-01&to=2026-12-31", money(latest, "income").href());
        assertEquals("/finance/petty-cash", money(latest, "petty").href());
        var old = dashboard.overview(previous);
        assertEquals("2025", old.selected().label());
        assertEquals(1, old.portfolios().getFirst().clients());
        assertEquals(n("-11.00"), old.portfolios().getFirst().annualDifference());
        assertEquals(n("-10.00"), dashboard.overview(previous,"amcos").portfolios().getFirst().annualDifference());
        assertNull(money(old, "income").value());
        assertEquals(n("699.99"), money(old, "petty").value());
        assertNull(money(old, "expenses").value());
    }

    @Test
    void staffScopesExpensesAndWorkWithoutFinanceWidgetsOrBrokenCloudLinks() {
        actor("dashboard-owner@test.invalid");
        var view = dashboard.overview(null);
        assertTrue(view.money().isEmpty());
        assertTrue(view.portfolios().isEmpty());
        assertTrue(view.years().isEmpty());
        assertEquals(2, view.work().size());
        assertTrue(view.work().stream().noneMatch(m->m.label().equals("workspace.my.expense")));
        assertTrue(view.work().stream().filter(m -> !m.label().equals("workspace.my.expense")).allMatch(m -> m.value() == 1));
        assertFalse(view.cloudBilling());
    }

    @Test
    void customClaimsEnableOnlyTheirOwnWidgetsAndDashboardClaimIsRequired() {
        actor("dashboard-finance@test.invalid");
        var financeOnly = dashboard.overview(null);
        assertTrue(financeOnly.portfolios().isEmpty());
        assertTrue(financeOnly.work().isEmpty());
        assertFalse(financeOnly.cloudBilling());
        assertEquals(2, financeOnly.money().size());
        actor("dashboard-report@test.invalid");
        var report = dashboard.overview(null);
        assertTrue(report.portfolios().isEmpty());
        assertEquals(
                List.of(), report.money().stream().map(DashboardService.Money::key).toList());
        actor("dashboard-client@test.invalid");
        assertEquals(1, dashboard.overview(null).portfolios().size());
        assertTrue(dashboard.overview(null).money().isEmpty());
        actor("dashboard-task@test.invalid");
        var task = dashboard.overview(null);
        assertTrue(task.work().isEmpty());
        assertTrue(task.portfolios().isEmpty());
        assertTrue(task.money().isEmpty());
        actor("dashboard-denied@test.invalid");
        assertThrows(AccessDeniedException.class, () -> dashboard.overview(null));
    }

    @Test
    void unknownYearFailsAndNonCalendarLabelNeverFabricatesDateBoundFinancialFigures() {
        actor("admin@fcms.local");
        assertThrows(IllegalArgumentException.class, () -> dashboard.overview(Long.MAX_VALUE));
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            long id =
                    db.queryForObject(
                            "insert into financial_year(fyear) values ('FY-TEST') returning id",
                            Long.class);
            var view = dashboard.overview(id);
            assertNull(view.selected().calendarYear());
            assertFalse(view.cloudBilling());
            assertTrue(view.portfolios().stream().allMatch(p -> p.clients() == 0));
            for (String key : List.of("income", "bulk", "expenses")) {
                assertNull(money(view, key).value());
                assertNull(money(view, key).href());
            }
            assertEquals(n("699.99"), money(view, "petty").value());
            status.setRollbackOnly();
        });
    }

    String value(String html, String name) {
        var m = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find());
        return m.group(1);
    }

    HttpClient browser(String email) throws Exception {
        var web =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        var page = get(web, "/login");
        String body =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(
                                email.equals("admin@fcms.local") ? "ChangeMe123!" : PASSWORD,
                                StandardCharsets.UTF_8)
                        + "&_csrf="
                        + URLEncoder.encode(value(page.body(), "_csrf"), StandardCharsets.UTF_8);
        var response =
                web.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        return web;
    }

    HttpResponse<String> get(HttpClient web, String path) throws Exception {
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(45))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void nativeLocalizedPagesCarryTheYearToEveryClientFeeCloudAndFinanceDrill() throws Exception {
        var web = browser("admin@fcms.local");
        for (String lang : List.of("en", "sw")) {
            var response = get(web, "/accounting/dashboard?financialYearId=" + previous + "&lang=" + lang);
            assertEquals(200, response.statusCode());
            String html = response.body();
            assertTrue(html.contains("dashboard-year-form"));
            assertTrue(html.contains("financialYearId=" + previous));
            assertTrue(html.contains("data-dashboard-fee=\"training_fee\""));
            assertFalse(html.contains("/finance/cloud-backup/billing"));
            assertTrue(html.contains("from=2025-01-01"));
            assertFalse(html.contains("/finance/expenses?year=2025"));
            assertTrue(html.contains(lang.equals("sw") ? "Tofauti" : "Annual difference"));
        }
        var staff = browser("dashboard-owner@test.invalid");
        String html = get(staff, "/staff/dashboard").body();
        assertTrue(html.contains("workspace.my.leave"));
        assertFalse(html.contains("dashboard-year-form"));
        assertFalse(html.contains("data-dashboard-cloud"));
        assertFalse(html.contains("data-dashboard-money=\"petty\""));
        assertEquals(400, get(web, "/accounting/dashboard?financialYearId=" + Long.MAX_VALUE).statusCode());
        assertEquals(403, get(browser("dashboard-denied@test.invalid"), "/accounting/dashboard").statusCode());
    }
}
