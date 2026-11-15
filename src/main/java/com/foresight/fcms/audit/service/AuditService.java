package com.foresight.fcms.audit.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate db;

    public AuditService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long actor, String action, String resource, Long id, String detail) {
        String correlation = org.slf4j.MDC.get("requestId");
        if (correlation == null || !correlation.matches("[a-zA-Z0-9-]{1,64}"))
            correlation = java.util.UUID.randomUUID().toString();
        db.update(
                "insert into"
                    + " audit_events(actor_id,action,resource,resource_id,detail,correlation_id)"
                    + " values (?,?,?,?,?,?)",
                actor,
                action,
                resource,
                id,
                detail,
                correlation);
    }
}
