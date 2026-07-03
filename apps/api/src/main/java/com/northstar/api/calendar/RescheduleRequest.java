package com.northstar.api.calendar;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Drag-drop / resize payload: just the new time span. */
record RescheduleRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt) {
}
