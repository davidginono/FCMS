package com.foresight.fcms.report.view;

import com.foresight.fcms.report.dto.LegacyLedgerDocument;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

/** FPDF ledger-report millimetre geometry ported to the installed OpenPDF renderer. */
@Component
public final class LegacyLedgerStationery {
    private final Semaphore permits = new Semaphore(2);

    public byte[] render(LegacyLedgerDocument source) {
        if (source.rows().size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        if (!permits.tryAcquire()) throw new IllegalArgumentException("error.exportBusy");
        try (var out = new ByteArrayOutputStream();
                var bytes =
                        new ClassPathResource("report-fonts/NotoSans-Regular.ttf")
                                .getInputStream()) {
            var fallback =
                    BaseFont.createFont(
                            "NotoSans-Regular.ttf",
                            BaseFont.IDENTITY_H,
                            true,
                            false,
                            bytes.readAllBytes(),
                            null);
            boolean portrait = source.kind().equals("CLOUD");
            var document =
                    new Document(
                            portrait
                                    ? new Rectangle(595.28f, 841.89f)
                                    : new Rectangle(841.89f, 595.28f));
            var writer = PdfWriter.getInstance(document, out);
            document.addTitle(source.title());
            document.addCreator("FCMS");
            document.open();
            new Layout(source, document, writer, fallback).write();
            document.close();
            return out.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("Ledger report generation failed", failure);
        } finally {
            permits.release();
        }
    }

    private static final class Layout {
        private static final float MM = 72 / 25.4f;
        private final LegacyLedgerDocument source;
        private final Document document;
        private final PdfWriter writer;
        private final BaseFont normal, bold, italic, fallback;
        private final float height, width;
        private PdfContentByte ink;
        private float y;
        private int pages;
        private float headerSize, headerHeight;
        private List<List<String>> headings;

        Layout(LegacyLedgerDocument source, Document document, PdfWriter writer, BaseFont fallback)
                throws Exception {
            this.source = source;
            this.document = document;
            this.writer = writer;
            this.fallback = fallback;
            normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
            italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.WINANSI, false);
            height = document.getPageSize().getHeight() / MM;
            width = document.getPageSize().getWidth() / MM;
            prepareHeader();
            writer.setPageEvent(
                    new PdfPageEventHelper() {
                        @Override
                        public void onEndPage(PdfWriter w, Document d) {
                            ink = w.getDirectContent();
                            text(
                                    source.kind().equals("CLOUD")
                                            ? source.pageLabel() + " " + w.getPageNumber()
                                            : source.printed(),
                                    10,
                                    height - 15,
                                    width - 20,
                                    10,
                                    8,
                                    italic,
                                    source.kind().equals("CLOUD") ? "C" : "R");
                        }
                    });
        }

        void write() {
            page(false);
            text(source.title(), 10, y, width - 20, 10, 12, bold, "C");
            y += 10;
            text(source.printed(), 10, y, width - 20, 10, 10, normal, "R");
            y += 10;
            for (var line : source.preface()) {
                var font = line.bold() ? bold : normal;
                for (var value : wrap(line.text(), line.bold() ? 10 : 9, width - 20, font)) {
                    text(value, 10, y, width - 20, line.height(), line.bold() ? 10 : 9, font, "L");
                    y += line.height();
                }
            }
            y += source.kind().equals("BULK") ? 5 : 10;
            header();
            float totalHeight = 0;
            for (var total : source.totals()) totalHeight += total.gap() + 7;
            for (int i = 0; i < source.rows().size(); i++)
                row(source.rows().get(i), i == source.rows().size() - 1 ? totalHeight : 0);
            for (var total : source.totals()) {
                y += total.gap();
                if (y + 7 > height - 15) page(false);
                float x = 10;
                for (int i = 0; i < total.cells().size(); i++) {
                    float cellWidth = total.widths().get(i);
                    String value = total.cells().get(i);
                    box(x, y, cellWidth, 7);
                    float size =
                            value.matches("[-0-9.,]+")
                                    ? Math.min(
                                            10,
                                            (cellWidth - 2)
                                                    * MM
                                                    * 1000
                                                    / face(value, bold).getWidth(value))
                                    : 10;
                    text(value, x, y, cellWidth, 7, size, bold, "R");
                    x += cellWidth;
                }
                y += 7;
            }
            if (!source.notes().isEmpty()) {
                y += 5;
                for (String note : source.notes())
                    for (String value : wrap(note, 9, width - 20, normal)) {
                        if (y + 5 > height - 15) page(false);
                        text(value, 10, y, width - 20, 5, 9, normal, "L");
                        y += 5;
                    }
            }
        }

        private void page(boolean table) {
            if (pages++ > 0) document.newPage();
            ink = writer.getDirectContent();
            y = 10;
            if (table) header();
        }

        private BaseFont face(String value, BaseFont preferred) {
            return value.codePoints().allMatch(preferred::charExists) ? preferred : fallback;
        }

        private float length(String text, float size, BaseFont font) {
            return face(text, font).getWidth(text) * size / 1000 / MM;
        }

        private void prepareHeader() {
            headerSize = 10;
            while (headerSize > 5) {
                boolean fits = true;
                for (int i = 0; i < source.columns().size(); i++)
                    for (String word : source.columns().get(i).trim().split("\\s+"))
                        if (length(word, headerSize, bold) > source.widths().get(i) - 2)
                            fits = false;
                if (fits) break;
                headerSize = Math.max(5, headerSize - .25f);
            }
            headings = new ArrayList<>();
            headerHeight = 10;
            for (int i = 0; i < source.columns().size(); i++) {
                var lines = new ArrayList<String>();
                String line = "";
                for (String word : source.columns().get(i).trim().split("\\s+")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty()
                            && length(candidate, headerSize, bold) > source.widths().get(i) - 2) {
                        lines.add(line);
                        line = word;
                    } else line = candidate;
                }
                lines.add(line);
                headings.add(lines);
                headerHeight = Math.max(headerHeight, lines.size() * 3.5f + 2);
            }
        }

        private void header() {
            if (y + headerHeight > height - 15) page(false);
            float x = 10;
            for (int i = 0; i < headings.size(); i++) {
                float w = source.widths().get(i);
                box(x, y, w, headerHeight);
                for (int n = 0; n < headings.get(i).size(); n++)
                    text(
                            headings.get(i).get(n),
                            x,
                            y + 1 + n * 3.5f,
                            w,
                            3.5f,
                            headerSize,
                            bold,
                            "C");
                x += w;
            }
            y += headerHeight;
        }

        private void row(List<String> values, float followingHeight) {
            var lines = new ArrayList<List<String>>();
            int count = 1;
            for (int i = 0; i < values.size(); i++) {
                var wrapped = wrap(values.get(i), 9, source.widths().get(i), normal);
                lines.add(wrapped);
                count = Math.max(count, wrapped.size());
            }
            float together = count * 7 + followingHeight;
            if (y + together > height - 15 && together <= height - 25 - headerHeight) page(true);
            int offset = 0;
            while (offset < count) {
                if (y + 7 > height - 15) page(true);
                int take = Math.min(count - offset, Math.max(1, (int) ((height - 15 - y) / 7)));
                float x = 10;
                for (int i = 0; i < values.size(); i++) {
                    float w = source.widths().get(i);
                    box(x, y, w, take * 7);
                    for (int n = 0; n < take && offset + n < lines.get(i).size(); n++)
                        text(
                                lines.get(i).get(offset + n),
                                x,
                                y + n * 7,
                                w,
                                7,
                                9,
                                normal,
                                source.aligns().get(i));
                    x += w;
                }
                y += take * 7;
                offset += take;
            }
        }

        private List<String> wrap(String raw, float size, float cellWidth, BaseFont font) {
            String value = raw.replace("\r", "");
            if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
            var result = new ArrayList<String>();
            var line = new StringBuilder();
            float limit = cellWidth - 2;
            for (int cp : value.codePoints().toArray()) {
                if (cp == '\n') {
                    result.add(line.toString());
                    line.setLength(0);
                    continue;
                }
                if (Character.isISOControl(cp)) continue;
                String next = new String(Character.toChars(cp));
                if (!fallback.charExists(cp)) next = "?";
                if (!line.isEmpty() && length(line + next, size, font) > limit) {
                    int space = line.lastIndexOf(" ");
                    result.add(space >= 0 ? line.substring(0, space) : line.toString());
                    String rest = space >= 0 ? line.substring(space + 1) : "";
                    line.setLength(0);
                    line.append(rest);
                }
                line.append(next);
            }
            result.add(line.toString());
            return result;
        }

        private void box(float x, float top, float w, float h) {
            ink.setGrayStroke(0);
            ink.setLineWidth(.2f * MM);
            ink.rectangle(x * MM, (height - top - h) * MM, w * MM, h * MM);
            ink.stroke();
        }

        private void text(
                String value,
                float x,
                float top,
                float w,
                float h,
                float size,
                BaseFont preferred,
                String align) {
            BaseFont font = face(value, preferred);
            float left =
                    align.equals("R")
                            ? x + w - 1 - length(value, size, font)
                            : align.equals("C") ? x + (w - length(value, size, font)) / 2 : x + 1;
            ink.beginText();
            ink.setGrayFill(0);
            ink.setFontAndSize(font, size);
            ink.setTextMatrix(left * MM, (height - top - h / 2) * MM - .3f * size);
            ink.showText(value);
            ink.endText();
        }
    }
}
