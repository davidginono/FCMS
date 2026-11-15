package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.finance.dto.PostingSource;
import com.foresight.fcms.finance.service.FinancePostingSourceService;
import com.foresight.fcms.shared.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.*;

@Service
@Validated
public class SourcePostingService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final FinancePostingSourceService finance;
    private final AccountingService accounting;
    private final PostingMappingService mappings;
    private final SourceBindingService bindings;
    private final JournalService journals;

    public SourcePostingService(
            JdbcTemplate db,
            AccessService access,
            FinancePostingSourceService finance,
            AccountingService accounting,
            PostingMappingService mappings,
            SourceBindingService bindings,
            JournalService journals) {
        this.db = db;
        this.access = access;
        this.finance = finance;
        this.accounting = accounting;
        this.mappings = mappings;
        this.bindings = bindings;
        this.journals = journals;
    }

    private static final String MAPPING_EVENT =
            "case when s.reverses_id is null then s.source_event when o.source_date<b.opening_date"
                    + " then (case when o.source_kind='EXPENSE' then 'EXPENSE' else"
                    + " substring(o.source_kind from 8) end)||'_OPENING_REVERSAL' else '' end";
    private static final String WORKSPACE =
            """
            with workspace as (
             select s.*,s.source_id id,
            """
                    + MAPPING_EVENT
                    + """
                     mapping_event,r.journal_id,j.state journal_state,orr.journal_id original_journal_id,
                     case when j.state in ('DRAFT','SUBMITTED','REJECTED','POSTED') then l.mapping_id
                          when s.reverses_id is not null and o.source_date>=b.opening_date then ol.mapping_id else m.id end mapping_id,
                     case when b.state is distinct from 'APPROVED' or (b.opening_mode='JOURNAL' and not exists(select 1 from gl_journals where kind='OPENING' and state='POSTED')) then 'BOOKS_PENDING'
                      when s.source_date is null or (s.reverses_id is not null and o.source_date is null) or (s.source_date<b.opening_date and (s.recorded_at is null or s.recorded_at>b.approved_at) and not s.opening_verified) or (o.source_date<b.opening_date and (o.recorded_at is null or o.recorded_at>b.approved_at) and not o.opening_verified) then 'UNVERIFIED'
                      when s.source_date<b.opening_date then 'OPENING_COVERED'
                      when j.state in ('DRAFT','SUBMITTED','REJECTED','POSTED') then j.state
                      when s.reverses_id is not null and o.source_date>=b.opening_date and oj.state is distinct from 'POSTED' then 'WAITING_ORIGINAL'
                      when not exists(select 1 from gl_periods where s.source_date between starts_on and ends_on) then 'PERIOD_REQUIRED'
                      when not exists(select 1 from gl_periods where state='OPEN' and s.source_date between starts_on and ends_on) then 'PERIOD_CLOSED'
                      when s.reverses_id is not null and o.source_date>=b.opening_date then 'READY'
                      when m.eligible_count=0 then 'UNMAPPED' else 'READY' end coverage
                    from finance_gl_sources s left join finance_gl_sources o on o.source_kind=s.reverses_kind and o.source_id=s.reverses_id
                    left join gl_books b on true
                    left join gl_source_reservations r on r.source_kind=s.source_kind and r.source_id=s.source_id
                    left join gl_journals j on j.id=r.journal_id left join gl_source_links l on l.journal_id=j.id
                    left join gl_source_reservations orr on orr.source_kind=o.source_kind and orr.source_id=o.source_id
                    left join gl_journals oj on oj.id=orr.journal_id left join gl_source_links ol on ol.journal_id=oj.id
                    left join lateral (select case when count(*)=1 then min(pm.id) end id,count(*) eligible_count
                     from gl_posting_mappings pm join gl_transactions ct on ct.id=pm.transaction_id
                     join gl_activities ca on ca.id=ct.activity_id where pm.source_event=
                    """
                    + MAPPING_EVENT
                    + " and pm.state='APPROVED' and ct.active and ca.active) m on true) ";

    private Item row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Item(
                new PostingSource(
                        r.getString("source_kind"),
                        r.getLong("source_id"),
                        r.getString("source_event"),
                        r.getObject("source_date", java.time.LocalDate.class),
                        r.getString("reference"),
                        r.getString("description"),
                        r.getBigDecimal("signed_amount"),
                        r.getLong("actor_id"),
                        r.getString("source_state"),
                        r.getString("reverses_kind"),
                        r.getObject("reverses_id", Long.class)),
                r.getString("mapping_event"),
                r.getObject("mapping_id", Long.class),
                r.getObject("journal_id", Long.class),
                r.getString("journal_state"),
                r.getString("coverage"),
                r.getObject("original_journal_id", Long.class));
    }

    private void readAccess() {
        access.require("accounting.read");
        access.require("finance.gl-source.read");
    }

    @Transactional(readOnly = true)
    public RegisterPage<Item> list(RegisterQuery q) {
        readAccess();
        q.validate();
        if (!Set.of(
                        "",
                        "BOOKS_PENDING",
                        "UNVERIFIED",
                        "OPENING_COVERED",
                        "WAITING_ORIGINAL",
                        "PERIOD_REQUIRED",
                        "PERIOD_CLOSED",
                        "UNMAPPED",
                        "READY",
                        "DRAFT",
                        "SUBMITTED",
                        "REJECTED",
                        "POSTED")
                .contains(q.getStatus())) throw new IllegalArgumentException("error.invalid");
        return RegisterPage.of(
                db.query(
                        WORKSPACE
                                + "select * from workspace where lower(source_kind||'"
                                + " '||reference||' '||description) like ? escape '!' and (?='' or"
                                + " coverage=?) and (source_date is null or ?::date is null or"
                                + " source_date>=?::date) and (source_date is null or ?::date is"
                                + " null or source_date<=?::date) order by "
                                + q.order(
                                        Map.ofEntries(
                                                Map.entry("id", "source_id"),
                                                Map.entry("kind", "source_kind"),
                                                Map.entry("date", "source_date"),
                                                Map.entry("reference", "reference"),
                                                Map.entry("amount", "signed_amount"),
                                                Map.entry("status", "coverage"),
                                                Map.entry("sourceState", "source_state"),
                                                Map.entry("journal", "journal_id")))
                                + ",source_kind limit ? offset ?",
                        this::row,
                        q.pattern(),
                        q.getStatus(),
                        q.getStatus(),
                        q.getFrom(),
                        q.getFrom(),
                        q.getTo(),
                        q.getTo(),
                        q.getSize() + 1,
                        q.offset()),
                q);
    }

    @Transactional(readOnly = true)
    public Item find(String kind, long id) {
        readAccess();
        return db
                .query(
                        WORKSPACE + "select * from workspace where source_kind=? and source_id=?",
                        this::row,
                        kind,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Workspace workspace(String kind, long id, RegisterQuery q) {
        var item = find(kind, id);
        var mapping = item.mappingId() == null ? null : mappings.find(item.mappingId());
        var original =
                item.originalJournalId() == null ? null : journals.find(item.originalJournalId());
        return new Workspace(
                item,
                mapping,
                original,
                bindings.history(kind, id, q),
                item.source().reversesId() != null && item.mappingEvent().isEmpty(),
                item.coverage().equals("READY"),
                finance.paymentEvidenceExpense(kind, id));
    }

    @Transactional
    public long prepare(String kind, long id, @jakarta.validation.Valid SourcePreparationForm f) {
        var actor = access.require("accounting.write");
        readAccess();
        SourceBindingService.requireEvidence(f.getEvidenceReference());
        bindings.lock(kind, id);
        var source = finance.verify(kind, id);
        var book =
                accounting
                        .books()
                        .orElseThrow(
                                () -> new IllegalArgumentException("accounting.error.opening"));
        if (!book.ready() || source.date().isBefore(book.openingDate()))
            throw new IllegalArgumentException("accounting.error.opening");
        var existing = bindings.current(kind, id);
        if (existing.isPresent() && !existing.get().state().equals("CANCELLED")) {
            if (!existing.get().evidence().equals(f.getEvidenceReference().trim())
                    || !Objects.equals(existing.get().mappingId(), f.getMappingId()))
                throw new IllegalArgumentException("error.conflict");
            return existing.get().journalId();
        }
        var item = find(kind, id);
        if (Set.of("PERIOD_REQUIRED", "PERIOD_CLOSED").contains(item.coverage()))
            throw new IllegalArgumentException("accounting.error.period");
        if (!item.coverage().equals("READY"))
            throw new IllegalArgumentException(
                    item.coverage().equals("WAITING_ORIGINAL")
                            ? "gl.source.waitingOriginal"
                            : item.coverage().equals("UNMAPPED")
                                    ? "gl.source.unmapped"
                                    : "gl.source.unavailable");
        if (source.reversesId() != null) {
            var original = finance.verify(source.reversesKind(), source.reversesId());
            if (!original.date().isBefore(book.openingDate())) {
                actor.require("accounting.reverse");
                var binding =
                        bindings.current(original.kind(), original.id())
                                .filter(b -> b.state().equals("POSTED"))
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "gl.source.waitingOriginal"));
                if (!Objects.equals(binding.mappingId(), f.getMappingId()))
                    throw new IllegalArgumentException("error.conflict");
                return journals.reverseSource(
                        binding.journalId(), kind, id, f.getRequestKey(), f.getEvidenceReference());
            }
        }
        if (f.getMappingId() == null)
            throw new IllegalArgumentException("gl.source.chooseTransaction");
        var mapping = mappings.forPreparation(f.getMappingId(), item.mappingEvent());
        var voucher = new JournalForm();
        voucher.setKind(mapping.voucherKind());
        voucher.setDate(source.date());
        voucher.setReference(
                source.reference().isBlank()
                        ? "SRC-" + source.kind() + "-" + source.id()
                        : source.reference());
        voucher.setDescription(source.description());
        voucher.setEvidenceReference(f.getEvidenceReference().trim());
        voucher.setCashFlow(mapping.cashFlow());
        voucher.setRequestKey(f.getRequestKey());
        voucher.getLines().get(0).setAccountId(mapping.debitAccountId());
        voucher.getLines().get(0).setDebit(source.signedAmount().abs());
        voucher.getLines().get(1).setAccountId(mapping.creditAccountId());
        voucher.getLines().get(1).setCredit(source.signedAmount().abs());
        // Keep the full source narration above; line descriptions have a smaller storage limit.
        String lineDescription =
                source.description().substring(0, Math.min(500, source.description().length()));
        voucher.getLines().forEach(line -> line.setDescription(lineDescription));
        long journal = journals.save(null, voucher);
        bindings.bind(kind, id, journal, mapping.id(), f.getEvidenceReference());
        return journal;
    }

    public record Item(
            PostingSource source,
            String mappingEvent,
            Long mappingId,
            Long journalId,
            String journalState,
            String coverage,
            Long originalJournalId) {}

    public record Workspace(
            Item item,
            PostingMapping mapping,
            AccountingViews.Journal original,
            RegisterPage<SourceBindingService.Attempt> attempts,
            boolean exactReversal,
            boolean preparable,
            Long paymentEvidenceExpense) {}
}
