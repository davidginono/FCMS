package com.foresight.fcms.shared.dto;

import java.time.LocalDate;
import java.util.Map;

public class RegisterQuery {
    private Long ownerId;
    private Long clientId;

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long id) {
        if (id != null && id < 1) throw new IllegalArgumentException("error.invalid");
        ownerId = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long id) {
        if (id != null && id < 1) throw new IllegalArgumentException("error.invalid");
        clientId = id;
    }

    private int page;
    private int size = 25;
    private String search = "";
    private String sort = "id";
    private String direction = "desc";
    private String status = "";
    private LocalDate from;
    private LocalDate to;

    public int getPage() {
        return page;
    }

    public void setPage(int v) {
        page = Math.max(0, Math.min(v, 10000));
    }

    public int getSize() {
        return size;
    }

    public void setSize(int v) {
        size = java.util.Set.of(10, 25, 50, 100).contains(v) ? v : 25;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String v) {
        search = v == null ? "" : v.trim().substring(0, Math.min(v.trim().length(), 100));
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String v) {
        sort = v;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String v) {
        direction = "asc".equals(v) ? "asc" : "desc";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v == null ? "" : v.substring(0, Math.min(v.length(), 30));
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate v) {
        from = v;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate v) {
        to = v;
    }

    public long offset() {
        return (long) page * size;
    }

    public String order(Map<String, String> allowed) {
        return allowed.getOrDefault(sort, "id") + " " + direction + ", id " + direction;
    }

    public String pattern() {
        return "%"
                + search.toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                + "%";
    }

    public void validate() {
        if (from != null && to != null && to.isBefore(from))
            throw new IllegalArgumentException("error.dates");
    }
}
