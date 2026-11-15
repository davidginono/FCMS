package com.foresight.fcms.shared.view;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;

class ApplicationTimestampsTests {
    @Test
    void offsetAndInstantValuesUseApplicationTimezoneAcrossDayBoundaries() {
        assertEquals(
                "02/11/2026 01:30",
                ApplicationTimestamps.display(Instant.parse("2026-11-01T22:30:00Z")));
        assertEquals(
                "02/11/2026 01:30",
                ApplicationTimestamps.display(OffsetDateTime.parse("2026-11-01T22:30:00Z")));
    }

    @Test
    void localWorkforceTimesAndDateOnlyValuesRemainUnshifted() {
        assertEquals(
                "01/11/2026 22:30",
                ApplicationTimestamps.display(LocalDateTime.of(2026, 11, 1, 22, 30)));
        assertEquals("01/11/2026", ApplicationTimestamps.date(LocalDate.of(2026, 11, 1)));
        assertEquals("", ApplicationTimestamps.display(null));
    }
}
