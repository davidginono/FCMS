package com.foresight.fcms.modules;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Explicit legacy capabilities for workflow fixtures, independent of the production role defaults.
 */
final class OperationalTestClaims {
    private OperationalTestClaims() {}

    static void enable(JdbcTemplate db) {
        // Historical-source fixtures exercise retained accounting adapters, not assignable roles.
        // V33 intentionally removes these from every real database's permission catalog.
        if (db.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class) >= 33)
            db.update("insert into claims(name) values ('expense.read'),('expense.write'),"
                    + "('expense.approve'),('expense.assign'),('expense.evidence.write'),"
                    + "('expense.evidence.review'),('finance.approve'),('report.read') on conflict do nothing");
        db.update(
                "insert into role_claims(role_id,claim) select 1,name from claims where name not in"
                    + " ('workspace.staff','workspace.director') on conflict do nothing");
        db.update(
                "insert into role_claims(role_id,claim) select 2,name from claims where name in"
                    + " ('task.read','task.write','expense.read','expense.write') on conflict do"
                    + " nothing");
    }
}
