package com.foresight.fcms.client.service;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ClientRevenueService {
    private final JdbcTemplate db;
    private final AccessService access;

    public ClientRevenueService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Revenue> summary(int year) {
        access.require("report.read");
        List<Revenue> results = new ArrayList<>();
        for (String table :
                List.of("saccos", "amcos", "unions", "microfinance_clients", "other_clients")) {
            boolean full = Set.of("saccos", "amcos", "unions").contains(table);
            String revenue =
                    "amount+system_install_fee+training_fee+android_fee+system_reinstall_fee"
                            + (full ? "+hosting_fee+bulk_sms_fee" : "")
                            + (table.equals("saccos") ? "+cloud_backup_fee+ess_fee" : "");
            String cost =
                    "programmer_cost"
                            + (full ? "+commission" : "")
                            + (table.equals("saccos") ? "+android_cost+cloud_backup_cost" : "");
            results.add(
                    db.queryForObject(
                            "select count(*),coalesce(sum("
                                    + revenue
                                    + "),0),coalesce(sum("
                                    + cost
                                    + "),0) from "
                                    + table
                                    + " where financial_year_id in (select id from financial_year"
                                    + " where fyear=?)",
                            (r, n) ->
                                    new Revenue(
                                            table,
                                            r.getLong(1),
                                            r.getBigDecimal(2),
                                            r.getBigDecimal(3)),
                            String.valueOf(year)));
        }
        return List.copyOf(results);
    }

    @Transactional(readOnly = true)
    public List<Portfolio> portfolios() {
        access.require("client.read");
        List<Portfolio> r = new ArrayList<>();
        for (String t :
                List.of("saccos", "amcos", "unions", "microfinance_clients", "other_clients"))
            r.add(
                    db.queryForObject(
                            "select count(*),count(*) filter(where client_status_id=1),count(*)"
                                    + " filter(where client_status_id=2) from "
                                    + t
                                    + " where visible",
                            (s, n) -> new Portfolio(t, s.getLong(1), s.getLong(2), s.getLong(3))));
        return r;
    }

    public record Revenue(String category, long clients, BigDecimal received, BigDecimal costs) {
        public BigDecimal profit() {
            return received.subtract(costs);
        }
    }

    public record Portfolio(String category, long clients, long paid, long unpaid) {}
}
