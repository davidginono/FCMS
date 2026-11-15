package com.foresight.fcms.reference.service;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LegacyYearFilterService {
    private final JdbcTemplate db;
    private final AccessService access;

    public LegacyYearFilterService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolve(String year) {
        access.require("client.read");
        if (year == null || !year.matches("[0-9]{4}")) return Optional.empty();
        return db
                .queryForList("select id from financial_year where fyear=?", Long.class, year)
                .stream()
                .findFirst();
    }
}
