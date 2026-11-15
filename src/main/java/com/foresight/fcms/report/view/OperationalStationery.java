package com.foresight.fcms.report.view;

import com.foresight.fcms.report.dto.OperationalDocument;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.*;

import java.util.ArrayList;
import java.util.List;

/** Original task/expense/staff FPDF geometry, with lossless continuation for long cells. */
public final class OperationalStationery {
    private static final double MM = 72d / 25.4d;
    private static final double LEFT = 28.35d / MM, CELL_MARGIN = LEFT / 10;
    private final OperationalDocument source;
    private final Document document;
    private final PdfWriter writer;
    private final BaseFont regular, bold, italic, fallback;
    private final double width, height;
    private final List<Double> widths;
    private PdfContentByte ink;
    private double y, headerSize, headerHeight;
    private List<List<String>> headings;
    private final List<PdfTemplate> footers = new ArrayList<>();

    public OperationalStationery(
            OperationalDocument source, Document document, PdfWriter writer, BaseFont fallback)
            throws Exception {
        this.source = source;
        this.document = document;
        this.writer = writer;
        this.fallback = fallback;
        regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
        italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.WINANSI, false);
        width = Double.parseDouble(Float.toString(document.getPageSize().getWidth())) / MM;
        height = Double.parseDouble(Float.toString(document.getPageSize().getHeight())) / MM;
        double sum = source.widths().stream().mapToDouble(Float::doubleValue).sum();
        double scale = Math.min(1, (width - 2 * LEFT) / sum);
        widths = source.widths().stream().map(value -> value * scale).toList();
        prepareHeader();
    }

    public void write() {
        writer.setPageEvent(
                new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        var template =
                                w.getDirectContent()
                                        .createTemplate(
                                                (float) (width * MM), (float) (height * MM));
                        footers.add(template);
                        w.getDirectContent().addTemplate(template, 0, 0);
                    }

                    @Override
                    public void onCloseDocument(PdfWriter w, Document d) {
                        if (source.footer().size() == 0) return;
                        for (int i = 0; i < footers.size(); i++) {
                            ink = footers.get(i);
                            double size = source.footer().size();
                            String alias =
                                    source.pageLabel()
                                            + " "
                                            + (i + 1)
                                            + (source.footer().totalPages() ? "/{nb}" : "");
                            alias += source.footer().suffix();
                            double left =
                                    LEFT
                                            + (width - 2 * LEFT - length(alias, size, italic)) / 2
                                            - CELL_MARGIN;
                            text(
                                    source.pageLabel()
                                            + " "
                                            + (i + 1)
                                            + (source.footer().totalPages()
                                                    ? "/" + footers.size()
                                                    : "")
                                            + source.footer().suffix(),
                                    left,
                                    height - 15,
                                    width - 2 * LEFT,
                                    10,
                                    size,
                                    italic,
                                    "L");
                        }
                    }
                });
        page(false);
        for (int i = 0; i < source.rows().size(); i++)
            row(
                    source.rows().get(i),
                    i == source.rows().size() - 1 && !source.totals().isEmpty() ? 10 : 0,
                    i % 2 == 1
                            && (source.kind().equals("CLIENT_SACCOS")
                                    || source.kind().equals("CLIENT_MICROFINANCE_REGISTER")
                                    || source.kind().equals("CLIENT_OTHERS_REGISTER")));
        if (source.rows().isEmpty() && !source.emptyMessage().isBlank()) {
            double emptyWidth =
                    source.kind().equals("CLIENT_OTHERS_REGISTER")
                            ? source.widths().stream().mapToDouble(Float::doubleValue).sum()
                            : width - 2 * LEFT;
            box(LEFT, y, emptyWidth, 10, false);
            double size =
                    source.kind().equals("CLIENT_SACCOS")
                            ? 7
                            : source.kind().equals("CLIENT_UNIONS_REGISTER") ? 6 : 9;
            text(source.emptyMessage(), LEFT, y, emptyWidth, 10, size, regular, "C");
            y += 10;
        }
        if (!source.totals().isEmpty()) total();
        if (!source.notes().isEmpty()) {
            y += 5;
            for (String note : source.notes())
                for (String line : wrap(note, 8, width - 2 * LEFT, regular)) {
                    if (y + 5 > height - 20) page(false);
                    text(line, LEFT, y, width - 2 * LEFT, 5, 8, regular, "L");
                    y += 5;
                }
        }
    }

    private void page(boolean table) {
        boolean firstPage = ink == null;
        if (!firstPage) document.newPage();
        ink = writer.getDirectContent();
        y = LEFT;
        // Root print_task_report.php: title 10mm, gap 10mm, date range 8mm, gap 2mm.
        if (!source.headings().isEmpty()) {
            for (var heading : source.headings()) {
                if (!firstPage && !heading.repeat()) continue;
                var font = heading.bold() ? bold : regular;
                double headingWidth =
                        source.kind().equals("EXPENSE_CATEGORIES") ? 200 : width - 2 * LEFT;
                for (String line : wrap(heading.text(), heading.size(), headingWidth, font)) {
                    text(line, LEFT, y, headingWidth, heading.height(), heading.size(), font, "C");
                    y += heading.height();
                }
                y += heading.gapAfter();
            }
        } else if (source.kind().equals("TASK")) {
            text(source.title(), LEFT, y, width - 2 * LEFT, 10, 9, bold, "C");
            y += 20;
            period(9, 8, 2, bold);
        } else if (source.kind().equals("EXPENSE")) {
            text(source.title(), LEFT, y, width - 2 * LEFT, 10, 12, bold, "C");
            y += 10;
            period(10, 10, 5, italic);
        } else if (source.kind().equals("BILLING")) {
            for (String line : wrap(source.title(), 16, width - 2 * LEFT, bold)) {
                text(line, LEFT, y, width - 2 * LEFT, 10, 16, bold, "C");
                y += 10;
            }
            y += 5;
        } else if (source.kind().startsWith("CLIENT")) {
            if (!source.kind().equals("CLIENT_SACCOS")
                    && !source.kind().equals("CLIENT_MICROFINANCE_REGISTER")) {
                double titleSize =
                        source.kind().equals("CLIENT_PAYMENT")
                                        || source.kind().equals("CLIENT_SINGLE")
                                ? 16
                                : source.kind().equals("CLIENT_OTHERS_REGISTER")
                                        ? 9
                                        : source.kind().equals("CLIENT_OTHERS_REVENUE") ? 11 : 12;
                for (String line : wrap(source.title(), titleSize, width - 2 * LEFT, bold)) {
                    double titleHeight = source.kind().equals("CLIENT_SINGLE") ? 12 : 10;
                    text(line, LEFT, y, width - 2 * LEFT, titleHeight, titleSize, bold, "C");
                    y += titleHeight;
                }
            }
            if (source.kind().equals("CLIENT_PAYMENT")) y += 5;
            if (!source.period().isBlank()) {
                text(source.period(), LEFT, y, width - 2 * LEFT, 7, 9, bold, "C");
                y += 7;
                if (source.kind().equals("CLIENT_SINGLE")) y += 3;
            }
        } else {
            text(source.title(), LEFT, y, width - 2 * LEFT, 10, 12, bold, "C");
            y += 10;
            period(9, 7, 5, bold);
        }
        if (table || footers.isEmpty()) header();
    }

    private void period(double size, double lineHeight, double gapAfter, BaseFont font) {
        for (String line : wrap(source.period(), size, width - 2 * LEFT, font)) {
            text(line, LEFT, y, width - 2 * LEFT, lineHeight, size, font, "C");
            y += lineHeight;
        }
        y += gapAfter;
    }

    private BaseFont face(String value, BaseFont preferred) {
        return value.codePoints().allMatch(preferred::charExists) ? preferred : fallback;
    }

    private double length(String value, double size, BaseFont font) {
        return face(value, font).getWidth(value) * (double) size / 1000 / MM;
    }

    private void prepareHeader() {
        BaseFont headerFont = headerFont();
        headerSize =
                switch (source.kind()) {
                    case "CLIENT_SACCOS" -> 8;
                    case "BILLING" -> 11;
                    case "CLIENT_PAYMENT", "EXPENSE_CATEGORIES" -> 12;
                    case "CLIENT_SINGLE" -> 10;
                    case "CLIENT_MICROFINANCE_REGISTER",
                            "CLIENT_MICROFINANCE_REVENUE",
                            "CLIENT_OTHERS_REGISTER",
                            "CLIENT_OTHERS_REVENUE" ->
                            9;
                    case "REPORT", "REPORT_STAFF", "REPORT_CLIENT" -> 8;
                    default -> source.kind().startsWith("CLIENT") ? 6 : 10;
                };
        while (headerSize > 5) {
            boolean fits = true;
            for (int i = 0; i < source.columns().size(); i++)
                for (String word : source.columns().get(i).split("\\s+"))
                    if (length(word, headerSize, headerFont) > widths.get(i) - 2 * CELL_MARGIN)
                        fits = false;
            if (fits) break;
            headerSize -= .25f;
        }
        headings = new ArrayList<>();
        headerHeight = 10;
        for (int i = 0; i < source.columns().size(); i++) {
            var lines = headerLines(source.columns().get(i), headerSize, widths.get(i));
            headings.add(lines);
            headerHeight = Math.max(headerHeight, lines.size() * 3.5f + 2);
        }
    }

    private List<String> headerLines(String value, double size, double w) {
        BaseFont headerFont = headerFont();
        var lines = new ArrayList<String>();
        String line = "";
        for (String word : value.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && length(candidate, size, headerFont) > w - 2 * CELL_MARGIN) {
                lines.add(line);
                line = word;
            } else line = candidate;
        }
        lines.add(line);
        return lines.stream().flatMap(item -> wrap(item, size, w, headerFont).stream()).toList();
    }

    private BaseFont headerFont() {
        return source.kind().equals("CLIENT_MICROFINANCE_REVENUE") ? regular : bold;
    }

    private void header() {
        double x = LEFT;
        for (int i = 0; i < headings.size(); i++) {
            double w = widths.get(i);
            box(
                    x,
                    y,
                    w,
                    headerHeight,
                    (source.kind().startsWith("CLIENT")
                                    && !source.kind().equals("CLIENT_PAYMENT")
                                    && !source.kind().equals("CLIENT_MICROFINANCE_REVENUE")
                                    && !source.kind().equals("CLIENT_OTHERS_REVENUE"))
                            || source.kind().startsWith("REPORT"));
            for (int n = 0; n < headings.get(i).size(); n++)
                text(
                        headings.get(i).get(n),
                        x,
                        y + 1 + n * 3.5f,
                        w,
                        3.5f,
                        headerSize,
                        headerFont(),
                        "C");
            x += w;
        }
        y += headerHeight;
    }

    private void row(List<String> values, double following, boolean fill) {
        BaseFont bodyFont = source.kind().equals("CLIENT_OTHERS_REVENUE") ? bold : regular;
        double fontSize =
                switch (source.kind()) {
                    case "CLIENT_SACCOS" -> 7;
                    case "BILLING" -> 10;
                    case "CLIENT_PAYMENT" -> 11;
                    case "EXPENSE_CATEGORIES" -> 12;
                    case "CLIENT_SINGLE" -> 10;
                    case "CLIENT_MICROFINANCE_REGISTER",
                            "CLIENT_MICROFINANCE_REVENUE",
                            "CLIENT_OTHERS_REGISTER",
                            "CLIENT_OTHERS_REVENUE" ->
                            9;
                    case "REPORT", "REPORT_STAFF", "REPORT_CLIENT" -> 8;
                    default -> source.kind().startsWith("CLIENT") ? 6 : 9;
                };
        double lineHeight =
                source.kind().equals("CLIENT_SINGLE")
                        ? 9
                        : source.kind().equals("CLIENT_AMCOS_REGISTER")
                                        || (source.kind().endsWith("REVENUE")
                                                && !source.kind().contains("MICROFINANCE")
                                                && !source.kind().contains("OTHERS"))
                                        || source.kind().equals("CLIENT_UNIONS_REGISTER")
                                ? 8
                                : 10;
        var lines = new ArrayList<List<String>>();
        int count = 1;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            // Original centered Cell values may use their cell margins. Preserve that fit;
            // longer/multiline values still use lossless continuation within the cell.
            boolean cellFit =
                    source.kind().startsWith("CLIENT")
                            && source.aligns().get(i).equals("C")
                            && !value.contains("\n")
                            && !value.contains("\r")
                            && length(value, fontSize, bodyFont) <= widths.get(i) - .2;
            var wrapped = cellFit ? List.of(value) : wrap(value, fontSize, widths.get(i), bodyFont);
            lines.add(wrapped);
            count = Math.max(count, wrapped.size());
        }
        double full = count * lineHeight + following;
        if (y + full > height - 20 && full < height - 70) page(true);
        int offset = 0;
        while (offset < count) {
            if (y + lineHeight > height - 20) page(true);
            int take =
                    Math.min(count - offset, Math.max(1, (int) ((height - 20 - y) / lineHeight)));
            double x = LEFT;
            for (int i = 0; i < values.size(); i++) {
                double w = widths.get(i);
                box(x, y, w, take * lineHeight, fill, "1.000 1.000 1.000 rg\n");
                for (int n = 0; n < take && offset + n < lines.get(i).size(); n++)
                    text(
                            lines.get(i).get(offset + n),
                            x,
                            y + n * lineHeight,
                            w,
                            lineHeight,
                            fontSize,
                            bodyFont,
                            source.aligns().get(i));
                x += w;
            }
            y += take * lineHeight;
            offset += take;
        }
    }

    private void total() {
        if (y + 10 > height - 20) page(true);
        if (source.kind().startsWith("CLIENT") || source.kind().equals("BILLING")) {
            boolean largeRevenue =
                    source.kind().equals("CLIENT_MICROFINANCE_REVENUE")
                            || source.kind().equals("CLIENT_OTHERS_REVENUE");
            double totalHeight = source.kind().endsWith("REVENUE") && !largeRevenue ? 8 : 10;
            int firstAmount = 1;
            while (firstAmount < source.totals().size()
                    && source.totals().get(firstAmount).isBlank()) firstAmount++;
            double labelWidth =
                    widths.subList(0, firstAmount).stream().mapToDouble(Double::doubleValue).sum();
            double x = LEFT;
            boolean fill =
                    !source.kind().equals("CLIENT_PAYMENT") && !source.kind().equals("BILLING");
            double fontSize =
                    source.kind().equals("BILLING")
                            ? 11
                            : source.kind().equals("CLIENT_PAYMENT")
                                    ? 12
                                    : source.kind().equals("CLIENT_SINGLE")
                                            ? 10
                                            : largeRevenue
                                                    ? 12
                                                    : source.kind().equals("CLIENT_SACCOS") ? 7 : 6;
            String fillColor =
                    source.kind().equals("CLIENT_SACCOS") ? "0.902 0.902 0.902 rg\n" : null;
            box(x, y, labelWidth, totalHeight, fill, fillColor);
            text(source.totals().getFirst(), x, y, labelWidth, totalHeight, fontSize, bold, "R");
            if (largeRevenue) fontSize = 11;
            x += labelWidth;
            for (int i = firstAmount; i < source.totals().size(); i++) {
                double w = widths.get(i);
                String value = source.totals().get(i);
                box(x, y, w, totalHeight, fill, fillColor);
                double size =
                        Math.min(
                                fontSize,
                                (w - 2)
                                        * MM
                                        * 1000
                                        / Math.max(1, face(value, bold).getWidth(value)));
                text(value, x, y, w, totalHeight, size, bold, "C");
                x += w;
            }
            y += totalHeight;
            return;
        }
        int amounts = source.totals().size() - 1;
        int start = widths.size() - amounts;
        double labelWidth = 0;
        for (int i = 0; i < start; i++) labelWidth += widths.get(i);
        double x = LEFT;
        for (int i = 0; i < source.totals().size(); i++) {
            double w = i == 0 ? labelWidth : widths.get(start + i - 1);
            box(x, y, w, 10, true);
            String value = source.totals().get(i);
            double size = source.kind().equals("TASK") ? 11 : 9;
            size =
                    Math.min(
                            size,
                            (w - 2) * MM * 1000 / Math.max(1, face(value, bold).getWidth(value)));
            text(value, x, y, w, 10, size, bold, i == 0 || amounts == 1 ? "R" : "C");
            x += w;
        }
        y += 10;
    }

    private List<String> wrap(String value, double size, double w, BaseFont font) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        String normalized = value.replace("\r", "");
        if (normalized.endsWith("\n"))
            normalized = normalized.substring(0, normalized.length() - 1);
        for (int cp : normalized.codePoints().toArray()) {
            if (cp == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            if (Character.isISOControl(cp)) continue;
            String next = new String(Character.toChars(cp));
            if (!line.isEmpty() && length(line + next, size, font) > w - 2 * CELL_MARGIN) {
                int space = line.lastIndexOf(" ");
                lines.add(space > 0 ? line.substring(0, space) : line.toString());
                String rest = space > 0 ? line.substring(space + 1) : "";
                line.setLength(0);
                line.append(rest);
            }
            line.append(next);
        }
        lines.add(line.toString());
        return lines;
    }

    private void box(double x, double top, double w, double h, boolean fill) {
        box(x, top, w, h, fill, null);
    }

    private void box(double x, double top, double w, double h, boolean fill, String fillColor) {
        // FPDF writes two-decimal geometry, a .2mm stroke and square caps. Keep those
        // operators and the original three-component gray fill unchanged.
        ink.setLiteral("0 G\n0.57 w\n2 J\n");
        if (fillColor != null) ink.setLiteral(fillColor);
        else if (source.kind().startsWith("REPORT")) ink.setLiteral("1.000 1.000 1.000 rg\n");
        else if (source.kind().equals("CLIENT_SACCOS")
                || source.kind().equals("CLIENT_MICROFINANCE_REGISTER")
                || source.kind().equals("CLIENT_OTHERS_REGISTER"))
            ink.setLiteral("1.000 0.647 0.000 rg\n");
        else {
            double shade =
                    source.kind().startsWith("CLIENT")
                            ? 211d / 255
                            : source.kind().equals("TASK") ? 192d / 255 : 200d / 255;
            ink.setLiteral(
                    String.format(
                            java.util.Locale.ROOT, "%.3f %.3f %.3f rg\n", shade, shade, shade));
        }
        ink.setLiteral(
                String.format(
                        java.util.Locale.ROOT,
                        "%s %s %s %s re %s\n",
                        coordinate(x * MM),
                        coordinate((height - top) * MM),
                        coordinate(w * MM),
                        coordinate(-h * MM),
                        fill ? "B" : "S"));
    }

    private String coordinate(double value) {
        // FPDF's sprintf uses nearest/even rounding of the binary double. Java Formatter
        // rounds decimal ties up, which moves some centered captions by .01pt.
        return new java.math.BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_EVEN)
                .toPlainString();
    }

    private void text(
            String value,
            double x,
            double top,
            double w,
            double h,
            double size,
            BaseFont preferred,
            String align) {
        BaseFont font = face(value, preferred);
        double left =
                align.equals("R")
                        ? x + w - CELL_MARGIN - length(value, size, font)
                        : align.equals("C")
                                ? x + (w - length(value, size, font)) / 2
                                : x + CELL_MARGIN;
        ink.beginText();
        ink.setLiteral("0 g\n");
        ink.setFontAndSize(font, (float) size);
        ink.setLiteral(
                String.format(
                        java.util.Locale.ROOT,
                        "1 0 0 1 %s %s Tm\n",
                        coordinate(left * MM),
                        coordinate((height - top - h / 2d) * MM - .3d * size)));
        ink.showText(value);
        ink.endText();
    }
}
