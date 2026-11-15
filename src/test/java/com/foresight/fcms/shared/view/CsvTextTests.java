package com.foresight.fcms.shared.view;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class CsvTextTests {
    @Test
    void textQuotingPreservesOrdinaryUnicodeAndRfc4180Delimiters() {
        assertEquals("\"\"", CsvText.quote(null));
        assertEquals("\"Mteja Ω, \"\"One\"\"\nTwo\"", CsvText.quote("Mteja Ω, \"One\"\nTwo"));
        assertEquals("\"A-1\"", CsvText.quote("A-1"));
    }

    @Test
    void formulaPrefixesStayTextIncludingLeadingWhitespaceAndQuotedPayloads() {
        for (String text :
                List.of(
                        "=1+1",
                        "+SUM(A1)",
                        "-2+3",
                        "@SUM(A1)",
                        " \t=1+1",
                        "\r\n=HYPERLINK(\"example.invalid\")"))
            assertEquals("\"'" + text.replace("\"", "\"\"") + "\"", CsvText.quote(text));
    }
}
