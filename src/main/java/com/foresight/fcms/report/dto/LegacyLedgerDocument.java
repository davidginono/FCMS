package com.foresight.fcms.report.dto;

import java.util.List;

/** Prepared FPDF-compatible ledger stationery. Dimensions are millimetres. */
public record LegacyLedgerDocument(
        String kind,
        String title,
        String printed,
        String pageLabel,
        List<Preface> preface,
        List<String> columns,
        List<Float> widths,
        List<String> aligns,
        List<List<String>> rows,
        List<Total> totals,
        List<String> notes) {
    public LegacyLedgerDocument {
        preface = List.copyOf(preface);
        columns = List.copyOf(columns);
        widths = List.copyOf(widths);
        aligns = List.copyOf(aligns);
        rows = rows.stream().map(List::copyOf).toList();
        totals = List.copyOf(totals);
        notes = List.copyOf(notes);
    }

    public record Preface(String text, boolean bold, float height) {}

    public record Total(List<String> cells, List<Float> widths, float gap) {
        public Total {
            cells = List.copyOf(cells);
            widths = List.copyOf(widths);
        }
    }
}
