package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.EquityReporting.Setup;
import com.foresight.fcms.auth.service.AccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

/** Reads retained approved metadata for financial statements; management is retired. */
@Service
public class EquityReportingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final JsonMapper json = JsonMapper.builder().build();

    public EquityReportingService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Setup effective() {
        access.require("accounting.report");
        return db.query("select payload from gl_equity_reporting where kind='SETUP' and target_id=0 and state='APPROVED'",
                (r, n) -> json.readValue(r.getString(1), Setup.class))
                .stream().findFirst().orElseGet(Setup::empty);
    }

    @Transactional(readOnly = true)
    public List<Long> effectiveRevisionIds(LocalDate from, LocalDate to) {
        access.require("accounting.report");
        return db.queryForList(
                "select r.id from gl_equity_reporting r where r.state='APPROVED' and"
                    + " (r.kind='SETUP' or exists(select 1 from gl_journals j where"
                    + " coalesce(j.reverses_id,j.id)=r.target_id and j.state='POSTED' and"
                    + " j.effective_date between ? and ?)) order by r.id",
                Long.class, from, to);
    }
}
