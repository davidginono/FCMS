package com.foresight.fcms.client.service;

import com.foresight.fcms.auth.service.AccessService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientDirectoryService {
    private final JdbcTemplate db;
    private final AccessService access;

    public ClientDirectoryService(JdbcTemplate db, AccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Choice> saccos(String search, Long selected) {
        access.require("client.read");
        return db.query(
                "select id,saccos_name from saccos where visible and (lower(saccos_name) like ? or"
                        + " id=?) order by (id=? ) desc nulls last,saccos_name,id limit 50",
                (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                "%" + (search == null ? "" : search.toLowerCase(java.util.Locale.ROOT)) + "%",
                selected,
                selected);
    }

    @Transactional(readOnly = true)
    public void requireSaccos(long id) {
        access.require("client.read");
        if (db.queryForObject("select count(*) from saccos where id=? and visible", Long.class, id)
                != 1) throw new IllegalArgumentException("error.missing");
    }

    @Transactional(readOnly = true)
    public com.foresight.fcms.shared.dto.ChoicePage<Choice> search(String search, int page) {
        access.require("client.read");
        if (page < 0 || page > 1000 || search.length() > 100) throw new IllegalArgumentException();
        var rows =
                db.query(
                        "select id,saccos_name from saccos where visible and lower(saccos_name)"
                                + " like ? order by saccos_name,id limit 26 offset ?",
                        (r, n) -> new Choice(r.getLong(1), r.getString(2)),
                        "%" + search.toLowerCase(java.util.Locale.ROOT) + "%",
                        page * 25);
        return com.foresight.fcms.shared.dto.ChoicePage.from(rows, page);
    }

    public record Choice(long id, String name) {}
}
