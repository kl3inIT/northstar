package com.northstar.api.calendar;

import com.northstar.core.calendar.EventColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Create/update payload for a calendar event. Title size mirrors the V6 column width. */
record CalendarEventRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 10_000) String notes,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        @NotNull EventColor color) {
}
