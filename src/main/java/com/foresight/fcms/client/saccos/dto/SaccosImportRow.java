package com.foresight.fcms.client.saccos.dto;

import java.util.Map;

/** Source line and allowlisted, textual CSV fields; never contains a filesystem path. */
public record SaccosImportRow(int line, Map<String, String> values) {
    public SaccosImportRow {
        values = Map.copyOf(values);
    }
}
