package com.foresight.fcms.modules;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FCMS_TEST_DB_URL", matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
class RetiredWorkspaceMigrationPostgresTests {
    @Test
    void upgradeRemovesRetiredAssignmentsAndPreservesEveryOtherTableIncludingHistoricalMoneyAndAudit() {
        String schema = "retirement_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + schema,
                System.getenv("FCMS_DB_USERNAME"), System.getenv("FCMS_DB_PASSWORD"));
        var db = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("32").load().migrate();
        long user = db.queryForObject("select min(id) from users", Long.class);
        long role = db.queryForObject("select role_id from users where id=?", Long.class, user);
        db.update("insert into user_claims values (?,'expense.read')", user);
        db.update("insert into user_claim_denials values (?,'expense.evidence.write')", user);
        db.update("insert into role_claims values (?,'expense.approve') on conflict do nothing", role);
        db.update("insert into expenses(user_id,category,description,amount,financial_year,status_id) values (?,'Historical','Retained source',123.45,'2026',(select min(id) from expense_status))", user);
        db.update("insert into audit_events(actor_id,action,resource,resource_id,detail,correlation_id) values (?,'historical.test','expenses',1,'Retained audit','retirement-test')", user);
        db.update("insert into user_claims values (?,'accounting.period.close')", user);
        db.update("insert into user_claim_denials values (?,'accounting.period.close')", user);
        db.update("insert into gl_books(id,opening_date,policy_reference,opening_mode,state,maker_id) values(true,'2026-01-01','Retained history','ZERO','PENDING',?)", user);
        db.update("insert into gl_periods(name,starts_on,ends_on,created_by) values('Retained year','2026-01-01','2026-12-31',?)", user);
        Map<String,String> before = snapshot(db, schema);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        assertEquals(before, snapshot(db, schema));
        for (String table : new String[]{"claims", "role_claims", "user_claims", "user_claim_denials"}) {
            String column = table.equals("claims") ? "name" : "claim";
            assertEquals(0, db.queryForObject("select count(*) from " + table + " where " + column
                    + " like 'expense.%' or " + column + " in ('finance.approve','report.read')", Integer.class), table);
        }
        assertEquals(1, db.queryForObject("select count(*) from role_claims where role_id=? and claim='iam.user.manage'", Integer.class, role));
        for (String table : new String[]{"claims", "role_claims", "user_claims", "user_claim_denials"}) {
            assertEquals(0, db.queryForObject("select count(*) from " + table + " where "
                    + (table.equals("claims") ? "name" : "claim") + "='accounting.period.close'", Integer.class));
        }
        assertEquals(34, db.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class));
    }

    private Map<String,String> snapshot(JdbcTemplate db, String schema) {
        var result = new TreeMap<String,String>();
        for (String table : db.queryForList("select tablename from pg_tables where schemaname=? and tablename not in ('flyway_schema_history','claims','role_claims','user_claims','user_claim_denials') order by tablename", String.class, schema)) {
            assertTrue(table.matches("[a-z_]+"));
            result.put(table, db.queryForObject("select count(*)||':'||md5(coalesce(string_agg(row_to_json(t)::text,E'\\n' order by row_to_json(t)::text),'')) from " + table + " t", String.class));
        }
        return result;
    }
}
