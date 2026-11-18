package com.foresight.fcms.shared.validation;

import java.util.List;

/** A domain validation failure identifies controls, without retaining submitted values. */
public final class FieldValidationException extends IllegalArgumentException {
    private final List<String> fields;

    public FieldValidationException(String messageKey, String... fields) {
        super(messageKey);
        this.fields = List.of(fields);
    }

    public List<String> fields() { return fields; }
}
