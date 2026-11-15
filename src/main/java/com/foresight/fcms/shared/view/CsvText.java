package com.foresight.fcms.shared.view;

/**
 * RFC4180 text cells, with spreadsheet formula interpretation disabled. Numeric cells stay numeric.
 */
public final class CsvText {
    private CsvText() {}

    public static String quote(String value) {
        String text = value == null ? "" : value;
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
