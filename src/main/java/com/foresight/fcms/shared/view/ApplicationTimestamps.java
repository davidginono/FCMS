package com.foresight.fcms.shared.view;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Adapted from Microfinance's application-time component; presentation only. */
public final class ApplicationTimestamps {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

    private ApplicationTimestamps() {}

    public static String date(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDate date) return DATE.format(date);
        if (value instanceof java.sql.Date date) return DATE.format(date.toLocalDate());
        return display(value);
    }

    public static String display(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDate || value instanceof java.sql.Date) return date(value);
        if (value instanceof LocalDateTime time) return TIME.format(time);
        if (value instanceof OffsetDateTime time)
            return TIME.format(time.atZoneSameInstant(ZoneId.systemDefault()));
        if (value instanceof Instant time) return TIME.format(time.atZone(ZoneId.systemDefault()));
        return value.toString();
    }
}
