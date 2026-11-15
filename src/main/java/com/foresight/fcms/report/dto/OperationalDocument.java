package com.foresight.fcms.report.dto;

import java.util.List;

/** Prepared legacy operational stationery; all dimensions are millimetres. */
public record OperationalDocument(
        String kind,
        String title,
        String period,
        String pageLabel,
        List<String> columns,
        List<Float> widths,
        List<String> aligns,
        List<List<String>> rows,
        List<String> totals,
        List<String> notes,
        List<Heading> headings,
        Footer footer,
        String emptyMessage) {
    public OperationalDocument(
            String kind,
            String title,
            String period,
            String pageLabel,
            List<String> columns,
            List<Float> widths,
            List<String> aligns,
            List<List<String>> rows,
            List<String> totals,
            List<String> notes) {
        this(
                kind,
                title,
                period,
                pageLabel,
                columns,
                widths,
                aligns,
                rows,
                totals,
                notes,
                List.of(),
                new Footer(true, kind.equals("TASK") ? 9 : 8),
                "");
    }

    public OperationalDocument {
        columns = List.copyOf(columns);
        widths = List.copyOf(widths);
        aligns = List.copyOf(aligns);
        rows = rows.stream().map(List::copyOf).toList();
        totals = List.copyOf(totals);
        notes = List.copyOf(notes);
        headings = List.copyOf(headings);
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        int count = columns.size();
        if (columns.size() != widths.size()
                || columns.size() != aligns.size()
                || rows.stream().anyMatch(row -> row.size() != count))
            throw new IllegalArgumentException("Invalid report columns");
    }

    /** Original presentation metadata, independent of the underlying resource's business state. */
    public record Heading(
            String text, float size, float height, float gapAfter, boolean bold, boolean repeat) {}

    public record Footer(boolean totalPages, float size, String suffix) {
        public Footer(boolean totalPages, float size) {
            this(totalPages, size, "");
        }
    }
}
