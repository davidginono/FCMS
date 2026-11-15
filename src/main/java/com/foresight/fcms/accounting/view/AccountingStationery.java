package com.foresight.fcms.accounting.view;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;

import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** Microfinance AccountingPdfDocument statement, equity and trial geometry ported to OpenPDF. */
public final class AccountingStationery {
    public enum Style {
        STATEMENT,
        EQUITY,
        TRIAL
    }

    private final Document document;
    private final BaseFont font;
    private final Rectangle size;
    private final String title;
    private final List<String> context;
    private final Style style;
    private final PdfWriter writer;
    private final DecimalFormat coordinates =
            new DecimalFormat("0.#####", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private int pages;
    private final BaseFont bodyFont, headingFont, titleFont;
    private PdfContentByte stream;
    private float y;
    private List<String> headers = List.of();
    private float[] weights = new float[0];
    private boolean[] numeric = new boolean[0];

    public AccountingStationery(
            Document document,
            PdfWriter writer,
            BaseFont font,
            String title,
            List<String> context,
            Style style)
            throws IOException {
        this.document = document;
        this.writer = writer;
        this.font = font;
        this.title = title;
        this.context = context;
        this.style = style;
        coordinates.setRoundingMode(RoundingMode.HALF_UP);
        size = document.getPageSize();
        bodyFont =
                standard(
                        style == Style.TRIAL
                                ? BaseFont.TIMES_ROMAN
                                : style == Style.EQUITY ? BaseFont.COURIER : BaseFont.HELVETICA);
        headingFont =
                standard(
                        style == Style.TRIAL
                                ? BaseFont.TIMES_BOLD
                                : style == Style.EQUITY
                                        ? BaseFont.COURIER_BOLD
                                        : BaseFont.HELVETICA_BOLD);
        titleFont =
                standard(
                        style == Style.TRIAL
                                ? BaseFont.TIMES_ROMAN
                                : style == Style.EQUITY
                                        ? BaseFont.HELVETICA_BOLD
                                        : BaseFont.HELVETICA);
        installFooter();
        next();
    }

    private BaseFont standard(String name) throws IOException {
        try {
            return BaseFont.createFont(name, BaseFont.WINANSI, false);
        } catch (com.lowagie.text.DocumentException failure) {
            throw new IOException(failure);
        }
    }

    public float width() {
        return size.getWidth() - (style == Style.EQUITY ? 40 : 64);
    }

    private float tableLeft() {
        return style == Style.EQUITY ? 20 : 32;
    }

    private void next() throws IOException {
        if (pages++ > 0) document.newPage();
        stream = writer.getDirectContent();
        if (style == Style.EQUITY) {
            y = size.getHeight() - 22;
            for (String part : wrap(title, 14, width() * .546f, titleFont)) {
                float titleWidth = face(part, titleFont).getWidth(part) * 14f / 1000;
                drawWith(
                        part,
                        tableLeft() + width() * .273f - titleWidth / 2,
                        y,
                        14,
                        titleFont,
                        false,
                        false,
                        true);
                y -= 19;
            }
            for (String part : wrap(context.getFirst(), 9, width(), headingFont)) {
                draw(part, tableLeft(), y, 9, true, false);
                y -= 12;
            }
            y -= 24;
            if (!headers.isEmpty()) header();
            return;
        }
        y = size.getHeight() - 36;
        float left = 32, headingWidth = size.getWidth() - 32 - left;
        for (String line : wrap(title, 14, headingWidth)) {
            float titleWidth = face(line, titleFont).getWidth(line) * 14f / 1000,
                    x = left + (headingWidth - titleWidth) / 2;
            drawWith(line, x, y, 14, titleFont, false, false, false);
            if (style == Style.TRIAL) line(x, x + titleWidth, y - 2);
            y -= 19;
        }
        y -= 3;
        for (String value : context)
            for (String line : wrap(value, 8, headingWidth)) {
                draw(line, left, y, 8, false, false);
                y -= 12;
            }
        y -= 12;
        if (!headers.isEmpty()) header();
    }

    public void columns(List<String> labels, float[] weights, boolean[] numeric)
            throws IOException {
        this.headers = List.copyOf(labels);
        this.weights = weights.clone();
        this.numeric = numeric.clone();
        if (labels.size() != weights.length || labels.size() != numeric.length)
            throw new IllegalArgumentException("Column mismatch");
        header();
    }

    private void header() throws IOException {
        var lines = new ArrayList<List<String>>();
        int count = 1;
        float point = style == Style.EQUITY ? 9 : 8;
        for (int i = 0; i < headers.size(); i++) {
            var value = wrap(headers.get(i), point, width() * weights[i] - 10);
            lines.add(value);
            count = Math.max(count, value.size());
        }
        if (y - count * 12 - 10 < 55) nextWithoutHeader();
        float x = tableLeft();
        for (int i = 0; i < lines.size(); i++) {
            for (int n = 0; n < lines.get(i).size(); n++) {
                String label = lines.get(i).get(n);
                float edge = numeric[i] ? x + width() * weights[i] - 5 : x;
                if (style == Style.EQUITY) {
                    float length = face(label, headingFont).getWidth(label) * point / 1000;
                    drawWith(
                            label,
                            Math.max(x, x + width() * weights[i] / 2 - length / 2),
                            y - n * 12,
                            point,
                            headingFont,
                            false,
                            false,
                            false);
                } else draw(label, edge, y - n * 12, point, true, numeric[i]);
                if (style == Style.TRIAL) {
                    float length = face(label, headingFont).getWidth(label) * 8f / 1000;
                    line(
                            numeric[i] ? edge - length : edge,
                            numeric[i] ? edge : edge + length,
                            y - n * 12 - 2);
                }
            }
            x += width() * weights[i];
        }
        y -= count * 12 + (style == Style.EQUITY ? 32 : 8);
    }

    private void nextWithoutHeader() throws IOException {
        var saved = headers;
        headers = List.of();
        next();
        headers = saved;
    }

    public void row(List<String> values, boolean total) throws IOException {
        if (style == Style.EQUITY && total) y -= 10;
        var lines = new ArrayList<List<String>>();
        var points = new ArrayList<Float>();
        int count = 1;
        float leading = style == Style.EQUITY ? 12 : 13;
        for (int i = 0; i < values.size(); i++) {
            float max = width() * weights[i] - 10,
                    point = style == Style.EQUITY ? 9 : numeric[i] ? 8 : 9;
            if (numeric[i] && values.get(i).matches("[0-9.,()\\-]+"))
                point =
                        Math.min(
                                point,
                                (max - 1)
                                        * 1000
                                        / face(values.get(i), bodyFont).getWidth(values.get(i)));
            points.add(point);
            var value = wrap(values.get(i), point, max);
            lines.add(value);
            count = Math.max(count, value.size());
        }
        int offset = 0;
        if (y - Math.min(count * leading + 10, 100) < 55) next();
        while (offset < count) {
            if (y < 70) next();
            int take = Math.min(count - offset, Math.max(1, (int) ((y - 55) / leading)));
            float x = tableLeft();
            for (int i = 0; i < lines.size(); i++) {
                for (int n = 0; n < take && offset + n < lines.get(i).size(); n++)
                    draw(
                            lines.get(i).get(offset + n),
                            numeric[i] ? x + width() * weights[i] - 5 : x,
                            y - n * leading,
                            points.get(i),
                            total || style == Style.EQUITY && i == 0,
                            numeric[i]);
                x += width() * weights[i];
            }
            y -= take * leading;
            offset += take;
        }
        if (total) {
            float x = tableLeft();
            for (int i = 0; i < values.size(); i++) {
                if (numeric[i] && !values.get(i).isBlank())
                    line(
                            x
                                    + width() * weights[i]
                                    - 5
                                    - face(values.get(i), headingFont).getWidth(values.get(i))
                                            * points.get(i)
                                            / 1000,
                            x + width() * weights[i] - 5,
                            y + leading - 2);
                x += width() * weights[i];
            }
            y -= 5;
        }
    }

    public void text(String value) throws IOException {
        for (String line : wrap(value, 9, width())) {
            if (y < 65) next();
            draw(line, tableLeft(), y, 9, false, false);
            y -= 13;
        }
        y -= 4;
    }

    public void section(String value) throws IOException {
        if (y < 90) next();
        y -= 16;
        for (String line : wrap(value, 10, width())) {
            if (y < 65) next();
            draw(line, tableLeft(), y, 10, true, false);
            y -= 14;
        }
        y -= 4;
    }

    /**
     * End table before notes/provenance, so subsequent pages do not repeat an unrelated column
     * header.
     */
    public void endTable() {
        headers = List.of();
    }

    /** Keep provenance intact on its own page, leaving the financial face uncluttered. */
    public void appendix(String title) throws IOException {
        endTable();
        next();
        section(title);
    }

    private void line(float left, float right, float top) throws IOException {
        stream.setGrayStroke(0);
        stream.setLineWidth(.5f);
        stream.setLiteral(coordinates.format(left) + " " + coordinates.format(top) + " m\n");
        stream.setLiteral(coordinates.format(right) + " " + coordinates.format(top) + " l\n");
        stream.stroke();
    }

    private BaseFont face(String value, BaseFont preferred) throws IOException {
        return value.codePoints().allMatch(preferred::charExists) ? preferred : font;
    }

    private void draw(String value, float x, float top, float point, boolean bold, boolean right)
            throws IOException {
        drawWith(value, x, top, point, bold ? headingFont : bodyFont, false, right, false);
    }

    private void drawWith(
            String value,
            float x,
            float top,
            float point,
            BaseFont preferred,
            boolean bold,
            boolean right,
            boolean red)
            throws IOException {
        BaseFont selected = face(value, preferred);
        if (right) x -= selected.getWidth(value) * point / 1000;
        stream.beginText();
        if (red) stream.setRGBColorFill(255, 0, 0);
        else stream.setGrayFill(0);
        stream.setGrayStroke(0);
        stream.setLineWidth(.22f);
        stream.setTextRenderingMode(
                bold
                        ? PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE
                        : PdfContentByte.TEXT_RENDER_MODE_FILL);
        int fontStart = stream.getInternalBuffer().size();
        stream.setFontAndSize(selected, point);
        if (point != Math.rint(point)) {
            var content = stream.getInternalBuffer();
            String command =
                    new String(
                            content.getBuffer(),
                            fontStart,
                            content.size() - fontStart,
                            java.nio.charset.StandardCharsets.ISO_8859_1);
            content.setSize(fontStart);
            content.append(command.substring(0, command.indexOf(' ') + 1))
                    .append(coordinates.format(point))
                    .append(" Tf\n");
        }
        // The original PDFBox renderer retains five decimals; OpenPDF's default matrix rounds to
        // two.
        stream.setLiteral(
                "1 0 0 1 " + coordinates.format(x) + " " + coordinates.format(top) + " Tm\n");
        stream.showText(value);
        stream.endText();
    }

    private List<String> wrap(String value, float point, float max) throws IOException {
        return wrap(value, point, max, bodyFont);
    }

    private List<String> wrap(String value, float point, float max, BaseFont preferred)
            throws IOException {
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        for (int cp : value.codePoints().toArray()) {
            if (cp == '\n') {
                result.add(line.toString());
                line.setLength(0);
                continue;
            }
            if (Character.isISOControl(cp)) continue;
            String c = new String(Character.toChars(cp));
            if (!font.charExists(cp)) c = "?";
            if (!line.isEmpty()
                    && face(line + c, preferred).getWidth(line + c) * point / 1000 > max) {
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

    private void installFooter() {
        var footers = new ArrayList<PdfTemplate>();
        writer.setPageEvent(
                new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        var footer = w.getDirectContent().createTemplate(size.getWidth(), 40);
                        footers.add(footer);
                        w.getDirectContent().addTemplate(footer, 0, 0);
                    }

                    @Override
                    public void onCloseDocument(PdfWriter w, Document d) {
                        for (int n = 0; n < footers.size(); n++) {
                            stream = footers.get(n);
                            try {
                                draw(
                                        (n + 1) + " / " + footers.size(),
                                        size.getWidth() - 32,
                                        24,
                                        8,
                                        false,
                                        true);
                            } catch (IOException failure) {
                                throw new IllegalStateException(
                                        "Statement footer generation failed", failure);
                            }
                        }
                    }
                });
    }
}
