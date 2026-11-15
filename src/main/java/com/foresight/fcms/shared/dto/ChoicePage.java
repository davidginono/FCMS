package com.foresight.fcms.shared.dto;

public record ChoicePage<T>(java.util.List<T> items, int page, boolean hasNext) {
    public static <T> ChoicePage<T> from(java.util.List<T> rows, int page) {
        return new ChoicePage<>(
                java.util.List.copyOf(rows.subList(0, Math.min(rows.size(), 25))),
                page,
                rows.size() > 25);
    }
}
