package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;
import com.foresight.fcms.dashboard.DashboardService;
import com.foresight.fcms.finance.ledger.dto.*;
import com.foresight.fcms.finance.ledger.dto.LedgerQuery;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fcms.mail.initial-delay=3600000",
            "fcms.mail.interval=3600000",
            "fcms.backup.interval=3600000",
            "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
        })
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BusinessWorkspacePostgresTests {
    static final String SCHEMA = "business_" + UUID.randomUUID().toString().replace("-", "");
    static final LocalDate TODAY = com.foresight.fcms.shared.time.ApplicationCalendar.today();
    static final String MAKER = "business-maker@test.invalid",
            CHECKER = "business-checker@test.invalid",
            DIRECTOR = "business-director@test.invalid",
            STAFF = "business-staff@test.invalid",
            ADMIN = "business-admin@test.invalid";

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
    @Autowired AccessService access;
    @Autowired com.foresight.fcms.auth.service.UserAdministrationService users;
    @Autowired com.foresight.fcms.auth.UserAccountDetailsService userDetails;
    @Autowired LedgerService ledger;
    @Autowired AccountingService accounts;
    @Autowired PostingMappingService templates;
    @Autowired VoucherService vouchers;
    @Autowired JournalService journals;
    @Autowired AccountingSummaryService summary;
    @Autowired ClientRevenueService clients;
    @Autowired DashboardService dashboards;
    @Autowired com.foresight.fcms.workforce.service.WorkService work;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    long cash, income, expense, receiptTemplate, paymentTemplate;

    void actor(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long account(
            String code, String parent, String classification, int depth, boolean cashAccount) {
        var existing = db.queryForList("select id from gl_accounts where code=?", Long.class, code);
        if (!existing.isEmpty()) return existing.getFirst();
        return db.queryForObject(
                "insert into"
                    + " gl_accounts(code,name,classification,normal_balance,depth,parent_id,cash_account)"
                    + " values (?,?,?,?,?,(select id from gl_accounts where code=?),?) returning"
                    + " id",
                Long.class,
                code,
                "Synthetic " + code,
                classification,
                classification.equals("INCOME") ? "CREDIT" : "DEBIT",
                depth,
                parent,
                cashAccount);
    }

    @BeforeAll
    void fixtures() {
        for (var pair :
                Map.of(
                                MAKER,
                                "Accounts Clerk",
                                CHECKER,
                                "Accounts Clerk",
                                DIRECTOR,
                                "Director",
                                STAFF,
                                "Staff",
                                ADMIN,
                                "Administrator")
                        .entrySet())
            db.update(
                    "insert into users(first_name,last_name,email,password,status_id,role_id)"
                        + " select 'Synthetic','Workspace',?,?,1,role_id from roles where"
                        + " role_name=?",
                    pair.getKey(),
                    passwords.encode("Verification123!"),
                    pair.getValue());
        actor(MAKER);
        AccountingHistoryFixtures.books(db, TODAY.withDayOfYear(1).minusYears(1), "ZERO", MAKER, CHECKER);
        actor(MAKER);
        for (int year = TODAY.getYear() - 1; year <= TODAY.getYear(); year++) {
            AccountingHistoryFixtures.period(db, "Synthetic " + year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), MAKER);
        }
        cash = account("111001", "111000", "ASSET", 4, true);
        account("510000", "500000", "INCOME", 2, false);
        account("511000", "510000", "INCOME", 3, false);
        income = account("511001", "511000", "INCOME", 4, false);
        account("410000", "400000", "EXPENSE", 2, false);
        account("411000", "410000", "EXPENSE", 3, false);
        expense = account("411001", "411000", "EXPENSE", 4, false);
        receiptTemplate = template("INCOME_IN", "RECEIPT", cash, income);
        paymentTemplate = template("EXPENSE_PAYMENT", "PAYMENT", expense, cash);
    }

    long template(String event, String kind, long debit, long credit) {
        actor(MAKER);
        var f = new PostingMappingForm();
        f.setTransactionId(
                db.queryForObject(
                        "select id from gl_transactions where source_event=? order by id limit 1",
                        Long.class,
                        event));
        f.setEvent(event);
        f.setVoucherKind(kind);
        f.setDebitAccountId(debit);
        f.setCreditAccountId(credit);
        f.setCashFlow("OPERATING");
        f.setPolicyReference("Synthetic approved treatment");
        long id = templates.create(f);
        actor(CHECKER);
        templates.review(id, 0, "approve", "Independent template review");
        return id;
    }

    LedgerForm movement(String operation, String amount, long units, LocalDate date) {
        var f = new LedgerForm();
        f.setOperation(operation);
        f.setDate(date);
        f.setCategory(operation);
        f.setDescription("Synthetic " + operation);
        f.setReference(UUID.randomUUID().toString());
        f.setAmount(new BigDecimal(amount));
        f.setUnits(units);
        return f;
    }

    @Test
    @Order(1)
    void roleBundlesAndHomeRoutesAreSeparate() {
        actor(MAKER);
        assertTrue(access.current().has("accounting.write"));
        assertFalse(access.current().has("iam.user.manage"));
        assertFalse(access.current().has("leave.review"));
        assertEquals("/accounting/dashboard", dashboards.home());
        actor(DIRECTOR);
        assertTrue(access.current().has("leave.review"));
        assertTrue(access.current().has("staff-report.read.all"));
        assertTrue(access.current().has("client.write"));
        assertTrue(access.current().has("sales.invoice.read"));
        assertFalse(access.current().has("finance.write"));
        assertFalse(access.current().has("accounting.setup"));
        assertEquals("/director/dashboard", dashboards.home());
        actor(ADMIN);
        assertTrue(access.current().has("iam.user.manage"));
        assertFalse(access.current().has("accounting.read"));
        assertFalse(access.current().has("client.read"));
        assertEquals("/admin/users", dashboards.home());
        actor(STAFF);
        assertTrue(access.current().has("leave.write"));
        assertTrue(access.current().has("staff-report.write"));
        assertFalse(access.current().has("task.read"));
        assertFalse(access.current().has("expense.read"));
        assertFalse(access.current().has("leave.review"));
        assertEquals("/staff/dashboard", dashboards.home());
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from roles where lower(role_name)='hr'", Integer.class));
        assertThrows(AccessDeniedException.class, () -> ledger.balance(LedgerKind.BULK));
    }

    @Test
    @Order(2)
    void smsStockAndFilteredProfitAreIndependentOfPaging() {
        actor(MAKER);
        ledger.post(LedgerKind.BULK, movement("STOCK_PURCHASE", "100.00", 10, TODAY.minusYears(1)));
        ledger.post(LedgerKind.BULK, movement("STOCK_PURCHASE", "80.00", 20, TODAY));
        var sale = movement("CLIENT_SALE", "200.00", 5, TODAY);
        sale.setDirection("OUT");
        sale.setUnitDirection("IN");
        long saleId = ledger.post(LedgerKind.BULK, sale);
        assertEquals(new BigDecimal("200.00"), ledger.find(LedgerKind.BULK, saleId).amountIn());
        assertEquals(5, ledger.find(LedgerKind.BULK, saleId).unitsOut());
        assertEquals(saleId, ledger.post(LedgerKind.BULK, sale));
        for (int n = 0; n < 11; n++)
            ledger.post(LedgerKind.BULK, movement("CLIENT_SALE", "1.00", 1, TODAY));
        var query = new LedgerQuery();
        query.setYear(String.valueOf(TODAY.getYear()));
        query.setSize(10);
        var view = ledger.workspace(LedgerKind.BULK, query);
        assertEquals(10, view.records().rows().size());
        assertTrue(view.records().hasNext());
        assertEquals(new BigDecimal("211.00"), view.totals().amountIn());
        assertEquals(new BigDecimal("80.00"), view.totals().amountOut());
        assertEquals(new BigDecimal("131.00"), view.totals().profit());
        assertEquals(14, view.balance().units());
        query.setCategory("STOCK_PURCHASE");
        view = ledger.workspace(LedgerKind.BULK, query);
        assertEquals(1, view.records().rows().size());
        assertEquals(14, view.balance().units());
        assertEquals(new BigDecimal("80.00"), view.totals().amountOut());
        var wrong = movement("DEPOSIT", "1.00", 1, TODAY);
        assertThrows(IllegalArgumentException.class, () -> ledger.post(LedgerKind.BULK, wrong));
        sale.setAmount(new BigDecimal("201.00"));
        assertThrows(IllegalArgumentException.class, () -> ledger.post(LedgerKind.BULK, sale));
    }

    @Test
    @Order(3)
    void pettyCashRowsFollowDateOrderAndRetainEarlierFilteredMovements() {
        actor(MAKER);
        long deposit = ledger.post(LedgerKind.PETTY, movement("DEPOSIT", "100.00", 0, TODAY));
        ledger.post(LedgerKind.PETTY, movement("WITHDRAWAL", "25.00", 0, TODAY));
        ledger.post(LedgerKind.PETTY, movement("DEPOSIT", "10.00", 0, TODAY.minusDays(1)));
        var query = new LedgerQuery();
        query.setFrom(TODAY);
        query.setSort("date");
        query.setDirection("asc");
        var view = ledger.workspace(LedgerKind.PETTY, query);
        assertEquals(2, view.records().rows().size());
        assertEquals(new BigDecimal("110.00"), view.records().rows().getFirst().balance());
        assertEquals(new BigDecimal("85.00"), view.balance().amount());
        assertEquals(
                new BigDecimal("100.00"),
                db.queryForObject(
                        "select balance_after from ledger_entries where id=?",
                        BigDecimal.class,
                        deposit));
    }

    VoucherForm voucher(long template, String amount) {
        var f = new VoucherForm();
        var mapping=templates.find(template);
        f.setActivityId(db.queryForObject("select activity_id from gl_transactions where id=?",Long.class,mapping.transactionId()));
        f.setMoneyAccountId(mapping.voucherKind().equals("RECEIPT")?mapping.debitAccountId():mapping.creditAccountId());
        f.setCashFlow(mapping.cashFlow());f.setParty("Synthetic party");
        f.getRows().getFirst().setMappingId(template);
        f.getRows().getFirst().setAmount(new BigDecimal(amount));
        f.getRows().getFirst().setDescription("Synthetic transaction");
        f.setDate(TODAY);
        f.setReference("SYN-" + UUID.randomUUID());
        f.setDescription("Synthetic configured voucher");
        f.setEvidenceReference("Synthetic evidence");
        return f;
    }

    void postVoucher(String route, VoucherForm f) {
        actor(MAKER);
        long id = vouchers.create(VoucherService.kind(route.equals("receipt")?"receipts":"payments"), f,false);
        journals.transition(id, 0, "submit", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> journals.transition(id, 1, "post", "Own review refused"));
        actor(CHECKER);
        journals.transition(id, 1, "post", "Independent posting review");
    }

    @Test
    @Order(4)
    void configuredVouchersUseApprovedAccountsAndRetainMakerCheckerAndIdempotency() {
        actor(MAKER);
        var f = voucher(receiptTemplate, "123.45");
        long id = vouchers.create("RECEIPT", f,false);
        assertEquals(id, vouchers.create("RECEIPT", f,false));
        assertEquals(cash, journals.find(id).lines().getFirst().accountId());
        assertEquals(income, journals.find(id).lines().getLast().accountId());
        assertThrows(org.springframework.dao.DataAccessException.class,
                ()->db.update("update gl_vouchers set total=1 where journal_id=?",id));
        assertThrows(org.springframework.dao.DataAccessException.class,
                ()->db.update("delete from gl_vouchers where journal_id=?",id));
        assertEquals(new BigDecimal("123.45"),db.queryForObject("select total from gl_vouchers where journal_id=?",BigDecimal.class,id));
        f.getRows().getFirst().setAmount(new BigDecimal("124.45"));
        assertThrows(IllegalArgumentException.class, () -> vouchers.create("RECEIPT", f,false));
        f.getRows().getFirst().setAmount(new BigDecimal("123.45"));
        assertThrows(
                IllegalArgumentException.class,
                () -> vouchers.create("PAYMENT", voucher(receiptTemplate, "20.00"),false));
        journals.transition(id, 0, "submit", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> journals.transition(id, 1, "post", "No self approval"));
        actor(CHECKER);
        journals.transition(id, 1, "post", "Independent receipt review");
        postVoucher("payment", voucher(paymentTemplate, "20.00"));
        actor(DIRECTOR);
        var totals = summary.posted(TODAY.getYear());
        assertEquals(new BigDecimal("123.45"), totals.income());
        assertEquals(new BigDecimal("20.00"), totals.expenses());
        assertThrows(
                AccessDeniedException.class,
                () -> vouchers.create("RECEIPT", voucher(receiptTemplate, "1.00"),false));
    }

    HttpClient session(String email) throws Exception {
        var client =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var login = get(client, "/login");
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertTrue(token.find());
        String body =
                "email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&password=Verification123!&_csrf="
                        + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        var result =
                client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(302, result.statusCode());
        return client;
    }

    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port).resolve(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Order(5)
    void renderedWorkspacesAndForbiddenRoutesMatchTheRoleMatrix() throws Exception {
        var clerk = session(MAKER);
        var page = get(clerk, "/accounting/dashboard");
        assertEquals(200, page.statusCode());
        String sidebar = page.body().split("<aside", 2)[1].split("</aside>", 2)[0];
        assertTrue(sidebar.contains("/finance/bulk-sms"));
        assertFalse(sidebar.contains("/finance/expenses"));
        assertFalse(sidebar.contains("view=revenue"));
        assertFalse(sidebar.contains("/accounting/trial-balance"));
        assertFalse(sidebar.contains("/admin/users"));
        assertEquals(200, get(clerk, "/finance/bulk-sms").statusCode());
        assertEquals(200, get(clerk, "/accounting/vouchers/receipt/new").statusCode());
        assertTrue(get(clerk,"/accounting/journals?kind=RECEIPT").body().contains("href=\"/accounting/journals?kind=RECEIPT\">Clear</a>"));
        assertTrue(get(clerk,"/finance/bulk-sms?from="+TODAY.minusYears(1).withDayOfYear(1)+"&to="+TODAY.withDayOfYear(1).minusDays(1)).body()
                .contains(TODAY.minusYears(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertEquals(403, get(clerk, "/admin/users").statusCode());
        var director = session(DIRECTOR);
        assertEquals(200, get(director, "/director/dashboard").statusCode());
        assertEquals(200, get(director, "/leaves").statusCode());
        assertEquals(403, get(director, "/accounting/config/activities").statusCode());
        assertEquals(403, get(director, "/finance/petty-cash").statusCode());
        var staff = session(STAFF);
        assertEquals(200, get(staff, "/staff/dashboard").statusCode());
        assertEquals(403, get(staff, "/accounting/dashboard").statusCode());
        assertEquals(403, get(staff, "/tasks").statusCode());
        assertEquals(403, get(staff, "/clients/saccos").statusCode());
        var admin = session(ADMIN);
        assertEquals(200, get(admin, "/admin/users").statusCode());
        assertEquals(403, get(admin, "/accounting/statements/income-statement").statusCode());
        assertEquals(403, get(admin, "/director/dashboard").statusCode());
    }

    @Test
    @Order(6)
    void concurrentSmsSalesCannotOversell() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> sales = new ArrayList<>();
            for (int n = 0; n < 2; n++)
                sales.add(
                        executor.submit(
                                () -> {
                                    actor(MAKER);
                                    start.await();
                                    try {
                                        ledger.post(
                                                LedgerKind.BULK,
                                                movement("CLIENT_SALE", "10.00", 9, TODAY));
                                        return true;
                                    } catch (IllegalArgumentException rejected) {
                                        return false;
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                }));
            start.countDown();
            int successes = 0;
            for (var sale : sales) if (sale.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            actor(MAKER);
            assertEquals(5, ledger.balance(LedgerKind.BULK).units());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(7)
    void clientDashboardUsesStoredPaymentStatusAndSelectedYearVisibleRows() {
        actor(MAKER);
        long year =
                db.queryForObject("select id from financial_year where fyear='2026'", Long.class);
        db.update(
                "insert into"
                    + " saccos(saccos_name,region,financial_year_id,client_status_id,annual_fee,amount,withholding_tax)"
                    + " values ('Synthetic paid','Synthetic',?,1,100,0,10),('Synthetic"
                    + " pending','Synthetic',?,3,200,50,20)",
                year,
                year);
        db.update(
                "insert into"
                    + " saccos(saccos_name,region,financial_year_id,client_status_id,annual_fee,amount,visible)"
                    + " values ('Synthetic archived','Synthetic',?,2,999,999,false)",
                year);
        db.update(
                "insert into"
                    + " saccos(saccos_name,region,financial_year_id,client_status_id,annual_fee,amount)"
                    + " select 'Synthetic previous','Synthetic',id,2,999,999 from financial_year"
                    + " where fyear='2025'");
        var portfolio =
                clients.portfolios(year).stream()
                        .filter(p -> p.slug().equals("saccos"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, portfolio.clients());
        assertEquals(1, portfolio.paid());
        assertEquals(0, portfolio.unpaid());
        assertEquals(1, portfolio.pending());
        assertEquals(new BigDecimal("220.00"), portfolio.annualDifference());
        var dashboard = dashboards.accountantOverview(year, "saccos");
        assertEquals(1, dashboard.portfolios().size());
        assertEquals(portfolio, dashboard.portfolios().getFirst());
        assertEquals(
                "amcos",
                dashboards.accountantOverview(year, "amcos").portfolios().getFirst().slug());
        assertThrows(
                IllegalArgumentException.class,
                () -> dashboards.accountantOverview(year, "unknown"));
    }

    @Test
    @Order(8)
    void directorCanAssignReportsAndApproveStaffLeaveButStaffCannotReview() {
        var leave = new com.foresight.fcms.workforce.dto.WorkForm();
        leave.setSubject("Synthetic leave");
        leave.setDescription("Synthetic leave request");
        actor(STAFF);
        long id = work.save(com.foresight.fcms.workforce.model.WorkKind.LEAVE, null, leave);
        work.transition(com.foresight.fcms.workforce.model.WorkKind.LEAVE, id, 0, "submit", "");
        assertThrows(
                AccessDeniedException.class,
                () ->
                        work.transition(
                                com.foresight.fcms.workforce.model.WorkKind.LEAVE,
                                id,
                                1,
                                "approve",
                                "Self approval"));
        actor(DIRECTOR);
        work.transition(
                com.foresight.fcms.workforce.model.WorkKind.LEAVE,
                id,
                1,
                "approve",
                "Independent director approval");
        assertEquals(
                "APPROVED",
                work.find(com.foresight.fcms.workforce.model.WorkKind.LEAVE, id).status());
        var report = new com.foresight.fcms.workforce.dto.WorkForm();
        report.setSubject("Synthetic assigned report");
        report.setDescription("Synthetic report");
        report.setOwnerId(
                db.queryForObject("select id from users where email=?", Long.class, STAFF));
        long reportId = work.save(com.foresight.fcms.workforce.model.WorkKind.REPORT, null, report);
        actor(STAFF);
        assertEquals(
                reportId,
                work.find(com.foresight.fcms.workforce.model.WorkKind.REPORT, reportId).id());
        actor(MAKER);
        assertThrows(
                AccessDeniedException.class,
                () -> work.find(com.foresight.fcms.workforce.model.WorkKind.REPORT, reportId));
    }

    @Test
    @Order(9)
    void configuredVoucherNativePostKeepsCsrfValuesIdempotencyAndServerOwnedAccounts()
            throws Exception {
        var web = session(MAKER);
        var page = get(web, "/accounting/vouchers/receipt/new?lang=sw");
        assertEquals(200, page.statusCode());
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertTrue(csrf.find());
        var key = UUID.randomUUID();
        var values = new LinkedHashMap<String, String>();
        values.put("mappingId", String.valueOf(receiptTemplate));
        values.put("amount", "0.00");
        values.put("date", TODAY.toString());
        values.put("reference", "Synthetic native receipt");
        values.put("description", "Retained native values");
        values.put("evidenceReference", "Synthetic evidence");
        values.put("requestKey", key.toString());
        assertEquals(403, post(web, "/accounting/vouchers/receipt", values).statusCode());
        values.put("_csrf", csrf.group(1));
        var invalid = post(web, "/accounting/vouchers/receipt", values);
        assertEquals(200, invalid.statusCode());
        assertTrue(invalid.body().contains("Retained native values"));
        assertTrue(invalid.body().contains(key.toString()));
        values.put("amount", "8.50");
        values.put("lines[0].accountId", String.valueOf(expense));
        values.put("kind", "PAYMENT");
        var saved = post(web, "/accounting/vouchers/receipt", values);
        assertEquals(302, saved.statusCode());
        String location = saved.headers().firstValue("Location").orElseThrow();
        assertEquals("/accounting/journals", URI.create(location).getPath());
        assertTrue(URI.create(location).getQuery().contains("kind=RECEIPT"));
        String resource = CreationFeedback.follow(web, saved);
        assertEquals(resource, CreationFeedback.follow(web, post(web, "/accounting/vouchers/receipt", values)));
        long id =
                db.queryForObject(
                        "select id from gl_journals where request_key=?", Long.class, key);
        actor(MAKER);
        assertEquals("RECEIPT", journals.find(id).kind());
        assertEquals(cash, journals.find(id).lines().getFirst().accountId());
        values.put("amount", "9.50");
        assertEquals(200, post(web, "/accounting/vouchers/receipt", values).statusCode());
        assertEquals(
                new BigDecimal("8.50"),
                db.queryForObject(
                        "select total from gl_vouchers where journal_id=?",
                        BigDecimal.class,
                        id));
    }

    @Test
    @Order(10)
    void userFormsRenderEditableDefaultsAndPersistExactPermissionSelections() throws Exception {
        actor(STAFF);
        assertThrows(AccessDeniedException.class, () -> users.options());
        actor(ADMIN);
        var web = session(ADMIN);
        var initial = get(web, "/admin/users/new");
        assertEquals(200, initial.statusCode());
        assertTrue(initial.body().contains("user-permissions.js"));
        assertTrue(initial.body().contains("password-visibility.js"));
        assertTrue(initial.body().contains("class=\"fcms-password-toggle\""));
        assertTrue(initial.body().contains("id=\"lang\" name=\"lang\""));
        var localized = get(web, "/admin/users?lang=sw&search=Synthetic&size=10");
        assertEquals(200, localized.statusCode());
        assertTrue(localized.body().contains("<html lang=\"sw\">"));
        assertTrue(localized.body().contains("value=\"sw\" lang=\"sw\" selected"));
        assertTrue(localized.body().contains("name=\"search\" value=\"Synthetic\""));
        assertTrue(localized.body().contains("name=\"size\" value=\"10\""));
        assertTrue(get(web, "/admin/users/new").body().contains("<html lang=\"sw\">"));
        assertTrue(get(web, "/admin/users/new?lang=en").body().contains("value=\"en\" lang=\"en\" selected"));
        assertRoleChecks(initial.body(), "Accounts Clerk", Set.of());
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(initial.body());
        assertTrue(csrf.find());
        for (String role : List.of("Accounts Clerk", "Staff", "Administrator", "Director")) {
            long roleId = db.queryForObject("select role_id from roles where role_name=?", Long.class, role);
            var values = new LinkedHashMap<String, String>();
            values.put("firstName", "Synthetic");
            values.put("lastName", "Permissions");
            values.put("email", "permissions-" + roleId + "@test.invalid");
            values.put("roleId", String.valueOf(roleId));
            values.put("statusId", "1");
            values.put("password", "Verification123!");
            values.put("_claims", "on");
            values.put("permissionsSubmitted", "true");
            var chosen = roleDefaults(role);
            chosen.remove("account.write");
            String additional = role.equals("Administrator") ? "finance.read" : "backup.manage";
            chosen.add(additional);
            assertEquals(403, post(web, "/admin/users", values, chosen).statusCode());
            values.put("_csrf", csrf.group(1));
            var created = post(web, "/admin/users", values, chosen);
            assertEquals(302, created.statusCode());
            long id = db.queryForObject("select id from users where email=?", Long.class, values.get("email"));
            assertEquals(List.of(additional), db.queryForList("select claim from user_claims where user_id=? order by claim", String.class, id));
            assertEquals(List.of("account.write"), db.queryForList("select claim from user_claim_denials where user_id=? order by claim", String.class, id));
            assertEquals(1, db.queryForObject("select count(*) from audit_events where resource='users' and resource_id=? and action='iam.user.permission.change' and detail like 'account.write:%excluded=true'", Integer.class, id));
            assertEquals(chosen, new HashSet<>(users.form(id).getClaims()));
            actor(values.get("email"));
            assertFalse(access.current().has("account.write"));
            assertFalse(userDetails.loadUserByUsername(values.get("email")).getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("account.write")));
            actor(ADMIN);
            var targetSession = session(values.get("email"));
            assertEquals(403, get(targetSession, "/account").statusCode());
            var edit = get(web, "/admin/users/" + id + "/edit");
            assertEquals(200, edit.statusCode());
            assertTrue(edit.body().contains("data-password-visibility"));
            assertTrue(edit.body().contains("aria-controls=\"password\""));
            assertRoleChecks(edit.body(), chosen);
            values.put("version", "0");
            values.put("password", "");
            values.put("email", "invalid");
            var invalid = post(web, "/admin/users/" + id, values, chosen);
            assertEquals(200, invalid.statusCode());
            assertRoleChecks(invalid.body(), chosen);
            assertFalse(invalid.body().contains("data-role-default"));
            assertEquals(0, db.queryForObject("select version from users where id=?", Integer.class, id));
            values.put("email", "permissions-" + roleId + "@test.invalid");
            var forged = new HashSet<>(chosen); forged.add("unknown.permission");
            assertEquals(200, post(web, "/admin/users/" + id, values, forged).statusCode());
            assertEquals(0, db.queryForObject("select version from users where id=?", Integer.class, id));
            assertEquals(302, post(web, "/admin/users/" + id, values, chosen).statusCode());
            var expired = get(targetSession, "/account");
            assertTrue(expired.statusCode() == 302 || expired.body().toLowerCase(Locale.ROOT).contains("expired"));
            assertEquals(302, get(targetSession, "/account").statusCode(), "Permission edits invalidate existing sessions");
            assertRoleChecks(get(web, "/admin/users/" + id + "/edit").body(), chosen);
            values.put("version", "1");
            chosen.add("account.write");
            assertEquals(302, post(web, "/admin/users/" + id, values, chosen).statusCode());
            assertEquals(0, db.queryForObject("select count(*) from user_claim_denials where user_id=?", Integer.class, id));
            assertEquals(1, db.queryForObject("select count(*) from audit_events where resource='users' and resource_id=? and action='iam.user.permission.change' and detail like 'account.write:%excluded=true -> selected=true, excluded=false'", Integer.class, id));
            assertEquals(200, get(session(values.get("email")), "/account").statusCode());
            assertTrue(userDetails.loadUserByUsername(values.get("email")).getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("account.write")));
            values.put("version", "2");
            values.put("roleId", String.valueOf(db.queryForObject("select role_id from roles where role_name='Staff'", Long.class)));
            var staffSelection = roleDefaults("Staff"); staffSelection.remove("leave.write"); staffSelection.add(additional);
            assertEquals(302, post(web, "/admin/users/" + id, values, staffSelection).statusCode());
            assertRoleChecks(get(web, "/admin/users/" + id + "/edit").body(), staffSelection);
            assertEquals(staffSelection, new HashSet<>(users.form(id).getClaims()));
            actor(values.get("email"));
            assertFalse(access.current().has("leave.write"));
            assertFalse(access.current().has("iam.user.manage"));
            assertFalse(access.current().has("accounting.write"));
            actor(ADMIN);
            assertTrue(roleDefaults(role).contains("account.write"), "The role's defaults remain unchanged");
        }
    }

    @Test
    @Order(11)
    void exclusionsCannotRemoveTheLastAdministratorsWorkspaceReadOrManageCapability() throws Exception {
        actor(ADMIN);
        long admin = access.current().id();
        var states = db.queryForList("select id,status_id from users where id<>?", admin);
        db.update("update users set status_id=2 where id<>?", admin);
        try {
            var web = session(ADMIN);
            long version = users.form(admin).getVersion();
            for (String claim : List.of("workspace.admin", "iam.user.read", "iam.user.manage")) {
                var form = users.form(admin);
                var selected = new HashSet<>(form.getClaims()); selected.remove(claim);
                form.setClaims(new ArrayList<>(selected));
                var failure = assertThrows(IllegalArgumentException.class, () -> users.save(admin, form));
                assertEquals("error.lastAdmin", failure.getMessage());
                assertEquals(version, users.form(admin).getVersion());
                assertEquals(0, db.queryForObject("select count(*) from user_claim_denials where user_id=?", Integer.class, admin));
                assertEquals(200, get(web, "/admin/users").statusCode(), "Rejected permission edits must not revoke the admin session");
            }
        } finally {
            for (var state : states) db.update("update users set status_id=? where id=?", state.get("status_id"), state.get("id"));
        }
    }

    @Test
    @Order(12)
    void emptySelectionsAndLegacyFormsCannotSilentlyRestoreExplicitlyExcludedDefaults() {
        actor(ADMIN);
        var form = permissionUser("empty-selection@test.invalid", "Staff");
        var selected = roleDefaults("Staff"); selected.remove("account.write");
        form.setPermissionsSubmitted(true); form.setClaims(new ArrayList<>(selected));
        long id = users.save(null, form);
        form.setPermissionsSubmitted(false); form.setClaims(List.of("account.write"));
        users.save(id, form);
        actor(form.getEmail());
        assertFalse(access.current().has("account.write"), "A legacy grant cannot bypass a stored exclusion");
        actor(ADMIN);
        var edit = users.form(id); edit.getClaims().clear();
        users.save(id, edit);
        assertTrue(users.form(id).getClaims().isEmpty());
        assertEquals(roleDefaults("Staff"), new HashSet<>(db.queryForList("select claim from user_claim_denials where user_id=?", String.class, id)));
        assertTrue(userDetails.loadUserByUsername(form.getEmail()).getAuthorities().isEmpty());
        actor(form.getEmail());
        assertTrue(access.current().claims().isEmpty(), "Workspace fallback must not restore an unchecked workspace permission");
        assertThrows(AccessDeniedException.class, () -> users.save(id, edit));
    }

    @Test
    @Order(13)
    void concurrentAdministratorExclusionsKeepOneAdministratorAndRollbackTheLosingEdit() throws Exception {
        actor(ADMIN);
        long first = users.save(null, permissionUser("concurrent-admin-a@test.invalid", "Administrator"));
        long second = users.save(null, permissionUser("concurrent-admin-b@test.invalid", "Administrator"));
        var states = db.queryForList("select id,status_id from users where id not in (?,?)", first, second);
        db.update("update users set status_id=2 where id not in (?,?)", first, second);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var calls = new ArrayList<Future<String>>();
            for (String email : List.of("concurrent-admin-a@test.invalid", "concurrent-admin-b@test.invalid")) {
                calls.add(pool.submit(() -> {
                    actor(email);
                    try {
                        long id = access.current().id();
                        var edit = users.form(id);
                        var selected = new ArrayList<>(edit.getClaims()); selected.remove("iam.user.manage");
                        edit.setClaims(selected);
                        barrier.await(10, TimeUnit.SECONDS);
                        try {users.save(id, edit); return "saved";}
                        catch (IllegalArgumentException failure) {return failure.getMessage();}
                    } finally {SecurityContextHolder.clearContext();}
                }));
            }
            var results = new ArrayList<String>();
            for (var call : calls) results.add(call.get(20, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(results, "saved"));
            assertEquals(1, Collections.frequency(results, "error.lastAdmin"));
            assertEquals(1, db.queryForObject("select count(*) from user_claim_denials where user_id in (?,?) and claim='iam.user.manage'", Integer.class, first, second));
            assertEquals(1L, db.queryForObject("select sum(version) from users where id in (?,?)", Long.class, first, second));
        } finally {
            for (var state : states) db.update("update users set status_id=? where id=?", state.get("status_id"), state.get("id"));
        }
    }

    com.foresight.fcms.auth.dto.UserForm permissionUser(String email, String role) {
        var form = new com.foresight.fcms.auth.dto.UserForm();
        form.setFirstName("Synthetic"); form.setLastName("Permissions"); form.setEmail(email);
        form.setPassword("Verification123!");
        form.setRoleId(db.queryForObject("select role_id from roles where role_name=?", Long.class, role));
        return form;
    }

    @Test
    @Order(14)
    void expiredUserFormsStayForbiddenWithoutLosingSecurityAndFreshSessionTokensAllowCreate() throws Exception {
        var expiredSession = session(ADMIN);
        var originalForm = get(expiredSession, "/admin/users/new");
        assertEquals(200, originalForm.statusCode());
        assertTrue(originalForm.body().contains("data-form-session-url"));
        assertTrue(originalForm.body().contains("user-form-session.js"));
        String oldToken = csrfValue(originalForm.body());
        long before = db.queryForObject("select count(*) from users", Long.class);
        assertEquals(302, post(expiredSession, "/logout", Map.of("_csrf", oldToken)).statusCode());
        var values = new LinkedHashMap<String, String>();
        values.put("_csrf", oldToken);
        values.put("firstName", "DoNotEchoSessionDraft"); values.put("lastName", "Verification");
        values.put("email", "session-recovery@test.invalid"); values.put("password", "Verification123!");
        values.put("roleId", String.valueOf(db.queryForObject("select role_id from roles where role_name='Staff'", Long.class)));
        values.put("statusId", "1"); values.put("permissionsSubmitted", "true"); values.put("_claims", "on");
        var rejected = post(expiredSession, "/admin/users", values, roleDefaults("Staff"));
        assertEquals(403, rejected.statusCode());
        assertTrue(rejected.body().contains("Unable to submit"));
        assertTrue(rejected.body().contains("/admin/users/new"));
        assertFalse(rejected.body().contains("Whitelabel Error Page"));
        assertFalse(rejected.body().contains("DoNotEchoSessionDraft"));
        assertFalse(rejected.body().contains("Verification123!"));
        assertEquals(before, db.queryForObject("select count(*) from users", Long.class));
        assertEquals(302, get(expiredSession, "/admin/users/form-session").statusCode());
        var renewedSession = session(ADMIN);
        var verified = get(renewedSession, "/admin/users/form-session?userId=999999");
        assertEquals(200, verified.statusCode());
        assertTrue(verified.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        assertTrue(verified.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        String actorId = db.queryForObject("select id::text from users where email=?", String.class, ADMIN);
        assertTrue(verified.body().contains("\"userId\":\"" + actorId + "\""));
        assertTrue(verified.body().contains("\"parameterName\":\"_csrf\""));
        var token = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(verified.body());
        assertTrue(token.find());
        assertEquals(403, post(renewedSession, "/admin/users", values, roleDefaults("Staff")).statusCode());
        assertEquals(before, db.queryForObject("select count(*) from users", Long.class));
        values.put("_csrf", token.group(1));
        assertEquals(302, post(renewedSession, "/admin/users", values, roleDefaults("Staff")).statusCode());
        assertEquals(before + 1, db.queryForObject("select count(*) from users", Long.class));
        assertEquals(403, get(session(STAFF), "/admin/users/form-session").statusCode());
    }

    String csrfValue(String html) {
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(token.find()); return token.group(1);
    }

    void assertRoleChecks(String html, String role, Set<String> extras) {
        var selected = roleDefaults(role); selected.addAll(extras);
        assertRoleChecks(html, selected);
    }

    HashSet<String> roleDefaults(String role) {
        return new HashSet<>(db.queryForList("select c.claim from role_claims c join roles r on r.role_id=c.role_id where r.role_name=?", String.class, role));
    }

    void assertRoleChecks(String html, Set<String> selected) {
        var inputs = Pattern.compile("<input[^>]*data-role-permission[^>]*>").matcher(html);
        int count = 0;
        while (inputs.find()) {
            String tag = inputs.group();
            var claim = Pattern.compile("value=\"([^\"]+)\"").matcher(tag);
            assertTrue(claim.find());
            assertEquals(selected.contains(claim.group(1)), tag.contains(" checked"), tag);
            assertFalse(tag.contains(" disabled"), tag);
            count++;
        }
        assertEquals(db.queryForObject("select count(*) from claims", Integer.class), count);
    }

    @Test
    @Order(15)
    void retiredPagesCannotRenderExportOrMutateAndUserLinksOpenEditDirectly() throws Exception {
        var admin = session(ADMIN);
        actor(ADMIN);
        long adminId = access.current().id();
        var retiredClaims = Set.of("expense.read", "expense.write", "expense.approve", "expense.assign",
                "expense.evidence.write", "expense.evidence.review", "finance.approve", "report.read");
        assertTrue(Collections.disjoint(retiredClaims,
                db.queryForList("select name from claims", String.class)));
        assertTrue(Collections.disjoint(retiredClaims, access.current().claims()));
        String register = get(admin, "/admin/users").body();
        assertTrue(register.contains("href=\"/admin/users/" + adminId + "/edit\""));
        assertFalse(register.contains("href=\"/admin/users/" + adminId + "\""));
        var oldUserLink = get(admin, "/admin/users/" + adminId);
        assertEquals(302, oldUserLink.statusCode());
        assertEquals("/admin/users/" + adminId + "/edit", URI.create(oldUserLink.headers().firstValue("location").orElseThrow()).getPath());
        for (String route : List.of("/admin/users/new", "/admin/users/" + adminId + "/edit")) {
            var page = get(admin, route);
            assertEquals(200, page.statusCode());
            for (String claim : retiredClaims) assertFalse(page.body().contains("value=\"" + claim + "\""), claim);
        }
        var rejected = users.form(adminId);
        rejected.getClaims().add("expense.read");
        long version = rejected.getVersion();
        assertThrows(IllegalArgumentException.class, () -> users.save(adminId, rejected));
        assertEquals(version, users.form(adminId).getVersion());

        var paths = new ArrayList<>(List.of("/finance/expenses", "/finance/expenses/new",
                "/finance/expenses/1/edit", "/finance/expenses/categories", "/finance/expenses/evidence",
                "/finance/expenses/1/evidence/new", "/finance/expenses/1/evidence/1",
                "/finance/expenses/print.pdf", "/finance/expenses/categories/print.pdf",
                "/finance/income", "/finance/income/new", "/finance/income/1", "/finance/income/print.pdf",
                "/finance/income/1/print.pdf", "/finance/cloud-backup/billing", "/finance/cloud-backup/billing/print.pdf",
                "/reports", "/reports/print?year=2026", "/reports/statement.pdf?year=2026"));
        for (String category : List.of("saccos", "amcos", "unions", "microfinance", "others"))
            for (String suffix : List.of("", "/export.csv", "/print.pdf"))
                paths.add("/clients/" + category + "/revenues" + suffix);
        var clerk = session(MAKER);
        for (String path : paths) {
            var response = get(path.startsWith("/clients/") ? clerk : admin, path);
            assertEquals(410, response.statusCode(), path);
            assertTrue(response.body().isEmpty(), path);
            assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
        }
        for (String path : List.of("/saccos_revenues.php", "/union_revenues.php", "/microfinance_report.php", "/other_report.php"))
            assertEquals(410, get(clerk, path).statusCode(), path);
        String token = csrfValue(get(admin, "/admin/users/new").body());
        long count = db.queryForObject("select count(*) from expenses", Long.class);
        for (String path : List.of("/finance/expenses", "/finance/expenses/1",
                "/finance/expenses/1/actions/pay", "/finance/expenses/1/evidence",
                "/finance/expenses/1/evidence/1/review", "/finance/income", "/finance/income/1/reverse"))
            assertEquals(410, post(admin, path, Map.of("_csrf", token, "amount", "999.99")).statusCode(), path);
        assertEquals(count, db.queryForObject("select count(*) from expenses", Long.class));
        assertEquals(403, post(admin, "/finance/expenses", Map.of()).statusCode());
        for (String path : List.of("/accounting/vouchers/receipt/new", "/accounting/vouchers/payment/new",
                "/finance/petty-cash", "/finance/bulk-sms", "/finance/cloud-backup",
                "/accounting/statements/income-statement", "/accounting/trial-balance"))
            assertEquals(200, get(clerk, path).statusCode(), path);
        var statements = get(clerk, "/accounting/statements");
        assertEquals(302, statements.statusCode());
        assertEquals("/accounting/statements/income-statement", URI.create(statements.headers().firstValue("location").orElseThrow()).getPath());
        assertEquals(403, get(session(STAFF), "/admin/users/" + adminId + "/edit").statusCode());
    }

    @Test
    @Order(22)
    void userCreationReturnsToListWithOneAuthenticNoticeAndFieldSpecificConflicts() throws Exception {
        var web = session(ADMIN);
        var initial = get(web, "/admin/users/new?lang=en");
        var values = new LinkedHashMap<String,String>();
        values.put("_csrf", csrfValue(initial.body()));
        values.put("firstName", "Creation"); values.put("lastName", "Fixture");
        values.put("email", "creation-" + UUID.randomUUID() + "@test.invalid");
        values.put("roleId", db.queryForObject("select role_id from roles where role_name='Staff'", Long.class).toString());
        values.put("statusId", "1"); values.put("permissionsSubmitted", "true");
        values.put("password", "SyntheticCreation123!");
        long count = db.queryForObject("select count(*) from users", Long.class);
        var created = post(web, "/admin/users", values);
        assertEquals(302, created.statusCode());
        String destination = created.headers().firstValue("Location").orElseThrow();
        assertEquals("/admin/users", URI.create(destination).getPath());
        // A concurrent registry request must not consume a different destination's flash.
        assertFalse(get(web, "/admin/users?saved&created=999").body().contains("data-resource-notice"));
        var listed = get(web, destination);
        assertEquals(200, listed.statusCode());
        String resource = CreationFeedback.resourceUrl(listed.body());
        assertTrue(resource.matches("/admin/users/[0-9]+/edit"));
        assertTrue(listed.body().contains("User created successfully."));
        assertTrue(listed.body().indexOf("data-resource-notice") < listed.body().indexOf("resource-table" , listed.body().indexOf("<main")));
        assertFalse(get(web, destination).body().contains("data-resource-notice"));
        assertEquals(count + 1, db.queryForObject("select count(*) from users", Long.class));
        var duplicate = post(web, "/admin/users", values);
        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.body().matches("(?s).*id=\"email\"[^>]*>\\s*<span[^>]*class=\"fcms-error\"[^>]*>.*"));
        assertTrue(duplicate.body().contains("value=\"Creation\""));
        assertFalse(duplicate.body().contains("SyntheticCreation123!"));
        assertFalse(duplicate.body().contains("data-resource-notice"));
        values.put("email", "different-" + UUID.randomUUID() + "@test.invalid");
        values.put("password", "short");
        var passwordError = post(web, "/admin/users", values);
        assertEquals(200, passwordError.statusCode());
        assertTrue(passwordError.body().contains("id=\"password.errors\""));
        assertEquals(count + 1, db.queryForObject("select count(*) from users", Long.class));
        var probe = get(web, "/form-session");
        assertEquals(200, probe.statusCode());
        assertEquals("no-store", probe.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(probe.body().contains("\"parameterName\":\"_csrf\""));
        assertEquals(302, get(HttpClient.newHttpClient(), "/form-session").statusCode());
        assertEquals(403, get(session(STAFF), "/admin/users/new").statusCode());
    }

    @Test
    @Order(23)
    void clientStaffAndConfigurationCreatesShowFeedbackInTheirOwningRegisters() throws Exception {
        var clerk = session(MAKER);
        long client = 0;
        for (String category : List.of("saccos", "amcos", "unions", "microfinance", "others")) {
            String route = "/clients/" + category;
            var values = creationValues(clerk, route + "/new?lang=en");
            values.put(category.equals("saccos") ? "saccosName" : "clientName", "Creation fixture " + category);
            values.put("region", "Synthetic region"); values.put("entryDate", TODAY.toString());
            values.put("paymentStatusId", db.queryForObject("select min(id) from client_status", Long.class).toString());
            values.put("financialYearId", db.queryForObject("select min(id) from financial_year", Long.class).toString());
            values.put("expireDate", TODAY.minusDays(1).toString());
            var invalidDates = post(clerk, route, values);
            assertEquals(200, invalidDates.statusCode()); assertTrue(invalidDates.body().contains("id=\"expireDate.errors\""));
            values.remove("expireDate");
            var saved = post(clerk, route, values);
            assertEquals(302, saved.statusCode(), category + ": " + saved.body());
            assertEquals(route, URI.create(saved.headers().firstValue("Location").orElseThrow()).getPath());
            String resource = CreationFeedback.follow(clerk, saved);
            if (category.equals("saccos")) client = Long.parseLong(resource.substring(resource.lastIndexOf('/') + 1));
        }
        for (String workspace : List.of("tasks", "leaves", "staff-reports")) {
            var web = workspace.equals("tasks") ? session(DIRECTOR) : session(STAFF);
            String route = "/" + workspace;
            var values = creationValues(web, route + "/new?lang=en");
            values.put("subject", "Creation fixture " + workspace); values.put("description", "Synthetic work description");
            values.put("startAt", TODAY + "T08:00"); values.put("endAt", TODAY + "T09:00");
            if (workspace.equals("tasks")) values.put("clientId", Long.toString(client));
            var saved = post(web, route, values);
            assertEquals(302, saved.statusCode(), workspace + ": " + saved.body());
            assertEquals(route, URI.create(saved.headers().firstValue("Location").orElseThrow()).getPath());
            assertTrue(CreationFeedback.follow(web, saved).startsWith(route + "/"));
        }
        var activity = creationValues(clerk, "/accounting/config/activities?create=true&lang=en");
        activity.put("code", "CREATE_" + UUID.randomUUID().toString().substring(0,8)); activity.put("name", "Creation fixture activity");
        var savedActivity = post(clerk, "/accounting/config/activities", activity);
        String destination = savedActivity.headers().firstValue("Location").orElseThrow();
        var list = get(clerk,destination);
        assertTrue(CreationFeedback.resourceUrl(list.body()).startsWith("/accounting/config/activities/"));
        assertTrue(Pattern.compile("id=\"config-create-modal\"[^>]*\\bhidden\\b").matcher(list.body()).find());
        var duplicate = post(clerk, "/accounting/config/activities", activity);
        assertEquals(302, duplicate.statusCode()); // An identical keyed retry is idempotent.
        activity.put("requestKey",UUID.randomUUID().toString());
        var duplicateCode = post(clerk, "/accounting/config/activities",activity);
        assertEquals(200,duplicateCode.statusCode()); assertTrue(duplicateCode.body().contains("id=\"code.errors\""));
        assertTrue(duplicateCode.body().contains("data-auto-open=\"true\""));
    }

    LinkedHashMap<String,String> creationValues(HttpClient web, String path) throws Exception {
        var page = get(web,path); assertEquals(200,page.statusCode(),path);
        var values = new LinkedHashMap<String,String>(); values.put("_csrf",csrfValue(page.body()));
        var key = Pattern.compile("name=\"requestKey\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        if (key.find()) values.put("requestKey",key.group(1));
        return values;
    }

    HttpResponse<String> post(HttpClient web, String path, Map<String, String> values)
            throws Exception {
        return post(web, path, values, Set.of());
    }

    HttpResponse<String> post(HttpClient web, String path, Map<String, String> values, Collection<String> selectedClaims)
            throws Exception {
        String body =
                values.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"));
        for (String claim : selectedClaims)
            body += "&claims=" + URLEncoder.encode(claim, StandardCharsets.UTF_8);
        return web.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
