package com.foresight.fcms.accounting.service;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class SourceCoverageService {
    private final JdbcTemplate db;
    private final AccessService access;

    public SourceCoverageService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Coverage through(LocalDate date) {
        access.require("accounting.read");
        if (date == null) throw new IllegalArgumentException("error.dates");
        return db.queryForObject(
                "select count(*) filter(where coverage='POSTED'),count(*) filter(where"
                    + " coverage='OPENING_COVERED'),count(*) filter(where"
                    + " coverage='UNVERIFIED'),count(*) filter(where coverage in"
                    + " ('UNPOSTED','BOOKS_PENDING')),coalesce(sum(amount) filter(where coverage"
                    + " not in ('POSTED','OPENING_COVERED')),0),exists(select 1 from gl_books where"
                    + " state='APPROVED' and (opening_mode='ZERO' or exists(select 1 from"
                    + " gl_journals where kind='OPENING' and state='POSTED'))) from"
                    + " gl_source_coverage where source_date is null or source_date<=?",
                (r, n) ->
                        new Coverage(
                                date,
                                r.getLong(1),
                                r.getLong(2),
                                r.getLong(3),
                                r.getLong(4),
                                r.getBigDecimal(5),
                                r.getBoolean(6)),
                date);
    }

    public record Coverage(
            LocalDate through,
            long posted,
            long openingCovered,
            long unverified,
            long unposted,
            BigDecimal unresolvedAmount,
            boolean booksReady) {
        public boolean complete() {
            return booksReady && unverified == 0 && unposted == 0;
        }
    }
}
