package com.foresight.fcms.sales.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.sales.dto.*;
import com.foresight.fcms.sales.model.SalesKind;
import com.foresight.fcms.shared.dto.*;

import jakarta.validation.Validator;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class SalesService {
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;
    private final AccessService access;
    private final AuditService audit;
    private final Validator validator;
    private final JsonMapper json = new JsonMapper();

    public SalesService(
            JdbcTemplate db, AccessService access, AuditService audit, Validator validator) {
        this.db = db;
        this.named = new NamedParameterJdbcTemplate(db);
        this.access = access;
        this.audit = audit;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public void formAccess(SalesKind kind) {
        var a = access.require(kind.getClaim() + ".read");
        a.require(kind.getClaim() + ".write");
    }

    @Transactional(readOnly = true)
    public RegisterPage<SalesSummary> list(SalesKind kind, RegisterQuery q) {
        access.require(kind.getClaim() + ".read");
        q.validate();
        if (!q.getStatus().isEmpty() && !kind.getTypes().contains(q.getStatus()))
            throw new IllegalArgumentException("error.invalid");
        return RegisterPage.of(
                db.query(
                        """
                        select id,client_type,client_name,document_number,document_date,valid_until,currency,amount
                        from sales_documents where kind=? and (?='' or client_type=?)
                        and lower(client_name||' '||document_number) like ? escape '!'
                        and (?::date is null or document_date>=?::date) and (?::date is null or document_date<=?::date)
                        """
                                + " order by "
                                + q.order(
                                        Map.of(
                                                "id",
                                                "id",
                                                "clientType",
                                                "client_type",
                                                "clientName",
                                                "client_name",
                                                "documentNumber",
                                                "document_number",
                                                "documentDate",
                                                "document_date",
                                                "validUntil",
                                                "valid_until",
                                                "currency",
                                                "currency",
                                                "amount",
                                                "amount"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new SalesSummary(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getString(3),
                                        r.getString(4),
                                        r.getObject(5, LocalDate.class),
                                        r.getObject(6, LocalDate.class),
                                        r.getString(7),
                                        r.getBigDecimal(8)),
                        kind.name(),
                        q.getStatus(),
                        q.getStatus(),
                        q.pattern(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    private SalesDocument row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        var f = new SalesForm();
        f.setClientType(r.getString("client_type"));
        f.setClientName(r.getString("client_name"));
        f.setRegion(r.getString("region"));
        f.setContactName(r.getString("contact_name"));
        f.setPhone(r.getString("phone"));
        f.setEmail(r.getString("email"));
        f.setDocumentNumber(r.getString("document_number"));
        f.setDocumentDate(r.getObject("document_date", LocalDate.class));
        f.setValidUntil(r.getObject("valid_until", LocalDate.class));
        f.setCurrency(r.getString("currency"));
        f.setTerms(r.getString("terms"));
        f.setBillingAddress(r.getString("billing_address"));
        f.setBillingCity(r.getString("billing_city"));
        f.setBillingTin(r.getString("billing_tin"));
        f.setPaymentMethod(r.getString("payment_method"));
        f.setVersion(r.getLong("version"));
        f.setItems(
                new ArrayList<>(List.of(json.readValue(r.getString("items"), SalesItem[].class))));
        return new SalesDocument(
                r.getLong("id"),
                r.getString("kind"),
                f,
                r.getBigDecimal("amount"),
                r.getLong("created_by"),
                r.getLong("updated_by"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant());
    }

    @Transactional(readOnly = true)
    public SalesDocument find(SalesKind kind, long id) {
        access.require(kind.getClaim() + ".read");
        return db
                .query(
                        "select * from sales_documents where id=? and kind=?",
                        this::row,
                        id,
                        kind.name())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public SalesForm form(SalesKind kind, long id) {
        access.require(kind.getClaim() + ".write");
        return find(kind, id).data();
    }

    @Transactional(readOnly = true)
    public SalesDocument export(SalesKind kind, long id) {
        access.require(kind.getClaim() + ".export");
        var d = find(kind, id);
        if (total(d.data()).compareTo(d.amount()) != 0)
            throw new IllegalArgumentException("sales.error.reconcile");
        return d;
    }

    @Transactional(readOnly = true)
    public RegisterPage<Revision> history(SalesKind kind, long id, RegisterQuery q) {
        find(kind, id);
        q.validate();
        return RegisterPage.of(
                db.query(
                        """
                         select version,actor_id,occurred_at,version as id from sales_document_revisions
                         where document_id=? and (cast(version+1 as text)||' '||cast(actor_id as text)) like ? escape '!'
                        """
                                + " order by "
                                + q.order(
                                        Map.of(
                                                "revision",
                                                "version",
                                                "actor",
                                                "actor_id",
                                                "at",
                                                "occurred_at"))
                                + " limit ? offset ?",
                        (r, n) ->
                                new Revision(
                                        r.getLong(1), r.getLong(2), r.getTimestamp(3).toInstant()),
                        id,
                        q.pattern(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    public record Revision(long version, long actor, Instant at) {}

    private String clean(String value, int limit, boolean multiline) {
        if (value == null) throw new IllegalArgumentException("error.invalid");
        value = value.trim();
        if (value.length() > limit
                || value.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                && !(multiline && (c == 9 || c == 10 || c == 13))))
            throw new IllegalArgumentException("error.invalid");
        return value;
    }

    private BigDecimal total(SalesForm f) {
        if (f.getItems() == null || f.getItems().isEmpty() || f.getItems().size() > 50)
            throw new IllegalArgumentException("sales.error.items");
        BigDecimal total = BigDecimal.ZERO;
        int bytes = 0;
        for (var item : f.getItems()) {
            if (item == null) throw new IllegalArgumentException("sales.error.items");
            item.setDescription(clean(item.getDescription(), 4000, true));
            var amount = item.getAmount();
            if (item.getDescription().isBlank()
                    || amount == null
                    || amount.signum() < 0
                    || amount.scale() > 2
                    || amount.compareTo(new BigDecimal("999999999999.99")) > 0)
                throw new IllegalArgumentException("sales.error.items");
            item.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
            total = total.add(item.getAmount());
            bytes += item.getDescription().getBytes(StandardCharsets.UTF_8).length + 1;
        }
        if (bytes > 50000 || total.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new IllegalArgumentException("sales.error.items");
        return total.setScale(2);
    }

    @Transactional
    public long save(SalesKind kind, Long id, SalesForm f) {
        var actor = access.require(kind.getClaim() + ".write");
        actor.require(kind.getClaim() + ".read");
        if (!validator.validate(f).isEmpty()) throw new IllegalArgumentException("error.invalid");
        if (!kind.getTypes().contains(f.getClientType()))
            throw new FieldValidationException("error.invalid", "clientType");
        if (!Set.of("TZS", "USD", "KES", "EUR", "GBP").contains(f.getCurrency()))
            throw new FieldValidationException("error.invalid", "currency");
        if (!Set.of("CASH", "CHEQUE", "BANK_TRANSFER").contains(f.getPaymentMethod()))
            throw new FieldValidationException("error.invalid", "paymentMethod");
        if (f.getDocumentDate().isBefore(LocalDate.of(1900, 1, 1)))
            throw new FieldValidationException("error.dates", "documentDate");
        if (f.getValidUntil().isBefore(f.getDocumentDate()))
            throw new FieldValidationException("error.dates", "validUntil");
        f.setClientName(clean(f.getClientName(), 150, false));
        f.setDocumentNumber(clean(f.getDocumentNumber(), 60, false));
        f.setRegion(clean(f.getRegion(), 100, false));
        f.setContactName(clean(f.getContactName(), 150, false));
        f.setPhone(clean(f.getPhone(), 40, false));
        f.setEmail(clean(f.getEmail(), 190, false));
        f.setBillingAddress(clean(f.getBillingAddress(), 250, false));
        f.setBillingCity(clean(f.getBillingCity(), 150, false));
        f.setBillingTin(clean(f.getBillingTin(), 60, false));
        f.setTerms(clean(f.getTerms(), 2000, true));
        BigDecimal amount = total(f);
        var p =
                new MapSqlParameterSource()
                        .addValue("kind", kind.name())
                        .addValue("type", f.getClientType())
                        .addValue("name", f.getClientName())
                        .addValue("region", f.getRegion())
                        .addValue("contact", f.getContactName())
                        .addValue("phone", f.getPhone())
                        .addValue("email", f.getEmail())
                        .addValue("number", f.getDocumentNumber())
                        .addValue("date", f.getDocumentDate())
                        .addValue("until", f.getValidUntil())
                        .addValue("currency", f.getCurrency())
                        .addValue("items", json.writeValueAsString(f.getItems()))
                        .addValue("amount", amount)
                        .addValue("terms", f.getTerms())
                        .addValue("address", f.getBillingAddress())
                        .addValue("city", f.getBillingCity())
                        .addValue("tin", f.getBillingTin())
                        .addValue("payment", f.getPaymentMethod())
                        .addValue("actor", actor.id())
                        .addValue("id", id)
                        .addValue("version", f.getVersion());
        // Ordered value encoding retains field boundaries; retries cannot substitute another
        // command.
        String payload = json.writeValueAsString(new TreeMap<>(p.getValues()));
        String fingerprint;
        try {
            fingerprint =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        // Serialize only identical actor/request commands; row versions arbitrate unrelated edits.
        db.query(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                r -> {},
                actor.id() + ":" + f.getRequestKey());
        var prior =
                db.queryForList(
                        "select document_id,fingerprint from sales_document_revisions where"
                                + " actor_id=? and request_key=?",
                        actor.id(),
                        f.getRequestKey());
        if (!prior.isEmpty()) {
            if (!fingerprint.equals(prior.getFirst().get("fingerprint")))
                throw new IllegalArgumentException("error.conflict");
            return ((Number) prior.getFirst().get("document_id")).longValue();
        }
        if (id == null) {
            id =
                    named.queryForObject(
                            """
                            insert into sales_documents(kind,client_type,client_name,region,contact_name,phone,email,document_number,document_date,
                            valid_until,currency,items,amount,terms,billing_address,billing_city,billing_tin,payment_method,created_by,updated_by)
                            values(:kind,:type,:name,:region,:contact,:phone,:email,:number,:date,:until,:currency,cast(:items as jsonb),
                            :amount,:terms,:address,:city,:tin,:payment,:actor,:actor) returning id
                            """,
                            p,
                            Long.class);
        } else {
            find(kind, id);
            int updated =
                    named.update(
                            """
                            update sales_documents set client_type=:type,client_name=:name,region=:region,contact_name=:contact,phone=:phone,email=:email,
                            document_number=:number,document_date=:date,valid_until=:until,currency=:currency,items=cast(:items as jsonb),amount=:amount,
                            terms=:terms,billing_address=:address,billing_city=:city,billing_tin=:tin,payment_method=:payment,
                            updated_by=:actor,updated_at=current_timestamp,version=version+1 where id=:id and kind=:kind and version=:version
                            """,
                            p);
            if (updated != 1) throw new OptimisticLockingFailureException("error.stale");
        }
        db.update(
                """
                insert into sales_document_revisions(document_id,version,actor_id,request_key,fingerprint,snapshot)
                select id,version,?,?,?,to_jsonb(d) from sales_documents d where id=?
                """,
                actor.id(),
                f.getRequestKey(),
                fingerprint,
                id);
        audit.record(actor.id(), "sales.save", kind.name(), id, "Document revision retained");
        return id;
    }
}
