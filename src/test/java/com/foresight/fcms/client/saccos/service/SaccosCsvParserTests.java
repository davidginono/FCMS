package com.foresight.fcms.client.saccos.service;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.client.saccos.dto.SaccosImportFailure;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

class SaccosCsvParserTests {
    final SaccosCsvParser csv = new SaccosCsvParser();
    static final String HEADER =
            "Saccos_Name,Entry_Date,Region,financial_year,payment_status,Amount,Annual_fee\r\n";
    static final String ROW = "Client,2026-11-01,Mwanza,2026,PAID,100.11,200.25";

    byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    SaccosImportFailure rejected(String text) {
        return assertThrows(SaccosImportFailure.class, () -> csv.parse(bytes(text)));
    }

    @Test
    void legacySeventeenColumnsPreserveQuotesDatesAndEveryExactFinancialField() {
        String header =
                "Saccos_Name,Entry_Date,expire_date,Region,Amount,tax,com,Programmer_cost,Annual_fee,Sys_install_fee,Training_fee,Android_fee,Sys_reinstall_fee,bulk_sms,hosting_fee,saccos_status_id,fyear_id\r\n";
        var row =
                csv.parse(
                                bytes(
                                        header
                                                + "\"Umoja,"
                                                + " \"\"Ltd\"\"\",2026-11-01,2027-11-01,Mwanza,100.11,1.01,2.02,3.03,200.25,4.04,5.05,6.06,7.07,8.08,9.09,1,1"))
                        .getFirst();
        var form = csv.form(row, 1, 1);
        assertEquals("Umoja, \"Ltd\"", form.getSaccosName());
        assertEquals("2027-11-01", form.getExpireDate().toString());
        assertEquals(new BigDecimal("200.25"), form.getAnnualFee());
        assertEquals(new BigDecimal("100.11"), form.getAmount());
        assertEquals(new BigDecimal("1.01"), form.getWithholdingTax());
        assertEquals(new BigDecimal("2.02"), form.getCommission());
        assertEquals(new BigDecimal("3.03"), form.getProgrammerCost());
        assertEquals(new BigDecimal("4.04"), form.getSystemInstallFee());
        assertEquals(new BigDecimal("5.05"), form.getTrainingFee());
        assertEquals(new BigDecimal("6.06"), form.getAndroidFee());
        assertEquals(new BigDecimal("7.07"), form.getSystemReinstallFee());
        assertEquals(new BigDecimal("8.08"), form.getBulkSmsFee());
        assertEquals(new BigDecimal("9.09"), form.getHostingFee());
        assertEquals(1, csv.id(row, "financialYearId"));
        assertNull(form.getDatePaid());
        assertEquals("ACTIVE", form.getOperationalStatus());
        assertEquals("NO", form.getInvoiceStatus());
    }

    @Test
    void utf8BomAndReadableReferencesDoNotInventDatesOrAdditionalFees() {
        var rows =
                csv.parse(
                        bytes(
                                "\ufeff"
                                        + HEADER
                                        + ROW.replace("Client", "Chama cha Ω")
                                                .replace("PAID", "paid")
                                        + "\r\n\r\n"));
        assertEquals(1, rows.size());
        assertEquals("Chama cha Ω", rows.getFirst().values().get("saccosName"));
        var form = csv.form(rows.getFirst(), 12, 4);
        assertEquals(12, form.getFinancialYearId());
        assertEquals(4, form.getPaymentStatusId());
        assertNull(form.getDatePaid());
        assertNull(form.getExpireDate());
        assertEquals(new BigDecimal("0.00"), form.getEssFee());
        assertEquals(new BigDecimal("0.00"), form.getCloudBackupCost());
        assertThrows(
                UnsupportedOperationException.class,
                () -> rows.getFirst().values().put("amount", "0"));
    }

    @Test
    void invalidFinancialRepresentationsAndOverflowAreRejectedBeforeAnyResult() {
        for (String value :
                new String[] {"-0.01", "1.001", "NaN", "1e3", "9999999999999.00", "\"1,000.00\""}) {
            var error =
                    rejected(
                            HEADER
                                    + ROW
                                    + "\r\n"
                                    + ROW.replace("Client", "PRIVATE-NAME")
                                            .replace("100.11", value));
            assertEquals(3, error.line());
            assertEquals("Amount", error.field());
            assertFalse(error.toString().contains("PRIVATE-NAME"));
        }
    }

    @Test
    void malformedDatesAndReverseRangesAreRejected() {
        for (String value : new String[] {"2026-02-29", "01/11/2026", "0000-11-01", ""})
            assertEquals("Entry_Date", rejected(HEADER + ROW.replace("2026-11-01", value)).field());
        assertEquals(
                "expire_date",
                rejected(HEADER.trim() + ",expire_date\n" + ROW + ",2025-11-01").field());
        assertEquals(
                "2024-02-29",
                csv.form(
                                csv.parse(bytes(HEADER + ROW.replace("2026-11-01", "2024-02-29")))
                                        .getFirst(),
                                1,
                                1)
                        .getEntryDate()
                        .toString());
    }

    @Test
    void headersCannotSelectRecordsActorsPathsOrApprovalState() {
        for (String key :
                new String[] {
                    "id",
                    "pfile",
                    "owner_id",
                    "approved_by",
                    "balance",
                    "invoice_status",
                    "operational_status"
                })
            assertEquals(
                    "import.error.header",
                    rejected(HEADER.trim() + "," + key + "\n" + ROW + ",1").getMessage());
        assertEquals(
                "import.error.duplicate",
                rejected(HEADER.trim() + ",amount\n" + ROW + ",0").getMessage());
        assertEquals(
                "import.error.duplicate",
                rejected(HEADER.trim() + ",fyear_id\n" + ROW + ",1").getMessage());
        assertEquals(
                "import.error.required",
                rejected(HEADER.replace("Entry_Date,", "") + ROW.replace("2026-11-01,", ""))
                        .getMessage());
    }

    @Test
    void strictCsvRejectsBrokenQuotesWrongCountsAndControlBytes() {
        for (String text :
                new String[] {
                    HEADER + '"' + ROW,
                    HEADER + "Cl\"ient,2026-11-01,Mwanza,2026,PAID,100.11,200.25",
                    HEADER + ROW + ",extra",
                    HEADER + ROW.replace("Client", "\"Client\"suffix"),
                    HEADER + ROW.replace("Client", "Cli\u0000ent")
                }) rejected(text);
        assertEquals(
                "import.error.encoding",
                assertThrows(
                                SaccosImportFailure.class,
                                () -> csv.parse(new byte[] {(byte) 0xc3, (byte) 0x28}))
                        .getMessage());
    }

    @Test
    void identifiersAndPaymentCodesAreExplicitAndBounded() {
        for (String value : new String[] {"ACTIVE", "UNKNOWN", ""})
            assertEquals("payment_status", rejected(HEADER + ROW.replace("PAID", value)).field());
        String legacy =
                HEADER.replace("financial_year,payment_status", "fyear_id,saccos_status_id");
        for (String value : new String[] {"0", "-1", "1.0", "9999999999999999999"})
            rejected(legacy + ROW.replace("2026,PAID", value + ",1"));
    }

    @Test
    void fileRowAndFieldLimitsRemainBounded() {
        assertEquals(100, csv.parse(bytes(HEADER + (ROW + "\n").repeat(100))).size());
        assertEquals(
                "import.error.limit", rejected(HEADER + (ROW + "\n").repeat(101)).getMessage());
        assertEquals(
                "import.error.file",
                assertThrows(
                                SaccosImportFailure.class,
                                () -> csv.parse(new byte[SaccosCsvParser.MAX_BYTES + 1]))
                        .getMessage());
        rejected(HEADER + ROW.replace("Client", "x".repeat(256)));
        rejected(HEADER + ROW.replace("Client", "x".repeat(4097)));
        assertEquals("import.error.empty", rejected(HEADER).getMessage());
    }

    @Test
    void templateUsesReadableReferencesAndIncludesAllSupportedFinancialColumns() {
        String template = new String(csv.template(), StandardCharsets.UTF_8);
        assertTrue(template.contains("financial_year,payment_status"));
        assertTrue(template.contains("ess_fee,cloud_backup,backup_cost"));
        assertFalse(template.contains("pfile"));
        assertFalse(template.contains("saccos_status_id"));
        assertEquals(22, template.trim().split(",").length);
    }
}
