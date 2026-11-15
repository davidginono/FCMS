package com.foresight.fcms.report.service;

import com.foresight.fcms.accounting.view.VoucherStationery;
import com.foresight.fcms.report.dto.OperationalDocument;
import com.foresight.fcms.report.view.OperationalStationery;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Semaphore;

/** One bounded renderer for prepared operational/client documents; never accesses persistence. */
@Service
public class ReportPdfRenderer {
    private final Semaphore renders = new Semaphore(2);

    public byte[] table(OperationalDocument source) {
        boolean landscape =
                source.kind().equals("TASK")
                        || source.kind().equals("BILLING")
                        || (source.kind().startsWith("CLIENT")
                                && !source.kind().equals("CLIENT_PAYMENT"));
        return render(
                landscape,
                (document, writer, font) ->
                        new OperationalStationery(source, document, writer, font).write());
    }

    public byte[] voucher(VoucherStationery.Source source) {
        return render(
                false,
                (document, writer, font) ->
                        new VoucherStationery(document, writer, font, source).write());
    }

    private interface Content {
        void write(Document document, PdfWriter writer, BaseFont font) throws Exception;
    }

    private byte[] render(boolean landscape, Content content) {
        if (!renders.tryAcquire()) throw new IllegalArgumentException("error.exportBusy");
        try (var out = new ByteArrayOutputStream();
                var bytes =
                        new ClassPathResource("report-fonts/NotoSans-Regular.ttf")
                                .getInputStream()) {
            var font =
                    BaseFont.createFont(
                            "NotoSans-Regular.ttf",
                            BaseFont.IDENTITY_H,
                            true,
                            false,
                            bytes.readAllBytes(),
                            null);
            var paper =
                    landscape ? new Rectangle(841.89f, 595.28f) : new Rectangle(595.28f, 841.89f);
            var document = new Document(paper);
            var writer = PdfWriter.getInstance(document, out);
            document.addCreator("FCMS");
            document.open();
            content.write(document, writer, font);
            document.close();
            return out.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("Report generation failed", failure);
        } finally {
            renders.release();
        }
    }
}
