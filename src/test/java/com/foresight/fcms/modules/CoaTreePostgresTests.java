package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.AccountingService;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fcms.mail.initial-delay=86400000", "fcms.mail.interval=86400000", "fcms.backup.interval=86400000",
        "spring.autoconfigure.exclude=org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"
})
@EnabledIfEnvironmentVariable(named = "FCMS_TEST_DB_URL", matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoaTreePostgresTests {
    static final String SCHEMA = "coa_tree_" + UUID.randomUUID().toString().replace("-", "");
    static final String ADMIN = "admin@fcms.local", READER = "coa-reader@test.invalid", STAFF = "coa-staff@test.invalid";
    static final String PASSWORD = "TreeVerification123!";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("fcms.storage.avatar-dir", () -> ".verification/avatars");
        p.add("fcms.storage.contract-dir", () -> ".verification/contracts-tested");
    }

    @Autowired AccountingService accounts;
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @LocalServerPort int port;
    long family, batchFamily, codeFamily;

    void actor(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "", List.of()));
    }

    long id(String code) { return db.queryForObject("select id from gl_accounts where code=?", Long.class, code); }

    long create(long parent, String code, String mode) {
        var f = new AccountForm();f.setParentId(parent);f.setCode(code);f.setName("COA fixture " + code);f.setMode(mode);
        return accounts.saveAccount(null, f);
    }

    @BeforeAll
    void fixtures() {
        OperationalTestClaims.enable(db);
        db.update("update users set password=? where email=?", passwords.encode(PASSWORD), ADMIN);
        for (String email : List.of(READER, STAFF)) {
            long user = db.queryForObject("insert into users(first_name,last_name,email,password,status_id,role_id)"
                    + " values ('COA','Fixture',?,?,1,2) returning id", Long.class, email, passwords.encode(PASSWORD));
            if (email.equals(READER)) db.update("insert into user_claims(user_id,claim) values"
                    + " (?,'workspace.admin'),(?,'accounting.read'),(?,'accounting.report')", user, user, user);
        }
        actor(ADMIN);
        family = create(create(id("300000"), "310000", "GROUP"), "311000", "GROUP");
        batchFamily = create(create(id("300000"), "320000", "GROUP"), "321000", "GROUP");
        codeFamily = create(create(id("300000"), "330000", "GROUP"), "331000", "GROUP");
        for (int n = 1; n <= 23; n++) create(batchFamily, "321" + String.format("%03d", n), "POSTING");
    }

    @BeforeEach void signIn() { actor(ADMIN); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void rootsAndRealParentPathsDriveCapabilities() {
        var q = new CoaQuery();var roots = accounts.chart(q, null);
        assertEquals(List.of("100000","200000","300000","400000","500000"), roots.records().rows().stream().map(r -> r.account().code()).toList());
        assertEquals(5, roots.total());
        assertTrue(roots.records().rows().stream().allMatch(r -> r.expandable() && r.addGroup() && !r.addPosting() && !r.editable() && !r.lifecycle()));
        var children = accounts.chart(q, id("110000"));
        assertEquals(List.of("100000", "110000"), children.path().stream().map(a -> a.code()).toList());
        var cashFamily = children.records().rows().stream().filter(r -> r.account().code().equals("111000")).findFirst().orElseThrow();
        assertEquals("GROUP", cashFamily.kind());assertTrue(cashFamily.addPosting());assertFalse(cashFamily.addGroup());
        assertFalse(children.records().rows().stream().anyMatch(r -> !Objects.equals(r.account().parentId(), id("110000"))));
    }

    @Test
    void childPagesAndGlobalFiltersAreBoundedAndStable() {
        var q = new CoaQuery();q.setSize(10);var all = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            q.setPage(page);var chart = accounts.chart(q, batchFamily);
            assertEquals(23, chart.total());assertEquals(page < 2 ? 10 : 3, chart.records().rows().size());
            assertEquals(page < 2, chart.records().hasNext());
            all.addAll(chart.records().rows().stream().map(r -> r.account().id()).toList());
        }
        assertEquals(23, new HashSet<>(all).size());
        q.setPage(0);q.setKind("POSTING");q.setClassification("EQUITY");q.setSearch("COA fixture 321");
        assertEquals(23, accounts.chart(q, null).total());
        for (String sort : List.of("code", "name", "parent", "classification", "kind", "normal", "status")) {
            q.setSort(sort);q.setDirection("asc");var ascending = accounts.chart(q, batchFamily);
            q.setDirection("desc");var descending = accounts.chart(q, batchFamily);
            assertEquals(10, ascending.records().rows().size());assertEquals(10, descending.records().rows().size());
            assertNotEquals(ascending.records().rows().getFirst().account().id(), descending.records().rows().getFirst().account().id());
        }
        q.setSearch("%");assertEquals(0, accounts.chart(q, null).total());
        q.setSearch("_");assertEquals(0, accounts.chart(q, null).total());
        q.setKind("GROUP");q.setSearch("");assertEquals(0, accounts.chart(q, batchFamily).total());
        q.setKind("SQL");assertThrows(IllegalArgumentException.class, () -> accounts.chart(q, null));
    }

    @Test
    void creationUsesActualLevelClassificationAndNormalBalance() {
        assertThrows(IllegalArgumentException.class, () -> create(family, "311900", "GROUP"));
        assertThrows(IllegalArgumentException.class, () -> create(id("300000"), "390001", "POSTING"));
        assertThrows(IllegalArgumentException.class, () -> create(family, "312901", "POSTING"));
        long posting = create(family, "311901", "POSTING");
        var a = accounts.account(posting);assertEquals("EQUITY", a.classification());assertEquals("DEBIT", a.normalBalance());
        assertEquals(4, a.depth());assertThrows(IllegalArgumentException.class, () -> create(posting, "311902", "POSTING"));
        assertThrows(IllegalArgumentException.class, () -> accounts.chart(new CoaQuery(), posting));
        assertThrows(IllegalArgumentException.class, () -> accounts.chart(new CoaQuery(), -1L));
        assertThrows(IllegalArgumentException.class, () -> accounts.chart(new CoaQuery(), Long.MAX_VALUE));
        assertEquals("CREDIT", accounts.account(family).normalBalance());
        assertTrue(accounts.choices("",0,false,"GROUP").items().stream().allMatch(c -> c.depth()<3));
        assertTrue(accounts.choices("",0,false,"POSTING").items().stream().allMatch(c -> c.depth()==3));
        assertTrue(accounts.choices("",0,true).items().stream().allMatch(c -> c.depth()==4));
    }

    @Test
    void suggestionsRemainUniqueUnderConcurrentCreationAndReportExhaustion() throws Exception {
        assertEquals("331001", accounts.nextAccountCode(codeFamily, "POSTING"));
        var pool = Executors.newFixedThreadPool(2);var gate = new CountDownLatch(1);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (int n=0;n<2;n++) outcomes.add(pool.submit(() -> {
                actor(ADMIN);gate.await();
                try {create(codeFamily,"331001","POSTING");return true;}
                catch (DataIntegrityViolationException expected) {return false;}
                finally {SecurityContextHolder.clearContext();}
            }));
            gate.countDown();assertNotEquals(outcomes.get(0).get(20,TimeUnit.SECONDS), outcomes.get(1).get(20,TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("select count(*) from gl_accounts where code='331001'",Integer.class));
            assertEquals("331002",accounts.nextAccountCode(codeFamily,"POSTING"));
        } finally {pool.shutdownNow();}
        db.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                + " select (331000+n)::text,'Exhaustion fixture','EQUITY','CREDIT',4,? from generate_series(2,999) n",codeFamily);
        assertEquals("",accounts.nextAccountCode(codeFamily,"POSTING"));
    }

    @Test
    void databaseRejectsInvalidCodesAndLifecycleStillProtectsActiveChildren() {
        assertThrows(DataIntegrityViolationException.class, () -> db.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                + " values ('399123','Invalid family prefix','EQUITY','CREDIT',4,?)",family));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                + " values ('340000','Invalid group balance','EQUITY','DEBIT',2,?)",id("300000")));
        assertThrows(DataIntegrityViolationException.class, () -> accounts.accountState(batchFamily,accounts.account(batchFamily).version(),false));
        var parent=accounts.chart(new CoaQuery(),id("320000")).records().rows().getFirst();assertFalse(parent.lifecycle());
        long inactive=create(family,"311903","POSTING");accounts.accountState(inactive,0,false);
        var q=new CoaQuery();q.setStatus("INACTIVE");assertEquals(List.of(inactive),accounts.chart(q,family).records().rows().stream().map(r->r.account().id()).toList());
        assertFalse(accounts.choices("311903",0,true).items().stream().anyMatch(c->c.id()==inactive));
    }

    @Test
    void readOnlyActorsCannotCreateOrSuggestAndStaffCannotRead() {
        actor(READER);var rows=accounts.chart(new CoaQuery(),null).records().rows();
        assertTrue(rows.stream().noneMatch(r->r.addGroup()||r.addPosting()||r.editable()||r.lifecycle()));
        assertThrows(AccessDeniedException.class,()->accounts.nextAccountCode(family,"POSTING"));
        assertThrows(AccessDeniedException.class,()->create(family,"311905","POSTING"));
        actor(STAFF);assertThrows(AccessDeniedException.class,()->accounts.chart(new CoaQuery(),null));
    }

    String token(String html) {
        var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);assertTrue(m.find());return m.group(1);
    }
    HttpClient browser(String email) throws Exception {
        var web=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var login=get(web,"/login");
        var response=web.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/login"))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf="+token(login.body())+"&email="+URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8)+"&password="+PASSWORD)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(302,response.statusCode());return web;
    }
    HttpResponse<String> get(HttpClient web,String path) throws Exception {
        return web.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void nativeJspAndFragmentContractsRetainLanguageFiltersAndCsrf() throws Exception {
        var web=browser(ADMIN);
        for(String lang:List.of("en","sw")) {
            var root=get(web,"/accounting/accounts?lang="+lang);assertEquals(200,root.statusCode());
            assertTrue(root.body().contains("data-coa-tree"));assertTrue(root.body().contains("data-table-selection=\"single\""));
            assertTrue(root.body().contains("/accounting/accounts/"+id("100000")));assertFalse(root.body().contains("data-grouped-heading"));
            var fragment=get(web,"/accounting/accounts/children?parentId="+batchFamily+"&size=10&classification=EQUITY&kind=POSTING&lang="+lang);
            assertEquals(200,fragment.statusCode());assertEquals("true",fragment.headers().firstValue("X-COA-Children").orElseThrow());
            assertEquals("no-store",fragment.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(fragment.body().contains("<html"));assertTrue(fragment.body().contains("data-coa-more=\"1\""));
            assertTrue(fragment.body().contains("classification=EQUITY"));assertTrue(fragment.body().contains("kind=POSTING"));
            var form=get(web,"/accounting/accounts/new?parentId="+family+"&mode=POSTING&lang="+lang);
            assertEquals(200,form.statusCode());assertTrue(form.body().contains("parentMode=POSTING"));token(form.body());
            assertTrue(form.body().contains("href=\"/accounting/accounts?parentId="+family+"\""),"Cancel retains the owning group");
        }
        var denied=web.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/accounting/accounts/"+family+"/state"))
                .POST(HttpRequest.BodyPublishers.ofString("version=0&active=false")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,denied.statusCode());
        long cashParent=id("111000");
        var cashForm=get(web,"/accounting/accounts/new?parentId="+cashParent+"&mode=POSTING");
        String input="_csrf="+token(cashForm.body())+"&parentId="+cashParent+"&mode=POSTING&code=111002&name=Retained+cash+fixture&normalBalance=DEBIT&description=Verification";
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/accounting/accounts"))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(input)).build();
        var saved=web.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(302,saved.statusCode());
        assertTrue(saved.headers().firstValue("Location").orElseThrow().startsWith("http://127.0.0.1:"+port+"/accounting/accounts?parentId="+cashParent+"&_notice="));
        assertTrue(CreationFeedback.follow(web,saved).startsWith("/accounting/accounts/"));
        var duplicate=web.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,duplicate.statusCode());
        assertTrue(duplicate.body().contains("Retained cash fixture"));assertEquals(1,db.queryForObject("select count(*) from gl_accounts where code='111002'",Integer.class));
        var reader=browser(READER);assertEquals(403,get(reader,"/accounting/accounts/next-code?parentId="+family).statusCode());
        assertEquals(403,get(reader,"/accounting/accounts").statusCode());
        String readerToken=token(get(reader,"/account").body());
        var readerPost=reader.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/accounting/accounts"))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf="+readerToken+"&name=")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,readerPost.statusCode(),"An invalid payload must not bypass setup authorization");
        var staff=browser(STAFF);assertEquals(403,get(staff,"/accounting/accounts/children?parentId="+family).statusCode());
    }

    @Test
    void migrationReusesCompatibleRecordsAndRollsBackConflictingDefaults() {
        for(String scenario:List.of("missing","compatible","conflict")) {
            String schema="coa_migration_"+UUID.randomUUID().toString().replace("-","");
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("28").load().migrate();
            if(!scenario.equals("missing")) db.update("insert into "+schema+".gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                    + " select '110000','Existing approved name',?,'DEBIT',2,id from "+schema+".gl_accounts where code=?",
                    scenario.equals("conflict")?"EQUITY":"ASSET",scenario.equals("conflict")?"300000":"100000");
            var upgrade=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("29").load();
            if(scenario.equals("conflict")) {
                var failure=assertThrows(org.flywaydb.core.api.FlywayException.class,upgrade::migrate);
                assertTrue(failure.getMessage().contains("V29"));
                assertEquals(6,db.queryForObject("select count(*) from "+schema+".gl_accounts",Integer.class));
                assertEquals(0,db.queryForObject("select count(*) from "+schema+".gl_accounts where code='120000'",Integer.class));
            } else {
                Long before=scenario.equals("compatible")?db.queryForObject("select id from "+schema+".gl_accounts where code='110000'",Long.class):null;
                upgrade.migrate();assertEquals(10,db.queryForObject("select count(*) from "+schema+".gl_accounts",Integer.class));
                assertEquals(0,db.queryForObject("select count(*) from "+schema+".gl_account_reporting",Integer.class));
                if(before!=null) {
                    assertEquals(before,db.queryForObject("select id from "+schema+".gl_accounts where code='110000'",Long.class));
                    assertEquals("Existing approved name",db.queryForObject("select name from "+schema+".gl_accounts where code='110000'",String.class));
                }
            }
        }
    }

    @Test
    void forwardMigrationRepairsPreviouslyMigratedIncompleteChartsWithoutReplacingExistingAccounts() {
        for(String scenario:List.of("missing-assets","missing-groups","compatible","conflict")) {
            String schema="coa_repair_"+UUID.randomUUID().toString().replace("-","");
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("34").load().migrate();
            var isolated=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    System.getenv("FCMS_TEST_DB_URL")+"?currentSchema="+schema,
                    System.getenv("FCMS_DB_USERNAME"),System.getenv("FCMS_DB_PASSWORD")));
            isolated.update("update gl_accounts set name='Retained liability group',description='Existing description',version=7 where code='210000'");
            isolated.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                    + " select '211000','Retained family','LIABILITY','CREDIT',3,id from gl_accounts where code='210000'");
            isolated.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                    + " select '211001','Retained contra posting','LIABILITY','DEBIT',4,id from gl_accounts where code='211000'");
            // Reproduce post-V29 maintenance damage only in this isolated fixture schema.
            if(!scenario.equals("compatible")) {
                isolated.execute("alter table gl_accounts disable trigger gl_account_guard");
                isolated.update("delete from gl_accounts where code='111000'");
                isolated.update("delete from gl_accounts where code in ('110000','120000')");
                if(scenario.equals("missing-groups")) isolated.update("delete from gl_accounts where code='220000'");
                isolated.execute("alter table gl_accounts enable trigger gl_account_guard");
            }
            if(scenario.equals("conflict")) {
                isolated.execute("alter table gl_accounts disable trigger gl_account_code_guard");
                isolated.update("insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)"
                        + " select '120000','Conflicting account','EQUITY','CREDIT',2,id from gl_accounts where code='300000'");
                isolated.execute("alter table gl_accounts enable trigger gl_account_code_guard");
            }
            var before=isolated.queryForList("select id,to_jsonb(a)::text as payload from gl_accounts a order by id");
            var upgrade=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("35").load();
            if(scenario.equals("conflict")) {
                assertThrows(org.flywaydb.core.api.FlywayException.class,upgrade::migrate);
                assertEquals(before,isolated.queryForList("select id,to_jsonb(a)::text as payload from gl_accounts a order by id"));
                assertEquals(0,isolated.queryForObject("select count(*) from gl_accounts where code='110000'",Integer.class),"Earlier insert rolls back with the conflict");
            } else {
                assertEquals(1,upgrade.migrate().migrationsExecuted);
                assertEquals(12,isolated.queryForObject("select count(*) from gl_accounts",Integer.class));
                assertEquals(before,isolated.queryForList("select id,to_jsonb(a)::text as payload from gl_accounts a where id in ("
                        +before.stream().map(r->r.get("id").toString()).collect(java.util.stream.Collectors.joining(","))+") order by id"));
                assertEquals(List.of("110000:100000:2","111000:110000:3","120000:100000:2"),isolated.queryForList(
                        "select a.code||':'||p.code||':'||a.depth from gl_accounts a join gl_accounts p on p.id=a.parent_id"
                        +" where a.code in ('110000','111000','120000') and a.active and not a.cash_account order by a.code",String.class));
                assertEquals(0,isolated.queryForObject("select count(*) from gl_account_reporting",Integer.class),"Repair does not approve reporting definitions");
                assertEquals(0,upgrade.migrate().migrationsExecuted,"Already repaired charts are unchanged on restart");
            }
        }
    }
}
