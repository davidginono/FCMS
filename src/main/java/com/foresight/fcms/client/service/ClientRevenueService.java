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
                            + (Set.of("saccos", "unions").contains(table) ? "+commission" : "")
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

    /** Selected-year visible client facts; the dashboard never reads client tables itself. */
    @Transactional(readOnly = true)
    public List<Portfolio> portfolios(long financialYearId) {
        access.require("client.read");
        var results = new ArrayList<Portfolio>();
        for (String table :
                List.of("saccos", "amcos", "unions", "microfinance_clients", "other_clients")) {
            boolean full = Set.of("saccos", "amcos", "unions").contains(table);
            boolean saccos = table.equals("saccos");
            String sql =
                    "select count(*),count(*) filter(where client_status_id=1),count(*)"
                            + " filter(where client_status_id=2),count(*) filter(where"
                            + " client_status_id=3),"
                            + "coalesce(sum(annual_fee),0),coalesce(sum(amount),0),"
                            + (Set.of("saccos", "unions").contains(table) ? "coalesce(sum(withholding_tax),0)," : "0,")
                            + "coalesce(sum(training_fee),0),coalesce(sum(system_install_fee),0),"
                            + "coalesce(sum(android_fee),0),coalesce(sum(system_reinstall_fee),0),"
                            + (full
                                    ? "coalesce(sum(hosting_fee),0),coalesce(sum(bulk_sms_fee),0),"
                                    : "0,0,")
                            + (saccos
                                    ? "count(*) filter(where"
                                          + " cloud_backup_fee>0),coalesce(sum(cloud_backup_fee),0),coalesce(sum(cloud_backup_cost),0)"
                                    : "0,0,0")
                            + " from "
                            + table
                            + (table.equals("other_clients") ? " where visible" : " where visible and financial_year_id=?");
            results.add(
                    db.queryForObject(
                            sql,
                            (r, n) -> {
                                var fees = new ArrayList<Fee>();
                                fees.add(
                                        new Fee(
                                                "training_fee",
                                                "client.trainingFee",
                                                r.getBigDecimal(8)));
                                fees.add(
                                        new Fee(
                                                "system_install_fee",
                                                "client.systemInstallFee",
                                                r.getBigDecimal(9)));
                                fees.add(
                                        new Fee(
                                                "android_fee",
                                                "client.androidFee",
                                                r.getBigDecimal(10)));
                                fees.add(
                                        new Fee(
                                                "reinstall_fee",
                                                "client.systemReinstallFee",
                                                r.getBigDecimal(11)));
                                if (full) {
                                    fees.add(
                                            new Fee(
                                                    "hosting_fee",
                                                    "client.hostingFee",
                                                    r.getBigDecimal(12)));
                                    fees.add(
                                            new Fee(
                                                    "bulk_sms",
                                                    "client.bulkSmsFee",
                                                    r.getBigDecimal(13)));
                                }
                                return new Portfolio(
                                        table,
                                        r.getLong(1),
                                        r.getLong(2),
                                        r.getLong(3),
                                        r.getLong(4),
                                        r.getBigDecimal(5),
                                        r.getBigDecimal(6),
                                        r.getBigDecimal(7),
                                        List.copyOf(fees),
                                        r.getLong(14),
                                        r.getBigDecimal(15),
                                        r.getBigDecimal(16));
                            },
                            table.equals("other_clients") ? new Object[0] : new Object[]{financialYearId}));
        }
        return List.copyOf(results);
    }

    public record Fee(String reportType, String label, BigDecimal amount) {}

    public record Portfolio(
            String category,
            long clients,
            long paid,
            long unpaid,
            long pending,
            BigDecimal annualFee,
            BigDecimal received,
            BigDecimal withholdingTax,
            List<Fee> fees,
            long cloudPaid,
            BigDecimal cloudFee,
            BigDecimal cloudCost) {
        public BigDecimal annualDifference() {
            return annualFee
                    .subtract(received)
                    .subtract(category.equals("saccos") ? withholdingTax : BigDecimal.ZERO);
        }

        public long cloudUnpaid() {
            return clients - cloudPaid;
        }

        public String slug() {
            return switch (category) {
                case "microfinance_clients" -> "microfinance";
                case "other_clients" -> "others";
                default -> category;
            };
        }
    }

    public record Revenue(String category, long clients, BigDecimal received, BigDecimal costs) {
        public BigDecimal profit() {
            return received.subtract(costs);
        }
    }
}
