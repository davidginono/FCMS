package com.foresight.fcms.client.saccos.dto;

/** Localizable validation context. Row contents are deliberately excluded. */
public final class SaccosImportFailure extends IllegalArgumentException {
    private final int line;
    private final String field;

    public SaccosImportFailure(String code, int line, String field) {
        super(code);
        this.line = line;
        this.field = field;
    }

    public int line() {
        return line;
    }

    public String field() {
        return field;
    }
}
