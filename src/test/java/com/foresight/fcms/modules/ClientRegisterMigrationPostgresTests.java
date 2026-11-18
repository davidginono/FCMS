package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;

@EnabledIfEnvironmentVariable(
        named = "FCMS_TEST_DB_URL",
        matches = "jdbc:postgresql://127.0.0.1:5432/fcms_codex_verify_.*")
class ClientRegisterMigrationPostgresTests {
    @Test
    void upgradeRetainsEveryRowAndAmountAndReconcilesOnlyLifecycleStatus() {
        String schema = "client_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source =
                new DriverManagerDataSource(
                        System.getenv("FCMS_TEST_DB_URL") + "?currentSchema=" + schema,
                        System.getenv("FCMS_DB_USERNAME"),
                        System.getenv("FCMS_DB_PASSWORD"));
        Flyway.configure()
                .dataSource(source)
                .schemas(schema)
                .defaultSchema(schema)
                .target("35")
                .load()
                .migrate();
        var db = new JdbcTemplate(source);
        for (String table :
                List.of("saccos", "amcos", "unions", "microfinance_clients", "other_clients"))
            db.update(
                    "insert into "
                            + table
                            + "("
                            + (table.equals("saccos") ? "saccos_name" : "client_name")
                            + ",region,financial_year_id,client_status_id,annual_fee,amount,programmer_cost,version)"
                            + " values('Retained client','Test',1,2,125.55,99.99,7.11,5)");
        db.update(
                "update saccos set"
                    + " operational_status='DORMANT',date_paid='2026-11-01',ess_fee=1.23");
        db.update("update amcos set withholding_tax=3.45,commission=6.78");
        var before = snapshot(db, schema);
        Flyway.configure()
                .dataSource(source)
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();
        assertEquals(before, snapshot(db, schema));
        assertEquals(
                "INACTIVE",
                db.queryForObject("select operational_status from saccos", String.class));
        for (String table : List.of("amcos", "unions", "microfinance_clients"))
            assertEquals(
                    "ACTIVE",
                    db.queryForObject("select operational_status from " + table, String.class));
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("update amcos set operational_status='DORMANT'"));
        assertEquals(
                36,
                db.queryForObject(
                        "select max(version::int) from flyway_schema_history where success",
                        Integer.class));
    }

    Map<String, String> snapshot(JdbcTemplate db, String schema) {
        var result = new TreeMap<String, String>();
        for (String table :
                db.queryForList(
                        "select tablename from pg_tables where schemaname=? and"
                            + " tablename<>'flyway_schema_history' order by tablename",
                        String.class,
                        schema)) {
            assertTrue(table.matches("[a-z_]+"));
            result.put(
                    table,
                    db.queryForObject(
                            "select"
                                + " count(*)||':'||md5(coalesce(string_agg((to_jsonb(t)-'operational_status')::text,E'\\n"
                                + "' order by (to_jsonb(t)-'operational_status')::text),'')) from "
                                    + table
                                    + " t",
                            String.class));
        }
        return result;
    }
}
