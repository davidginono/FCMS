package com.foresight.fcms.sales.service;

import com.foresight.fcms.sales.dto.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Coordinate-for-coordinate port of FCMS invoice_pdf.php using its original artwork. */
@Service
public class SalesPdfService {
    private final Semaphore renders = new Semaphore(2);

    public byte[] render(SalesDocument source) {
        if (!renders.tryAcquire()) throw new IllegalArgumentException("error.exportBusy");
        try (var out = new ByteArrayOutputStream()) {
            var page = new Canvas(source, out);
            page.start();
            page.billing();
            page.itemsHeader();
            int number = 0;
            for (var item : source.data().getItems())
                page.item(++number, item.getDescription(), page.money(item.getAmount()));
            page.closing();
            page.finish();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Sales document generation failed", e);
        } finally {
            renders.release();
        }
    }

    private static class Canvas {
        static final float MM = 72f / 25.4f, H = 249;
        final SalesDocument source;
        final SalesForm data;
        final boolean proforma;
        final Document document;
        final PdfWriter writer;
        final BaseFont regular, bold, italic, boldItalic, unicode;
        final Image logo, stamp;
        final DecimalFormat money =
                new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        PdfContentByte cb;
        float y, tableBottom, wordSpacing;
        boolean itemsStarted;
        final Color blue = new Color(49, 82, 238), boxBlue = new Color(35, 35, 220);

        Canvas(SalesDocument source, OutputStream out) throws IOException, DocumentException {
            this.source = source;
            this.data = source.data();
            proforma = source.kind().equals("PROSPECT");
            document = new Document(new Rectangle(210 * MM, H * MM), 0, 0, 0, 0);
            writer = PdfWriter.getInstance(document, out);
            regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
            italic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.WINANSI, false);
            boldItalic =
                    BaseFont.createFont(BaseFont.HELVETICA_BOLDOBLIQUE, BaseFont.WINANSI, false);
            try (var f =
                    new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()) {
                unicode =
                        BaseFont.createFont(
                                "NotoSans-Regular.ttf",
                                BaseFont.IDENTITY_H,
                                true,
                                false,
                                f.readAllBytes(),
                                null);
            }
            try (var l =
                            new ClassPathResource("sales-stationery/invoice-company-logo.png")
                                    .getInputStream();
                    var s =
                            new ClassPathResource("sales-stationery/company-stamp.png")
                                    .getInputStream()) {
                logo = Image.getInstance(l.readAllBytes());
                stamp = Image.getInstance(s.readAllBytes());
            }
            document.addTitle(proforma ? "Proforma Invoice" : "Invoice");
            document.addAuthor("Computer Resources (T) Limited");
            document.addCreator("FCMS");
        }

        String money(BigDecimal amount) {
            money.setMinimumFractionDigits(amount.remainder(BigDecimal.ONE).signum() == 0 ? 0 : 2);
            return money.format(amount);
        }

        BaseFont face(String text, BaseFont requested) {
            return text.codePoints().allMatch(requested::charExists) ? requested : unicode;
        }

        float width(String text, BaseFont requested, float size) {
            return face(text, requested).getWidthPoint(text, size) / MM;
        }

        void line(float x1, float y1, float x2, float y2, float thickness, Color color) {
            cb.setLineWidth(thickness * MM);
            cb.setColorStroke(color);
            cb.moveTo(x1 * MM, (H - y1) * MM);
            cb.lineTo(x2 * MM, (H - y2) * MM);
            cb.stroke();
        }

        void rect(
                float x,
                float top,
                float w,
                float h,
                Color fill,
                Color stroke,
                float thickness,
                boolean dashed) {
            cb.saveState();
            cb.setLineWidth(thickness * MM);
            if (dashed) cb.setLineDash(.7f * MM, .45f * MM, 0);
            if (fill != null) cb.setColorFill(fill);
            if (stroke != null) cb.setColorStroke(stroke);
            cb.rectangle(x * MM, (H - top - h) * MM, w * MM, h * MM);
            if (fill != null && stroke != null) cb.fillStroke();
            else if (fill != null) cb.fill();
            else cb.stroke();
            cb.restoreState();
        }

        void round(float x, float top, float w, float h, float radius) {
            cb.setColorStroke(boxBlue);
            cb.setLineWidth(.35f * MM);
            cb.roundRectangle(x * MM, (H - top - h) * MM, w * MM, h * MM, radius * MM);
            cb.stroke();
        }

        void cell(
                float x,
                float top,
                float w,
                float h,
                String value,
                BaseFont font,
                float size,
                int align,
                Color color) {
            if (value == null || value.isEmpty()) return;
            var f = face(value, font);
            float contentWidth = f.getWidthPoint(value, size) / MM;
            float left =
                    align == Element.ALIGN_RIGHT
                            ? x + w - 1 - contentWidth
                            : align == Element.ALIGN_CENTER ? x + (w - contentWidth) / 2 : x + 1;
            cb.beginText();
            cb.setColorFill(color);
            cb.setFontAndSize(f, size);
            cb.setTextMatrix(left * MM, (H - top - h / 2) * MM - .3f * size);
            cb.setWordSpacing(wordSpacing * MM);
            cb.showText(value);
            cb.setWordSpacing(0);
            cb.endText();
        }

        List<String> lines(String text, float w, BaseFont font, float size) {
            var result = new ArrayList<String>();
            String value = text.replace("\r", "");
            if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
            for (String paragraph : value.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    result.add("");
                    continue;
                }
                int start = 0;
                while (start < paragraph.length()) {
                    int end = start, lastSpace = -1;
                    while (end < paragraph.length()) {
                        int next = end + Character.charCount(paragraph.codePointAt(end));
                        if (width(paragraph.substring(start, next), font, size) > w - 2) break;
                        if (paragraph.charAt(end) == ' ') lastSpace = end;
                        end = next;
                    }
                    if (end == paragraph.length()) {
                        result.add(paragraph.substring(start));
                        break;
                    }
                    if (lastSpace >= start) {
                        result.add(paragraph.substring(start, lastSpace));
                        start = lastSpace + 1;
                    } else {
                        if (end == start)
                            end = start + Character.charCount(paragraph.codePointAt(start));
                        result.add(paragraph.substring(start, end));
                        start = end;
                    }
                }
            }
            return result;
        }

        float multi(
                float x,
                float top,
                float w,
                float h,
                String value,
                BaseFont font,
                float size,
                int align) {
            for (String part : lines(value, w, font, size)) {
                cell(x, top, w, h, part, font, size, align, Color.BLACK);
                top += h;
            }
            return top;
        }

        void image(Image original, float x, float top, float w) throws DocumentException {
            Image copy = Image.getInstance(original);
            float h = w * copy.getHeight() / copy.getWidth();
            copy.scaleAbsolute(w * MM, h * MM);
            copy.setAbsolutePosition(x * MM, (H - top - h) * MM);
            cb.addImage(copy);
        }

        void ribbon(float top, float left, Float right) {
            for (float at : new float[] {top - .45f, top, top + .45f}) {
                line(6, at, left, at, .35f, blue);
                if (right != null) line(right, at, 203, at, .35f, blue);
            }
        }

        void start() throws DocumentException {
            document.open();
            cb = writer.getDirectContent();
            header();
        }

        void next() throws DocumentException {
            footer();
            document.newPage();
            cb = writer.getDirectContent();
            header();
            if (itemsStarted) {
                billing();
                itemsHeader();
            }
        }

        void header() throws DocumentException {
            cb.saveState();
            cb.rectangle(2.5f * MM, (H - 20.5f) * MM, 87 * MM, 19.5f * MM);
            cb.clip();
            cb.newPath();
            image(logo, 2.5f, 1, 87);
            cb.restoreState();
            cell(
                    136,
                    0,
                    29,
                    4,
                    proforma ? "Proforma No." : "Invoice No.",
                    bold,
                    8,
                    Element.ALIGN_LEFT,
                    Color.BLACK);
            float numberBottom = 0;
            for (String text : lines(data.getDocumentNumber(), 38, bold, 8)) {
                cell(165, numberBottom, 38, 4, text, bold, 8, Element.ALIGN_RIGHT, Color.RED);
                numberBottom += 4;
            }
            float contactTop = Math.max(5, numberBottom + 1);
            float contactBottom =
                    multi(
                            93,
                            contactTop,
                            110,
                            5,
                            "Philips Area,Moshi- Arusha Road,  Arusha City\n"
                                    + "P. O. Box 16586 Arusha\n"
                                    + "Tel: 027 297 9004\n"
                                    + "Email: info@foresight.co.tz",
                            regular,
                            10,
                            Element.ALIGN_LEFT);
            float ribbonY = Math.max(26, contactBottom + 1);
            String title = proforma ? "Proforma Invoice" : "Invoice";
            float titleWidth = Math.max(32, width(title, boldItalic, 11) + 8),
                    left = 187 - titleWidth;
            ribbon(ribbonY, left - 1, 188f);
            cell(
                    left,
                    ribbonY - 3.5f,
                    titleWidth,
                    7,
                    title,
                    boldItalic,
                    11,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            y = ribbonY + 8;
        }

        void footer() {
            ribbon(H - 5, 203, null);
            if (proforma)
                cell(
                        6,
                        H - 12,
                        130,
                        4,
                        "Proforma only - not a tax invoice or receipt.",
                        italic,
                        6.5f,
                        Element.ALIGN_LEFT,
                        new Color(70, 70, 70));
        }

        void billing() {
            float top = y, at = top + 4.5f;
            String[][] fields = {
                {"Name", data.getClientName()},
                {"Address", data.getBillingAddress()},
                {
                    "City",
                    data.getBillingCity().isEmpty() ? data.getRegion() : data.getBillingCity()
                },
                {"TIN No.", data.getBillingTin()}
            };
            for (var field : fields) {
                float height = Math.max(4.7f, lines(field[1], 101, regular, 10).size() * 4.7f);
                cb.saveState();
                cb.setLineDash(.7f * MM, .45f * MM, 0);
                line(8, at + height, 127, at + height, .1f, new Color(180, 180, 180));
                cb.restoreState();
                cell(7.5f, at, 18, 4.7f, field[0], regular, 10, Element.ALIGN_LEFT, Color.BLACK);
                multi(25.5f, at, 101, 4.7f, field[1], regular, 10, Element.ALIGN_LEFT);
                at += height;
            }
            float height = Math.max(26, at - top + 2);
            round(6, top, 123, height, 4);
            round(131, top, 72, height, 4);
            rect(9, top - 2.3f, 24, 4.6f, Color.WHITE, null, 0, false);
            cell(
                    9,
                    top - 2.3f,
                    24,
                    4.6f,
                    "Customer",
                    regular,
                    10,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            cb.saveState();
            cb.setLineDash(.7f * MM, .45f * MM, 0);
            for (int i = 1; i <= 4; i++)
                line(
                        133,
                        top + 4.5f + i * 4.7f,
                        201,
                        top + 4.5f + i * 4.7f,
                        .1f,
                        new Color(180, 180, 180));
            cb.restoreState();
            cell(134, top + 4.5f, 18, 4.7f, "Date", regular, 10, Element.ALIGN_LEFT, Color.BLACK);
            var date = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
            cell(
                    153,
                    top + 4.5f,
                    47,
                    4.7f,
                    date.format(data.getDocumentDate()),
                    regular,
                    10,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            cell(
                    134,
                    top + 13.9f,
                    24,
                    4.7f,
                    proforma ? "Valid until" : "Due date",
                    regular,
                    7.5f,
                    Element.ALIGN_LEFT,
                    Color.BLACK);
            cell(
                    158,
                    top + 13.9f,
                    42,
                    4.7f,
                    date.format(data.getValidUntil()),
                    regular,
                    7.5f,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            y = top + height + 4;
        }

        void itemsHeader() {
            itemsStarted = true;
            float top = y, bodyTop = top + 5;
            tableBottom = Math.max(160, bodyTop + 15);
            rect(163.5f, bodyTop, 39.5f, tableBottom - bodyTop, Color.CYAN, null, 0, false);
            rect(6, top, 197, tableBottom - top, null, new Color(80, 80, 80), .15f, true);
            cb.saveState();
            cb.setLineDash(.7f * MM, .45f * MM, 0);
            for (float x : new float[] {25, 135, 163.5f})
                line(x, top, x, tableBottom, .15f, new Color(80, 80, 80));
            line(6, bodyTop, 203, bodyTop, .15f, new Color(80, 80, 80));
            cb.restoreState();
            String[] labels = {
                "Item", "Description", "Total Cost", "Amount in " + data.getCurrency()
            };
            float[] widths = {19, 110, 28.5f, 39.5f};
            float x = 6;
            for (int i = 0; i < 4; i++) {
                cell(
                        x,
                        top,
                        widths[i],
                        5,
                        labels[i],
                        bold,
                        9.5f,
                        Element.ALIGN_CENTER,
                        Color.BLACK);
                x += widths[i];
            }
            y = bodyTop + 5.4f;
        }

        void item(int number, String description, String amount) throws DocumentException {
            boolean first = true;
            var remaining = new ArrayDeque<>(lines(description, 110, bold, 10));
            while (!remaining.isEmpty()) {
                if (y + 5.2f > tableBottom + .001f) next();
                int count = Math.max(1, (int) Math.floor((tableBottom - y + .001f) / 5.2f));
                float top = y;
                if (first) {
                    cell(
                            6,
                            top,
                            19,
                            5.2f,
                            Integer.toString(number),
                            regular,
                            10,
                            Element.ALIGN_CENTER,
                            Color.BLACK);
                    for (float[] col : new float[][] {{135, 28.5f}, {163.5f, 39.5f}}) {
                        float size = 11;
                        while (width(amount, bold, size) > col[1] - 2 && size > 7.5f) size -= .25f;
                        cell(
                                col[0],
                                top,
                                col[1],
                                5.2f,
                                amount,
                                bold,
                                size,
                                Element.ALIGN_RIGHT,
                                Color.BLACK);
                    }
                }
                for (int i = 0; i < count && !remaining.isEmpty(); i++) {
                    cell(
                            25,
                            y,
                            110,
                            5.2f,
                            remaining.removeFirst(),
                            bold,
                            10,
                            Element.ALIGN_LEFT,
                            Color.BLACK);
                    y += 5.2f;
                }
                first = false;
            }
        }

        void closing() throws DocumentException {
            float base = tableBottom;
            itemsStarted = false;
            if (base + 79 > H - 7) {
                next();
                billing();
                base = 160;
            }
            rect(163.5f, base, 39.5f, 25, Color.CYAN, null, 0, false);
            cb.saveState();
            cb.setLineDash(.7f * MM, .45f * MM, 0);
            for (float x : new float[] {163.5f, 203})
                line(x, base, x, base + 25, .15f, new Color(80, 80, 80));
            for (int i = 0; i <= 5; i++)
                line(163.5f, base + i * 5, 203, base + i * 5, .15f, new Color(80, 80, 80));
            cb.restoreState();
            for (int i : new int[] {0, 20}) {
                cell(
                        135,
                        base + i,
                        28.5f,
                        5,
                        i == 0 ? "SubTotal" : "TOTAL",
                        i == 0 ? regular : bold,
                        9,
                        Element.ALIGN_RIGHT,
                        Color.BLACK);
                cell(
                        163.5f,
                        base + i,
                        39.5f,
                        5,
                        money(source.amount()),
                        bold,
                        10.5f,
                        Element.ALIGN_RIGHT,
                        Color.BLACK);
            }
            float top = base + 6;
            round(6, top, 99, 46, 7);
            rect(12, top - 2.3f, 34, 4.6f, Color.WHITE, null, 0, false);
            cell(
                    12,
                    top - 2.3f,
                    34,
                    4.6f,
                    "Payment Details",
                    regular,
                    10,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            String[] methods = {"CASH", "CHEQUE", "BANK_TRANSFER"},
                    labels = {"Cash", "Cheque", "Bank Transfer"};
            for (int i = 0; i < 3; i++) {
                float at = top + 4 + i * 5;
                rect(18, at, 3, 3, null, Color.BLACK, .2f, false);
                if (data.getPaymentMethod().equals(methods[i])) {
                    line(18.4f, at + 1.4f, 19.2f, at + 2.3f, .2f, Color.BLACK);
                    line(19.2f, at + 2.3f, 20.6f, at + .5f, .2f, Color.BLACK);
                }
                cell(
                        29.5f,
                        at - .6f,
                        71.5f,
                        4.2f,
                        labels[i],
                        regular,
                        10,
                        Element.ALIGN_LEFT,
                        Color.BLACK);
            }
            String[][] bank = {
                {"Name:", "Computer Resources T. Limited"},
                {"Bank", "CRDB Bank A/c No. 015C943810800"}
            };
            for (int i = 0; i < 2; i++) {
                cell(
                        7.5f,
                        top + 21 + i * 5,
                        18.5f,
                        4.5f,
                        bank[i][0],
                        bold,
                        10,
                        Element.ALIGN_LEFT,
                        Color.BLACK);
                cell(
                        26,
                        top + 21 + i * 5,
                        77.5f,
                        4.5f,
                        bank[i][1],
                        bold,
                        10,
                        Element.ALIGN_LEFT,
                        Color.BLACK);
            }
            cell(25, top + 31, 75, 4, "Bank Name:", bold, 10, Element.ALIGN_CENTER, Color.BLACK);
            cell(7.5f, top + 36, 18.5f, 4.5f, "Details", bold, 10, Element.ALIGN_LEFT, Color.BLACK);
            cell(
                    26,
                    top + 36,
                    77.5f,
                    4.5f,
                    "NMB Bank A/c No. 40810042746",
                    bold,
                    10,
                    Element.ALIGN_LEFT,
                    Color.BLACK);
            rect(
                    129,
                    base + 32,
                    74,
                    21,
                    new Color(235, 235, 235),
                    new Color(100, 100, 100),
                    .15f,
                    true);
            cell(
                    130,
                    base + 32.5f,
                    72,
                    4,
                    "Office Use Only",
                    regular,
                    8,
                    Element.ALIGN_LEFT,
                    Color.BLACK);
            image(stamp, 154, base + 24, 50);
            String currency =
                    Map.of(
                                    "TZS",
                                    "TANZANIA SHILLINGS",
                                    "USD",
                                    "US DOLLARS",
                                    "KES",
                                    "KENYA SHILLINGS",
                                    "EUR",
                                    "EUROS",
                                    "GBP",
                                    "POUNDS STERLING")
                            .get(data.getCurrency());
            cell(
                    42,
                    base + 66,
                    120,
                    5,
                    "THE ABOVE FIGURES ARE IN " + currency,
                    italic,
                    10,
                    Element.ALIGN_CENTER,
                    Color.BLACK);
            if (!data.getTerms().isEmpty()) {
                next();
                cell(6, y, 197, 7, "Terms and notes", bold, 10, Element.ALIGN_LEFT, Color.BLACK);
                y += 7;
                for (String paragraph : data.getTerms().replace("\r", "").split("\n", -1)) {
                    var wrapped = lines(paragraph, 197, regular, 9);
                    for (int i = 0; i < wrapped.size(); i++) {
                        if (y + 5 > H - 12) next();
                        String text = wrapped.get(i);
                        long spaces = text.chars().filter(c -> c == ' ').count();
                        wordSpacing =
                                i < wrapped.size() - 1 && spaces > 0
                                        ? (195 - width(text, regular, 9)) / spaces
                                        : 0;
                        cell(6, y, 197, 5, text, regular, 9, Element.ALIGN_LEFT, Color.BLACK);
                        wordSpacing = 0;
                        y += 5;
                    }
                }
            }
        }

        void finish() {
            footer();
            document.close();
        }
    }
}
