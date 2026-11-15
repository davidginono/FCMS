package com.foresight.fcms.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/** Prepared statement face shared by JSP, print and PDF. No persistence objects. */
public record StatementDocument(
        String title,
        String style,
        boolean landscape,
        int detailsBefore,
        Header header,
        List<Column> columns,
        List<Row> rows,
        String notice,
        List<String> provenance) {
    public StatementDocument {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        provenance = List.copyOf(provenance);
    }

    public List<String> context() {
        if (style.equals("equity"))
            return List.of(header.period(), header.identity() + " | " + header.scope() + " | TZS");
        return header.pdfLines();
    }

    /** The original preview and PDF place the same metadata in different orders. */
    public record Header(
            String identity,
            String scope,
            String period,
            String previous,
            String generated,
            List<String> extra) {
        public Header {
            extra = List.copyOf(extra);
        }

        public List<String> pdfLines() {
            var lines =
                    new ArrayList<>(List.of(identity + " | " + scope, period, previous, generated));
            lines.addAll(extra);
            return List.copyOf(lines);
        }

        public List<String> previewLines() {
            var lines =
                    new ArrayList<>(
                            List.of(
                                    scope,
                                    period.replaceFirst(": ", " ").replace(" | TZS", " · TZS"),
                                    generated.replaceFirst(": ", " "),
                                    previous));
            lines.addAll(extra);
            return List.copyOf(lines);
        }
    }

    public record Column(String label, boolean numeric, float weight) {}

    public record Row(
            List<String> cells,
            boolean total,
            boolean heading,
            String label,
            String drillLine,
            Long accountId,
            Long journalId,
            java.util.Map<Integer, String> cellDrills) {
        public Row {
            cells = List.copyOf(cells);
            cellDrills = java.util.Map.copyOf(cellDrills);
        }

        public Row(
                List<String> cells,
                boolean total,
                boolean heading,
                String label,
                String drillLine,
                Long accountId,
                Long journalId) {
            this(cells, total, heading, label, drillLine, accountId, journalId, java.util.Map.of());
        }

        public Row(
                List<String> cells,
                boolean total,
                boolean heading,
                String label,
                String drillLine,
                Long accountId) {
            this(cells, total, heading, label, drillLine, accountId, null);
        }

        private static final java.util.regex.Pattern MONEY =
                java.util.regex.Pattern.compile("\\(?[0-9][0-9,]*\\.[0-9]{2}\\)?");

        public boolean numericCell(int index) {
            return !heading && MONEY.matcher(cells.get(index)).matches();
        }
    }
}
