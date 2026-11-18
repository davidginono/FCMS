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
        requireLookup();
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
        requireLookup();
        if (db.queryForObject("select count(*) from saccos where id=? and visible", Long.class, id)
                != 1) throw new IllegalArgumentException("error.missing");
    }

    @Transactional(readOnly = true)
    public com.foresight.fcms.shared.dto.ChoicePage<Choice> search(String search, int page) {
        requireLookup();
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

    public record VoucherClient(String id, String name) {}
    private static final String VOUCHER_CLIENTS = "select 'saccos:'||id id,saccos_name name,visible from saccos union all select 'amcos:'||id,client_name,visible from amcos union all select 'unions:'||id,client_name,visible from unions union all select 'microfinance:'||id,client_name,visible from microfinance_clients union all select 'others:'||id,client_name,visible from other_clients";

    @Transactional(readOnly=true)
    public com.foresight.fcms.shared.dto.ChoicePage<VoucherClient> voucherClients(String search,int page) {
        requireLookup();
        if(search==null || search.length()>100 || page<0 || page>1000)throw new IllegalArgumentException("error.invalid");
        var q=new com.foresight.fcms.shared.dto.RegisterQuery();q.setSearch(search);
        return com.foresight.fcms.shared.dto.ChoicePage.from(db.query("select id,name from ("+VOUCHER_CLIENTS+") clients where visible and lower(name) like ? escape '!' order by name,id limit 26 offset ?",(r,n)->new VoucherClient(r.getString(1),r.getString(2)),q.pattern(),page*25),page);
    }

    @Transactional(readOnly=true)
    public VoucherClient voucherClient(String key) {
        requireLookup();
        if(key==null || !key.matches("(saccos|amcos|unions|microfinance|others):[1-9][0-9]{0,17}"))throw new IllegalArgumentException("error.invalid");
        return db.query("select id,name from ("+VOUCHER_CLIENTS+") clients where visible and id=?",(r,n)->new VoucherClient(r.getString(1),r.getString(2)),key).stream().findFirst().orElseThrow(()->new IllegalArgumentException("error.invalid"));
    }

    public record Choice(long id, String name) {}

    private void requireLookup() {
        var actor = access.current();
        if (!actor.has("client.read")) actor.require("client.lookup");
    }
}
