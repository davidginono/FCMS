package com.foresight.fcms.report.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PdfReportService {
    public byte[] table(String title, List<String> columns, List<List<String>> rows) {
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        var bytes = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4.rotate(), 28, 28, 30, 30);
        var writer = PdfWriter.getInstance(document, bytes);
        writer.setPageEvent(
                new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        ColumnText.showTextAligned(
                                w.getDirectContent(),
                                Element.ALIGN_RIGHT,
                                new Phrase(
                                        Integer.toString(w.getPageNumber()),
                                        FontFactory.getFont(FontFactory.HELVETICA, 8)),
                                d.right(),
                                15,
                                0);
                    }
                });
        document.open();
        document.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        document.add(new Paragraph("FCMS | TZS", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        var table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(12);
        for (String c : columns) {
            var cell =
                    new PdfPCell(new Phrase(c, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            cell.setBackgroundColor(new java.awt.Color(242, 243, 243));
            cell.setPadding(5);
            table.addCell(cell);
        }
        table.setHeaderRows(1);
        for (var row : rows)
            for (String value : row) {
                var cell =
                        new PdfPCell(
                                new Phrase(
                                        value == null ? "" : value,
                                        FontFactory.getFont(FontFactory.HELVETICA, 9)));
                cell.setPadding(5);
                table.addCell(cell);
            }
        document.add(table);
        document.close();
        return bytes.toByteArray();
    }
}
