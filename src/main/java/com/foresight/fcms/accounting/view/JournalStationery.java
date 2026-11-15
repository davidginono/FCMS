package com.foresight.fcms.accounting.view;

import com.foresight.fcms.accounting.view.VoucherStationery.Field;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.util.*;
import java.util.List;

/** Microfinance VoucherPdfService.Layout journal geometry, with exact FCMS debit/credit lines. */
public final class JournalStationery {
    public record Row(
            String number, String particulars, String ledger, String debit, String credit) {}

    public record Source(
            String institution,
            String branch,
            String title,
            String number,
            String status,
            String numberLabel,
            String dateLabel,
            String date,
            String referenceLabel,
            String reference,
            List<Field> evidence,
            List<String> columns,
            List<Row> rows,
            String wordsLabel,
            String words,
            List<Field> totals,
            String narrationLabel,
            String narration,
            String postingLabel,
            List<Field> posting,
            String pageLabel,
            String continued) {
        public Source {
            evidence = List.copyOf(evidence);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            totals = List.copyOf(totals);
            posting = List.copyOf(posting);
        }
    }

    private final Document document;
    private final PdfWriter writer;
    private final BaseFont font;
    private final Source source;
    private PdfContentByte stream;
    private float y;
    private int pages;
    private static final Color INK = Color.BLACK,
            MUTED = new Color(70, 70, 70),
            BORDER = new Color(130, 130, 130);

    public JournalStationery(Document document, PdfWriter writer, BaseFont font, Source source) {
        this.document = document;
        this.writer = writer;
        this.font = font;
        this.source = source;
    }

    public void write() {
        installFooter();
        page();
        pair(source.dateLabel(), source.date(), source.referenceLabel(), source.reference());
        for (var field : source.evidence())
            if (!field.value().isBlank()) paragraph(field.label() + ": " + field.value(), 9, MUTED);
        y -= 10;
        tableHeader();
        for (var row : source.rows()) row(row);
        // Original unnumbered writing space never becomes fabricated ledger entries.
        for (int blank = source.rows().size(); blank < 10 && y > 370; blank++) {
            y -= 18;
            rule(y);
        }
        if (y < 245) page();
        y -= 18;
        section(source.wordsLabel());
        paragraph(source.words(), 10, INK);
        for (var total : source.totals()) total(total);
        y -= 10;
        section(source.narrationLabel());
        paragraph(source.narration(), 9, INK);
        section(source.postingLabel());
        for (var field : source.posting()) paragraph(field.label() + ": " + field.value(), 9, INK);
    }

    private void row(Row row) {
        var lines = wrap(row.particulars(), 9, 176);
        var refs = wrap(row.ledger(), 8, 115);
        int offset = 0, count = Math.max(lines.size(), refs.size());
        while (offset < count) {
            if (y < 95) {
                page();
                tableHeader();
            }
            int take = Math.min(count - offset, Math.max(1, (int) ((y - 82) / 13)));
            float height = take * 13 + 14;
            draw(44, y - 14, 9, INK, row.number());
            for (int j = 0; j < take; j++) {
                if (offset + j < lines.size())
                    draw(66, y - 14 - j * 13, 9, INK, lines.get(offset + j));
                if (offset + j < refs.size())
                    draw(253, y - 14 - j * 13, 8, INK, refs.get(offset + j));
            }
            if (offset == 0) {
                amount(463, y - 14, row.debit());
                amount(553, y - 14, row.credit());
            }
            y -= height;
            rule(y);
            offset += take;
        }
    }

    private void page() {
        if (pages++ > 0) document.newPage();
        stream = writer.getDirectContent();
        y = 803;
        var institution = wrap(source.institution(), 13, 246);
        var title = wrap(source.title(), 17, 250);
        for (int n = 0; n < Math.max(institution.size(), title.size()); n++) {
            if (n < institution.size()) draw(36, y, 13, INK, institution.get(n));
            if (n < title.size()) draw(309, y, 17, INK, title.get(n));
            y -= 20;
        }
        paragraph(source.branch(), 9, MUTED);
        draw(36, y, 10, INK, source.numberLabel() + ": " + source.number());
        right(559, y, 9, INK, source.status());
        y -= 20;
        rule(y);
        y -= 18;
        if (pages > 1) {
            draw(36, y, 9, MUTED, source.continued());
            y -= 22;
        }
    }

    private void pair(String leftLabel, String left, String rightLabel, String right) {
        var l = wrap(left, 10, 248);
        var r = wrap(right, 10, 248);
        int height = Math.max(l.size(), r.size());
        if (y - height * 14 - 35 < 65) page();
        draw(36, y, 8, MUTED, leftLabel);
        draw(310, y, 8, MUTED, rightLabel);
        y -= 16;
        for (int n = 0; n < height; n++) {
            if (n < l.size()) draw(36, y, 10, INK, l.get(n));
            if (n < r.size()) draw(310, y, 10, INK, r.get(n));
            y -= 14;
        }
        y -= 13;
    }

    private void tableHeader() {
        stream.setColorFill(new Color(235, 235, 235));
        stream.rectangle(36, y - 38, 523, 38);
        stream.fill();
        draw(44, y - 20, 8, INK, source.columns().get(0));
        draw(66, y - 20, 8, INK, source.columns().get(1));
        draw(253, y - 20, 8, INK, source.columns().get(2));
        right(463, y - 16, 8, INK, source.columns().get(3));
        right(553, y - 16, 8, INK, source.columns().get(4));
        right(463, y - 30, 8, INK, "TZS");
        right(553, y - 30, 8, INK, "TZS");
        y -= 38;
    }

    private void section(String title) {
        if (y < 100) page();
        draw(36, y, 10, INK, title.toUpperCase(Locale.ROOT));
        y -= 8;
        rule(y);
        y -= 18;
    }

    private void total(Field total) {
        var lines = wrap(total.label() + " (TZS)", 9, 95);
        if (y - lines.size() * 13 < 70) page();
        for (int n = 0; n < lines.size(); n++) draw(310, y - n * 13, 9, INK, lines.get(n));
        right(553, y, Math.min(11, 140 * 1000 / font.getWidth(total.value())), INK, total.value());
        y -= lines.size() * 13;
        rule(y + 3);
        y -= 13;
    }

    private void paragraph(String value, float size, Color color) {
        for (var line : wrap(value, size, 523)) {
            if (y < 70) page();
            draw(36, y, size, color, line);
            y -= 14;
        }
        y -= 5;
    }

    private void amount(float x, float top, String value) {
        if (value.isBlank()) return;
        right(x, top, Math.min(8, 80 * 1000 / font.getWidth(value)), INK, value);
    }

    private List<String> wrap(String value, float size, float width) {
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        for (int cp : value.codePoints().toArray()) {
            if (cp == '\n') {
                result.add(line.toString());
                line.setLength(0);
                continue;
            }
            if (Character.isISOControl(cp)) continue;
            String c = font.charExists(cp) ? new String(Character.toChars(cp)) : "?";
            if (!line.isEmpty() && font.getWidth(line + c) * size / 1000 > width) {
                int space = line.lastIndexOf(" ");
                String rest = space > 0 ? line.substring(space + 1) : "";
                result.add(space > 0 ? line.substring(0, space) : line.toString());
                line.setLength(0);
                line.append(rest);
            }
            line.append(c);
        }
        result.add(line.toString());
        return result;
    }

    private void draw(float x, float top, float size, Color color, String value) {
        stream.beginText();
        stream.setColorFill(color);
        stream.setFontAndSize(font, size);
        stream.setTextMatrix(x, top);
        stream.showText(value);
        stream.endText();
    }

    private void right(float x, float top, float size, Color color, String value) {
        draw(x - font.getWidth(value) * size / 1000, top, size, color, value);
    }

    private void rule(float top) {
        stream.setColorStroke(BORDER);
        stream.setLineWidth(.5f);
        stream.moveTo(36, top);
        stream.lineTo(559, top);
        stream.stroke();
    }

    private void installFooter() {
        var footers = new ArrayList<PdfTemplate>();
        writer.setPageEvent(
                new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter writer, Document document) {
                        var footer = writer.getDirectContent().createTemplate(595.27563f, 45);
                        footers.add(footer);
                        writer.getDirectContent().addTemplate(footer, 0, 0);
                    }

                    @Override
                    public void onCloseDocument(PdfWriter writer, Document document) {
                        for (int n = 0; n < footers.size(); n++) {
                            stream = footers.get(n);
                            draw(36, 30, 8, MUTED, source.number() + " | " + source.status());
                            right(
                                    559,
                                    30,
                                    8,
                                    MUTED,
                                    source.pageLabel() + " " + (n + 1) + " / " + footers.size());
                        }
                    }
                });
    }
}
