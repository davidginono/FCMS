package com.foresight.fcms.client.service;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class CloudBillingService {
    private final JdbcTemplate db;
    private final AccessService access;

    public CloudBillingService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public RegisterPage<Billing> list(int year, RegisterQuery q) {
        return RegisterPage.of(rows(year, q, q.getSize() + 1, q.offset()), q);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public java.util.List<Billing> report(int year, RegisterQuery query) {
        var rows = rows(year, query, 1001, 0);
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return java.util.List.copyOf(rows);
    }

    private java.util.List<Billing> rows(int year, RegisterQuery q, int limit, long offset) {
        access.require("finance.read");
        access.require("client.read");
        q.validate();
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("error.invalid");
        return db.query(
                "select s.id,s.saccos_name,s.cloud_backup_fee,s.cloud_backup_cost,case when"
                        + " s.cloud_backup_fee>0 then 'PAID' else 'UNPAID' end as status from"
                        + " saccos s join financial_year y on y.id=s.financial_year_id where"
                        + " s.visible and y.fyear=? and lower(s.saccos_name) like ? escape '!'"
                        + " and (?='' or (?='PAID' and s.cloud_backup_fee>0) or (?='UNPAID' and"
                        + " s.cloud_backup_fee=0)) order by "
                        + q.order(
                                Map.of(
                                        "id",
                                        "s.id",
                                        "name",
                                        "s.saccos_name",
                                        "fee",
                                        "s.cloud_backup_fee",
                                        "cost",
                                        "s.cloud_backup_cost",
                                        "status",
                                        "status"))
                        + " limit ? offset ?",
                (r, n) ->
                        new Billing(
                                r.getLong(1),
                                r.getString(2),
                                r.getBigDecimal(3),
                                r.getBigDecimal(4),
                                r.getString(5)),
                Integer.toString(year),
                q.pattern(),
                q.getStatus(),
                q.getStatus(),
                q.getStatus(),
                limit,
                offset);
    }

    public record Billing(long id, String name, BigDecimal fee, BigDecimal cost, String status) {}
}
