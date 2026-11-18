package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.StatementDocument;
import com.foresight.fcms.accounting.view.AccountingStationery;
import com.foresight.fcms.accounting.view.JournalStationery;
import com.foresight.fcms.accounting.view.VoucherStationery;
import com.foresight.fcms.accounting.view.VoucherStationery.Field;
import com.foresight.fcms.shared.view.ApplicationTimestamps;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * Original Microfinance accounting stationery adapted to FCMS and the installed OpenPDF renderer.
 */
@Service
public class AccountingDocumentPdfService {
    private final MessageSource messages;
    private final Semaphore renders = new Semaphore(2);

    public AccountingDocumentPdfService(MessageSource messages) {
        this.messages = messages;
    }

    private String text(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private String money(BigDecimal amount, Locale locale) {
        var format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    interface Content {
        void write(Document document, PdfWriter writer, BaseFont font) throws Exception;
    }

    private byte[] render(String title, boolean landscape, boolean exactPaper, Content content) {
        if (!renders.tryAcquire()) throw new IllegalArgumentException("error.exportBusy");
        try (var fontBytes =
                        new ClassPathResource("report-fonts/NotoSans-Regular.ttf")
                                .getInputStream();
                var out = new ByteArrayOutputStream()) {
            var face =
                    BaseFont.createFont(
                            "NotoSans-Regular.ttf",
                            BaseFont.IDENTITY_H,
                            BaseFont.EMBEDDED,
                            false,
                            fontBytes.readAllBytes(),
                            null);
            var paper = new Rectangle(595.27563f, 841.8898f);
            var document =
                    new Document(
                            landscape ? new Rectangle(paper.getHeight(), paper.getWidth()) : paper,
                            24,
                            24,
                            28,
                            40);
            var writer = PdfWriter.getInstance(document, out);
            document.addTitle(title);
            document.addCreator("FCMS");
            document.open();
            content.write(document, writer, face);
            document.close();
            return exactPaper
                    ? exactPaper(out.toByteArray(), document.getPageSize())
                    : out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Accounting document generation failed", e);
        } finally {
            renders.release();
        }
    }

    /** Retain the original A4 media box without changing OpenPDF's global number formatting. */
    private byte[] exactPaper(byte[] content, Rectangle size) throws IOException, DocumentException {
        try (var reader = new PdfReader(content);
                var out = new ByteArrayOutputStream()) {
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                var box = new PdfArray();
                for (float value : new float[] {0, 0, size.getWidth(), size.getHeight()})
                    box.add(new PdfNumber(Float.toString(value)));
                reader.getPageN(page).put(PdfName.MEDIABOX, box);
            }
            try (var stamper = new PdfStamper(reader, out)) {
                // Closing writes the existing content with the precise page dictionary.
            }
            return out.toByteArray();
        }
    }

    public byte[] voucher(JournalDocumentService.VoucherDocument source, Locale locale) {
        if (!List.of("RECEIPT", "PAYMENT").contains(source.journal().kind()))
            return ledgerVoucher(source, locale);
        String title = text("gl.kind." + source.journal().kind(), locale);
        return render(
                title + " #" + source.journal().id(),
                false,
                false,
                (document, writer, face) ->
                        new VoucherStationery(document, writer, face, stationery(source, locale))
                                .write());
    }

    private Field field(String key, String value, Locale locale) {
        return new Field(text(key, locale), value == null ? "" : value);
    }

    private VoucherStationery.Source stationery(
            JournalDocumentService.VoucherDocument source, Locale locale) {
        var j = source.journal();
        boolean receipt = j.kind().equals("RECEIPT");
        var rows = new ArrayList<List<String>>();
        if (receipt) {
            for (var item : source.receiptItems())
                rows.add(
                        List.of(
                                Integer.toString(rows.size() + 1),
                                item.particulars(),
                                money(item.amount(), locale)));
        } else {
            for (var line : j.lines())
                rows.add(
                        List.of(
                                Integer.toString(rows.size() + 1),
                                line.description(),
                                line.accountCode() + " - " + line.accountName(),
                                line.debit().signum() == 0 ? "" : money(line.debit(), locale),
                                line.credit().signum() == 0 ? "" : money(line.credit(), locale)));
        }
        if(source.header()!=null) {
            rows.clear();
            for(var transaction:source.transactions()) {
                String description=transaction.description();
                if(!transaction.transactionName().isBlank())description+="\n"+transaction.transactionName();
                if(!transaction.component().equals("TOTAL"))description+="\n"+text("voucher.component."+transaction.component(),locale);
                if(receipt)rows.add(List.of(Integer.toString(rows.size()+1),description,money(transaction.amount(),locale)));
                else for(var entry:transaction.postings().stream().sorted(java.util.Comparator.comparingInt(e->e.side().equals("CREDIT")?0:1)).toList()) {
                    boolean debit=entry.side().equals("DEBIT");
                    rows.add(List.of(Integer.toString(rows.size()+1),description,entry.account().name(),debit?money(entry.amount(),locale):"",debit?"":money(entry.amount(),locale)));
                }
            }
        }
        var totals = new ArrayList<Field>();
        if (!receipt) {
            totals.add(field("gl.document.totalDebit", money(j.total(), locale), locale));
            totals.add(field("gl.document.totalCredit", money(j.total(), locale), locale));
        }
        totals.add(field("gl.document.totalAmount", money(source.amount(), locale), locale));
        String narration =
                j.description()
                        + "\n"
                        + text("gl.evidence", locale)
                        + ": "
                        + j.evidenceReference()
                        + "\n"
                        + text("gl.cashFlowClassification", locale)
                        + ": "
                        + text("gl.flow." + j.cashFlow(), locale);
        if (!j.reviewComment().isBlank())
            narration += "\n" + text("gl.review", locale) + ": " + j.reviewComment();
        if (j.reversesId() != null)
            narration += "\n" + text("ledger.reversalOf", locale) + ": #" + j.reversesId();
        if (j.reversedBy() != null)
            narration += "\n" + text("ledger.reversedBy", locale) + ": #" + j.reversedBy();
        var posting =
                List.of(
                        field("gl.maker", j.maker() + " (#" + j.makerId() + ")", locale),
                        field(
                                "gl.checker",
                                j.checkerId() == null
                                        ? ""
                                        : j.checker() + " (#" + j.checkerId() + ")",
                                locale),
                        field("gl.document.postedAt", timestamp(source.postedAt()), locale),
                        field("common.status", text("status." + j.state(), locale), locale));
        return new VoucherStationery.Source(
                text("gl.kind." + j.kind(), locale),
                "FCMS",
                "",
                source.header()==null?"#"+j.id():source.header().number(),
                text("status." + j.state(), locale),
                List.of(
                        field("gl.voucherId", source.header()==null?Long.toString(j.id()):source.header().number(), locale),
                        field("common.date", ApplicationTimestamps.date(j.date()), locale),
                        field("common.status", text("status." + j.state(), locale), locale)),
                text(receipt ? "gl.document.payerDetails" : "gl.document.payeeDetails", locale),
                List.of(
                        field("common.name", source.header()==null?"":source.header().party(), locale),
                        field("gl.document.company", "", locale),
                        field("gl.document.address", "", locale),
                        field("gl.document.phone", "", locale),
                        field("common.email", "", locale)),
                text(receipt ? "gl.document.receiptDetails" : "gl.document.paymentDetails", locale),
                receipt && source.header()!=null?List.of(field("common.reference",j.reference(),locale),field("common.date",ApplicationTimestamps.date(j.date()),locale)):List.of(
                        field(
                                "gl.document.moneyAccount",
                                String.join("\n", source.moneyAccounts()),
                                locale),
                        field("common.reference", j.reference(), locale),
                        field("common.date", ApplicationTimestamps.date(j.date()), locale)),
                receipt
                        ? List.of(
                                "#",
                                text("gl.document.particulars", locale),
                                text("gl.document.amount", locale) + " (TZS)")
                        : List.of(
                                "#",
                                text("gl.document.particulars", locale),
                                text("gl.account", locale),
                                text("gl.debit", locale),
                                text("gl.credit", locale)),
                receipt
                        ? new float[] {.06f, .74f, .20f}
                        : new float[] {.055f, .26f, .325f, .18f, .18f},
                rows,
                text("gl.document.amountWords", locale),
                source.words(),
                totals,
                text("gl.document.narration", locale),
                narration,
                text("gl.document.postingRecord", locale),
                posting,
                text("gl.document.page", locale),
                text("gl.document.continued", locale),
                null,
                text("gl.document.method", locale),
                List.of(
                        field("gl.document.cash", "false", locale),
                                field("gl.document.bank", "false", locale),
                        field("gl.document.cheque", "false", locale),
                                field("gl.document.mobile", "false", locale)));
    }

    private byte[] ledgerVoucher(JournalDocumentService.VoucherDocument source, Locale locale) {
        var display = journalStationery(source, locale);
        return render(
                display.title() + " " + display.number(),
                false,
                false,
                (document, writer, face) ->
                        new JournalStationery(document, writer, face, display).write());
    }

    private String timestamp(java.time.Instant instant) {
        return instant == null
                ? ""
                : java.time.format.DateTimeFormatter.ofPattern(
                                "dd/MM/uuuu HH:mm:ss z", Locale.ENGLISH)
                        .withZone(java.time.ZoneId.of("Africa/Dar_es_Salaam"))
                        .format(instant);
    }

    private JournalStationery.Source journalStationery(
            JournalDocumentService.VoucherDocument source, Locale locale) {
        var journal = source.journal();
        var rows = new ArrayList<JournalStationery.Row>();
        for (var line : journal.lines())
            rows.add(
                    new JournalStationery.Row(
                            Integer.toString(rows.size() + 1),
                            line.description(),
                            line.accountCode() + " - " + line.accountName(),
                            line.debit().signum() == 0 ? "" : money(line.debit(), locale),
                            line.credit().signum() == 0 ? "" : money(line.credit(), locale)));
        var posting =
                new ArrayList<Field>(
                        List.of(
                                field(
                                        "gl.maker",
                                        journal.maker() + " (#" + journal.makerId() + ")",
                                        locale),
                                field(
                                        "gl.checker",
                                        journal.checkerId() == null
                                                ? ""
                                                : journal.checker()
                                                        + " (#"
                                                        + journal.checkerId()
                                                        + ")",
                                        locale),
                                field("gl.document.postedAt", timestamp(source.postedAt()), locale),
                                field(
                                        "common.status",
                                        text("status." + journal.state(), locale),
                                        locale)));
        if (!journal.reviewComment().isBlank())
            posting.add(field("gl.review", journal.reviewComment(), locale));
        if (journal.reversesId() != null)
            posting.add(field("ledger.reversalOf", "#" + journal.reversesId(), locale));
        if (journal.reversedBy() != null)
            posting.add(field("ledger.reversedBy", "#" + journal.reversedBy(), locale));
        return new JournalStationery.Source(
                "FCMS",
                "",
                text("gl.kind." + journal.kind(), locale),
                source.header()==null?"#"+journal.id():source.header().number(),
                text("status." + journal.state(), locale),
                text("gl.voucherId", locale),
                text("common.date", locale),
                ApplicationTimestamps.date(journal.date()),
                text("common.reference", locale),
                journal.reference(),
                List.of(
                        field("gl.evidence", journal.evidenceReference(), locale),
                        field(
                                "gl.cashFlowClassification",
                                text("gl.flow." + journal.cashFlow(), locale),
                                locale)),
                List.of(
                        "#",
                        text("gl.document.particulars", locale),
                        text("gl.account", locale),
                        text("gl.debit", locale).replace(" (TZS)", ""),
                        text("gl.credit", locale).replace(" (TZS)", "")),
                rows,
                text("gl.document.amountWords", locale),
                source.words(),
                List.of(
                        field("gl.document.totalDebit", money(journal.total(), locale), locale),
                        field("gl.document.totalCredit", money(journal.total(), locale), locale),
                        field("gl.document.totalAmount", money(source.amount(), locale), locale)),
                text("gl.document.narration", locale),
                journal.description(),
                text("gl.document.postingRecord", locale),
                posting,
                text("gl.document.page", locale),
                text("gl.document.continued", locale));
    }

    public byte[] statement(StatementDocument source, Locale locale) {
        return render(
                source.title(),
                source.landscape(),
                true,
                (document, writer, face) -> {
                    var page =
                            new AccountingStationery(
                                    document,
                                    writer,
                                    face,
                                    source.style().equals("equity")?source.title():source.title().toUpperCase(locale),
                                    source.context(),
                                    AccountingStationery.Style.valueOf(
                                            source.style().toUpperCase(Locale.ROOT)));
                    float[] weights = new float[source.columns().size()];
                    boolean[] numeric = new boolean[weights.length];
                    for (int i = 0; i < weights.length; i++) {
                        weights[i] = source.columns().get(i).weight();
                        numeric[i] = source.columns().get(i).numeric();
                    }
                    page.columns(
                            source.columns().stream().map(StatementDocument.Column::label).toList(),
                            weights,
                            numeric);
                    for (var row : source.rows())
                        if (row.heading()) page.section(row.label());
                        else page.row(row.cells(), row.total());
                    page.endTable();
                    if(source.style().equals("equity"))page.text(source.context().get(1));
                    page.text(source.notice());
                    if (!source.provenance().isEmpty()) {
                        page.appendix(text("gl.reportStatus", locale));
                        for (String value : source.provenance()) page.text(value);
                    }
                });
    }
}
