package com.northstar.core.calendar;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for calendar events. Only {@code notes} is nullable; the
 * {@code @NotNull} marks make the rest required in the generated OpenAPI client.
 */
public record CalendarEventSummary(
        @NotNull UUID id,
        @NotNull String title,
        String notes,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        boolean allDay,
        @NotNull EventColor color) {
}
