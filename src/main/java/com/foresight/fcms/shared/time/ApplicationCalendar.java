package com.foresight.fcms.shared.time;

import java.time.*;

/** FCMS business calendar; persisted audit and event instants remain UTC. */
public final class ApplicationCalendar {
    public static final ZoneId ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private ApplicationCalendar() {}

    public static LocalDate today() {
        return today(Clock.systemUTC());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(Clock.system(ZONE));
    }
}
