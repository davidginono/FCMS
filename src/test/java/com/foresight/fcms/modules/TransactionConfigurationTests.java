package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.finance.ledger.dto.LedgerForm;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.RegisterQuery;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TransactionConfigurationTests.UpgradeFixture.class)
class TransactionConfigurationTests {
    static final String SCHEMA =
            "transaction_config_" + UUID.randomUUID().toString().replace("-", "");
    static final LocalDate TODAY = LocalDate.now();
    static long bank, income, oldMapping, oldJournal, oldSource;
    static String mappingSnapshot, journalSnapshot, linkSnapshot, linesSnapshot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () -> System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + SCHEMA);
        p.add("spring.flyway.default-schema", () -> SCHEMA);
        p.add("spring.flyway.schemas", () -> SCHEMA);
        p.add("fcms.backup.interval", () -> "3600000");
        p.add("fcms.mail.interval", () -> "3600000");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UpgradeFixture {
        @Bean
        FlywayMigrationStrategy retainedConfiguration() {
            return flyway -> {
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .target("20")
                        .load()
                        .migrate();
                var db = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                for (int i = 2; i <= 5; i++)
                    db.update(
                            "insert into"
                                + " users(id,first_name,last_name,email,password,status_id,role_id)"
                                + " select ?,'Config','Fixture',?,password,1,1 from users where"
                                + " id=1",
                            i,
                            "config" + i + "@test.invalid");
                db.queryForObject(
                        "select setval(pg_get_serial_sequence('users','id'),5)", Long.class);
                bank = account(db, "110000", "Bank group", "ASSET", 2, "100000", false);
                bank = account(db, "111000", "Bank subgroup", "ASSET", 3, "110000", false);
                bank = account(db, "111001", "Synthetic bank", "ASSET", 4, "111000", true);
                account(db, "510000", "Income group", "INCOME", 2, "500000", false);
                account(db, "511000", "Income subgroup", "INCOME", 3, "510000", false);
                income = account(db, "511001", "Synthetic income", "INCOME", 4, "511000", false);
                oldMapping =
                        db.queryForObject(
                                "insert into"
                                    + " gl_posting_mappings(source_event,debit_account_id,credit_account_id,voucher_kind,cash_flow,policy_reference,maker_id,request_key,payload_hash)"
                                    + " values ('INCOME_IN',?,?,'RECEIPT','OPERATING','Retained"
                                    + " reviewed treatment',1,?,?) returning id",
                                Long.class,
                                bank,
                                income,
                                UUID.randomUUID(),
                                "a".repeat(64));
                db.update(
                        "update gl_posting_mappings set"
                            + " state='APPROVED',checker_id=2,review_reason='Independent historical"
                            + " approval',reviewed_at=current_timestamp,version=1 where id=?",
                        oldMapping);
                db.update(
                        "insert into"
                            + " gl_books(id,opening_date,policy_reference,opening_mode,state,maker_id,checker_id,approved_at)"
                            + " values (true,?,'Synthetic"
                            + " cutover','ZERO','APPROVED',1,2,current_timestamp)",
                        TODAY.withDayOfYear(1));
                db.update(
                        "insert into finance_gl_cutover(id,starts_on,policy_reference,approved_by)"
                                + " values (true,?,'Synthetic cutover',2)",
                        TODAY.withDayOfYear(1));
                long period =
                        db.queryForObject(
                                "insert into gl_periods(name,starts_on,ends_on,created_by) values"
                                        + " ('Synthetic year',?,?,1) returning id",
                                Long.class,
                                TODAY.withDayOfYear(1),
                                TODAY.withDayOfYear(1).plusYears(1).minusDays(1));
                oldSource =
                        db.queryForObject(
                                "insert into"
                                    + " ledger_entries(kind,transaction_date,description,category,reference,payment_mode,amount_delta,units_delta,actor_id,request_key,payload_hash,balance_after,units_after)"
                                    + " values ('INCOME',?,'Retained"
                                    + " source','Other','MIGRATION-1','Bank',10.25,0,4,?,?,10.25,0)"
                                    + " returning id",
                                Long.class,
                                TODAY,
                                UUID.randomUUID(),
                                "b".repeat(64));
                db.update(
                        "update ledger_accounts set balance=10.25,version=version+1 where"
                                + " kind='INCOME'");
                oldJournal =
                        db.queryForObject(
                                "insert into"
                                    + " gl_journals(kind,reference,description,evidence_reference,effective_date,period_id,cash_flow,state,maker_id,request_key,payload_hash)"
                                    + " values ('RECEIPT','MIGRATION-1','Retained"
                                    + " source','Synthetic evidence',?,?,'OPERATING','DRAFT',3,?,?)"
                                    + " returning id",
                                Long.class,
                                TODAY,
                                period,
                                UUID.randomUUID(),
                                "c".repeat(64));
                db.update(
                        "insert into"
                            + " gl_lines(journal_id,line_number,account_id,account_code,account_name,debit,credit)"
                            + " values (?,1,?,'111001','Synthetic"
                            + " bank',10.25,0),(?,2,?,'511001','Synthetic income',0,10.25)",
                        oldJournal,
                        bank,
                        oldJournal,
                        income);
                db.update(
                        "insert into"
                            + " gl_source_links(source_kind,source_id,journal_id,evidence_reference,linked_by,mapping_id,source_date,signed_amount)"
                            + " values ('LEDGER_INCOME',?,?,'Synthetic evidence',3,?,?,10.25)",
                        oldSource,
                        oldJournal,
                        oldMapping,
                        TODAY);
                db.update(
                        "insert into gl_source_reservations values ('LEDGER_INCOME',?,?)",
                        oldSource,
                        oldJournal);
                db.update(
                        "update gl_journals set state='SUBMITTED',version=1 where id=?",
                        oldJournal);
                db.update(
                        "update gl_journals set"
                            + " state='POSTED',checker_id=2,review_comment='Independent retained"
                            + " source review',posted_at=current_timestamp,version=2 where id=?",
                        oldJournal);
                mappingSnapshot = json(db, "gl_posting_mappings", oldMapping);
                journalSnapshot = json(db, "gl_journals", oldJournal);
                linkSnapshot =
                        db.queryForObject(
                                "select to_jsonb(l)::text from gl_source_links l where"
                                        + " journal_id=?",
                                String.class,
                                oldJournal);
                linesSnapshot =
                        db.queryForObject(
                                "select jsonb_agg(to_jsonb(l) order by line_number)::text from"
                                        + " gl_lines l where journal_id=?",
                                String.class,
                                oldJournal);
                flyway.migrate();
            };
        }

        static long account(
                JdbcTemplate db,
                String code,
                String name,
                String classification,
                int depth,
                String parent,
                boolean cash) {
            return db.queryForObject(
                    "insert into"
                        + " gl_accounts(code,name,classification,normal_balance,depth,parent_id,cash_account,created_by)"
                        + " select ?,?,?,?, ?,id,?,1 from gl_accounts where code=? returning id",
                    Long.class,
                    code,
                    name,
                    classification,
                    classification.equals("ASSET") ? "DEBIT" : "CREDIT",
                    depth,
                    cash,
                    parent);
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired ActivityTransactionService config;
    @Autowired PostingMappingService templates;
    @Autowired SourcePostingService sources;
    @Autowired JournalService journals;
    @Autowired LedgerService ledger;

    static String json(JdbcTemplate db, String table, long id) {
        return db.queryForObject(
                "select to_jsonb(t)::text from " + table + " t where id=?", String.class, id);
    }

    void actor(int id) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                id == 1 ? "admin@fcms.local" : "config" + id + "@test.invalid",
                                "",
                                List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    long activity() {
        return db.queryForObject("select id from gl_activities where code='INCOME'", Long.class);
    }

    TransactionConfigForm transaction(String code) {
        var f = new TransactionConfigForm();
        f.setActivityId(activity());
        f.setCode(code);
        f.setName("Synthetic " + code);
        f.setEvent("INCOME_IN");
        f.setDebitAccountId(bank);
        f.setCreditAccountId(income);
        f.setVoucherKind("RECEIPT");
        f.setCashFlow("OPERATING");
        f.setPolicyReference("Reviewed synthetic treatment");
        return f;
    }

    TransactionTemplateForm edit(long id, String policy) {
        var f = new TransactionTemplateForm();
        f.setVersion(config.transaction(id).version());
        f.setDebitAccountId(bank);
        f.setCreditAccountId(income);
        f.setVoucherKind("RECEIPT");
        f.setCashFlow("OPERATING");
        f.setPolicyReference(policy);
        return f;
    }

    long proposal(long id) {
        return templates.latest(id).orElseThrow().id();
    }

    void approve(long id, long mapping) {
        actor(2);
        config.reviewTemplate(
                id,
                mapping,
                templates.find(mapping).version(),
                config.transaction(id).version(),
                "approve",
                "Independent synthetic review");
    }

    @Test
    @Order(1)
    void upgradeRetainsEveryHistoricalFinancialPayload() {
        actor(1);
        assertEquals(
                mappingSnapshot,
                db.queryForObject(
                        "select (to_jsonb(m)-'transaction_id')::text from gl_posting_mappings m"
                                + " where id=?",
                        String.class,
                        oldMapping));
        assertEquals(journalSnapshot, json(db, "gl_journals", oldJournal));
        assertEquals(
                linkSnapshot,
                db.queryForObject(
                        "select to_jsonb(l)::text from gl_source_links l where journal_id=?",
                        String.class,
                        oldJournal));
        assertEquals(
                linesSnapshot,
                db.queryForObject(
                        "select jsonb_agg(to_jsonb(l) order by line_number)::text from gl_lines l"
                                + " where journal_id=?",
                        String.class,
                        oldJournal));
        assertEquals(
                "INCOME_IN", config.transaction(templates.find(oldMapping).transactionId()).code());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_posting_mappings set transaction_id=(select id from"
                                    + " gl_transactions where code='PETTY_IN'),version=version+1"
                                    + " where id=?",
                                oldMapping));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> db.update("delete from gl_transactions where code='INCOME_IN'"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        db.update(
                                "update gl_transactions set"
                                        + " source_event='PETTY_IN',version=version+1 where"
                                        + " code='INCOME_IN'"));
    }

    @Test
    @Order(2)
    void metadataIsIdempotentBoundedAndVersioned() {
        actor(1);
        var f = new ActivityForm();
        f.setCode("VERIFIED");
        f.setName("Verified activity");
        long id = config.createActivity(f);
        assertEquals(id, config.createActivity(f));
        f.setName("Conflicting retry");
        assertThrows(IllegalArgumentException.class, () -> config.createActivity(f));
        f.setName("Edited activity");
        f.setVersion(0);
        config.editActivity(id, f);
        assertEquals("Edited activity", config.activity(id).name());
        assertThrows(OptimisticLockingFailureException.class, () -> config.editActivity(id, f));
        for (int n = 0; n < 31; n++) {
            var a = new ActivityForm();
            a.setCode("PAGE" + String.format("%02d", n));
            a.setName("Pagination fixture");
            config.createActivity(a);
        }
        var q = new RegisterQuery();
        q.setSearch("Pagination fixture");
        q.setSize(10);
        q.setSort("code");
        q.setDirection("asc");
        var page = config.activities(q);
        assertEquals(10, page.rows().size());
        assertTrue(page.hasNext());
        assertEquals("PAGE00", page.rows().getFirst().code());
        q.setPage(3);
        assertEquals(1, config.activities(q).rows().size());
        config.activityState(id, 1, false, "Synthetic retirement");
        assertFalse(config.activity(id).active());
    }

    @Test
    @Order(3)
    void multipleTemplatesRequireAnExplicitChoiceAndRetainExactReversals() {
        actor(1);
        long tx = config.createTransaction(transaction("SECOND-INCOME"));
        long mapping = proposal(tx);
        approve(tx, mapping);
        actor(4);
        var f = new LedgerForm();
        f.setDate(TODAY);
        f.setAmount(new BigDecimal("2.35"));
        f.setDescription("Multiple transaction fixture");
        f.setCategory("Other");
        f.setReference("CHOICE");
        f.setPaymentMode("BANK");
        long source = ledger.post(LedgerKind.INCOME, f);
        actor(3);
        var item = sources.find("LEDGER_INCOME", source);
        assertEquals("READY", item.coverage());
        assertNull(item.mappingId());
        var p = new SourcePreparationForm();
        p.setEvidenceReference("Explicit template evidence");
        assertThrows(
                IllegalArgumentException.class, () -> sources.prepare("LEDGER_INCOME", source, p));
        p.setMappingId(mapping);
        long journal = sources.prepare("LEDGER_INCOME", source, p);
        assertEquals(
                mapping,
                db.queryForObject(
                        "select mapping_id from gl_source_links where journal_id=?",
                        Long.class,
                        journal));
        journals.transition(journal, 0, "submit", "");
        actor(2);
        journals.transition(journal, 1, "post", "Independent source approval");
        actor(1);
        long replacement = config.saveTemplate(tx, edit(tx, "Updated synthetic treatment"));
        assertEquals(mapping, templates.currentForTransaction(tx).orElseThrow().id());
        approve(tx, replacement);
        assertEquals("RETIRED", templates.find(mapping).state());
        assertEquals("APPROVED", templates.find(oldMapping).state());
        actor(1);
        config.transactionState(
                tx, config.transaction(tx).version(), false, "Synthetic retirement");
        assertThrows(
                IllegalArgumentException.class,
                () -> templates.forPreparation(replacement, "INCOME_IN"));
        actor(5);
        long reversed =
                ledger.reverse(
                        LedgerKind.INCOME, source, UUID.randomUUID(), "Exact synthetic correction");
        var rp = new SourcePreparationForm();
        rp.setMappingId(mapping);
        rp.setEvidenceReference("Retained exact reversal evidence");
        long reversal = sources.prepare("LEDGER_INCOME", reversed, rp);
        assertEquals(
                mapping,
                db.queryForObject(
                        "select mapping_id from gl_source_links where journal_id=?",
                        Long.class,
                        reversal));
        assertEquals(journal, journals.find(reversal).reversesId());
        journals.transition(reversal, 0, "submit", "");
        actor(1);
        journals.transition(reversal, 1, "post", "Independent exact reversal review");
        assertEquals("POSTED", journals.find(reversal).state());
        assertEquals(journalSnapshot, json(db, "gl_journals", oldJournal));
    }

    @Test
    @Order(4)
    void concurrentEditsAndReviewsCannotOverwriteEachOther() throws Exception {
        actor(1);
        long tx = config.createTransaction(transaction("CONCURRENT"));
        long first = proposal(tx);
        approve(tx, first);
        actor(1);
        var a = edit(tx, "First concurrent edit");
        var b = edit(tx, "Second concurrent edit");
        var results =
                race(
                        () -> {
                            actor(1);
                            return config.saveTemplate(tx, a);
                        },
                        () -> {
                            actor(1);
                            return config.saveTemplate(tx, b);
                        });
        assertEquals(1, results.stream().filter(Long.class::isInstance).count());
        assertEquals(
                1,
                results.stream()
                        .filter(OptimisticLockingFailureException.class::isInstance)
                        .count());
        long pending = proposal(tx);
        var accepted =
                templates.find(pending).policyReference().equals(a.getPolicyReference()) ? a : b;
        assertEquals(pending, config.saveTemplate(tx, accepted));
        accepted.setPolicyReference("Changed replay");
        assertThrows(IllegalArgumentException.class, () -> config.saveTemplate(tx, accepted));
        long version = config.transaction(tx).version();
        results =
                race(
                        () -> {
                            actor(2);
                            config.reviewTemplate(
                                    tx, pending, 0, version, "approve", "Concurrent review A");
                            return pending;
                        },
                        () -> {
                            actor(3);
                            config.reviewTemplate(
                                    tx, pending, 0, version, "approve", "Concurrent review B");
                            return pending;
                        });
        assertEquals(1, results.stream().filter(Long.class::isInstance).count());
        assertEquals(
                1,
                results.stream()
                        .filter(OptimisticLockingFailureException.class::isInstance)
                        .count());
        assertEquals(pending, templates.currentForTransaction(tx).orElseThrow().id());
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from gl_posting_mappings where transaction_id=? and"
                                + " state='APPROVED'",
                        Integer.class,
                        tx));
    }

    @Test
    @Order(5)
    void claimsAndInactiveParentsAreEnforcedAtTheServiceBoundary() {
        actor(1);
        long tx = config.createTransaction(transaction("PARENT-GUARD")), mapping = proposal(tx);
        long parent = activity();
        config.activityState(
                parent, config.activity(parent).version(), false, "Synthetic parent suspension");
        try {
            actor(2);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            config.reviewTemplate(
                                    tx,
                                    mapping,
                                    0,
                                    config.transaction(tx).version(),
                                    "approve",
                                    "Inactive review"));
            actor(1);
            config.reviewTemplate(
                    tx,
                    mapping,
                    0,
                    config.transaction(tx).version(),
                    "cancel",
                    "Retain cancelled inactive proposal");
            assertEquals("CANCELLED", templates.find(mapping).state());
            assertTrue(templates.choices("INCOME_IN", "", 0).items().isEmpty());
        } finally {
            actor(1);
            config.activityState(
                    parent,
                    config.activity(parent).version(),
                    true,
                    "Synthetic parent reinstatement");
        }
        db.update(
                "insert into users(id,first_name,last_name,email,password,status_id,role_id) select"
                    + " 6,'Config','Reader','config6@test.invalid',password,1,2 from users where"
                    + " id=1");
        actor(6);
        assertThrows(
                AccessDeniedException.class, () -> config.transactions(new RegisterQuery(), null));
        db.update("insert into user_claims(user_id,claim) values (6,'accounting.read')");
        assertFalse(config.transactions(new RegisterQuery(), null).rows().isEmpty());
        var activity = new ActivityForm();
        activity.setCode("DENIED");
        activity.setName("Forbidden activity");
        assertThrows(AccessDeniedException.class, () -> config.createActivity(activity));
        assertThrows(
                AccessDeniedException.class,
                () -> config.reviewTemplate(tx, mapping, 1, 0, "approve", "Forbidden review"));
        actor(2);
        long other = config.transaction(templates.find(oldMapping).transactionId()).id();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        config.reviewTemplate(
                                other,
                                mapping,
                                1,
                                config.transaction(other).version(),
                                "approve",
                                "Wrong transaction association"));
    }

    List<Object> race(Callable<Long> a, Callable<Long> b) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = new ArrayList<Future<Object>>();
            for (var c : List.of(a, b))
                calls.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    try {
                                        return c.call();
                                    } catch (Exception e) {
                                        return e;
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    calls.get(0).get(20, TimeUnit.SECONDS), calls.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
