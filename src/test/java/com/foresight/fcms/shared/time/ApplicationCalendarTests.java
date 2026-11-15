package com.foresight.fcms.shared.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.time.*;

class ApplicationCalendarTests {
    @Test
    void businessDayChangesAtTanzaniaMidnightRegardlessOfClockZone() {
        assertEquals(
                LocalDate.of(2026, 11, 15),
                ApplicationCalendar.today(
                        Clock.fixed(Instant.parse("2026-11-15T20:59:59Z"), ZoneOffset.UTC)));
        assertEquals(
                LocalDate.of(2026, 11, 16),
                ApplicationCalendar.today(
                        Clock.fixed(
                                Instant.parse("2026-11-15T21:00:00Z"),
                                ZoneId.of("America/Los_Angeles"))));
        assertEquals(
                LocalDate.of(2027, 1, 1),
                ApplicationCalendar.today(
                        Clock.fixed(Instant.parse("2026-12-31T21:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    void productionBusinessDatesNeverDependOnTheHostDefaultTimezone() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            var violations =
                    paths.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> {
                                try {
                                    return Files.readString(p)
                                            .matches(
                                                    "(?s).*LocalDate(?:Time)?\\.now\\(\\).*");
                                } catch (java.io.IOException e) {
                                    throw new java.io.UncheckedIOException(e);
                                }
                            })
                            .toList();
            assertTrue(violations.isEmpty(), violations.toString());
        }
    }
}
