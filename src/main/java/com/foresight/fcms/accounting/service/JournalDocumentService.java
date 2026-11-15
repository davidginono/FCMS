package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.AccountingViews.Journal;
import com.foresight.fcms.accounting.view.AmountInWords;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class JournalDocumentService {
    private final JdbcTemplate db;
    private final JournalService journals;

    public JournalDocumentService(JdbcTemplate db, JournalService journals) {
        this.db = db;
        this.journals = journals;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VoucherDocument document(long id, Locale locale) {
        var journal = journals.find(id);
        var values =
                db.queryForMap(
                        "select j.posted_at,coalesce((select sum(l.debit-l.credit) from gl_lines l"
                            + " join gl_accounts a on a.id=l.account_id where l.journal_id=j.id and"
                            + " a.cash_account),0) cash_delta from gl_journals j where j.id=?",
                        id);
        var cash = (BigDecimal) values.get("cash_delta");
        var total =
                switch (journal.kind()) {
                    case "RECEIPT" -> cash;
                    case "PAYMENT" -> cash.negate();
                    default -> journal.total();
                };
        var posted = values.get("posted_at");
        Instant at = posted == null ? null : ((java.sql.Timestamp) posted).toInstant();
        var cashIds =
                db.queryForList(
                        "select distinct a.id from gl_accounts a join gl_lines l on"
                            + " l.account_id=a.id where l.journal_id=? and a.cash_account",
                        Long.class,
                        id);
        // Signed counter-entries reconcile to cash movement, including non-cash adjustments.
        var receiptItems =
                journal.lines().stream()
                        .filter(line -> !cashIds.contains(line.accountId()))
                        .map(
                                line ->
                                        new ReceiptItem(
                                                line.accountCode()
                                                        + " - "
                                                        + line.accountName()
                                                        + (line.description().isBlank()
                                                                ? ""
                                                                : "\n" + line.description()),
                                                line.credit().subtract(line.debit())))
                        .toList();
        var moneyAccounts =
                journal.lines().stream()
                        .filter(line -> cashIds.contains(line.accountId()))
                        .map(line -> line.accountCode() + " - " + line.accountName())
                        .distinct()
                        .toList();
        return new VoucherDocument(
                journal,
                total,
                AmountInWords.amountInWords(total, locale),
                at,
                receiptItems,
                moneyAccounts);
    }

    public record ReceiptItem(String particulars, BigDecimal amount) {}

    public record VoucherDocument(
            Journal journal,
            BigDecimal amount,
            String words,
            Instant postedAt,
            List<ReceiptItem> receiptItems,
            List<String> moneyAccounts) {}
}
