package com.northstar.core.calendar;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A gap between events, big enough for the asked-for duration. */
public record FreeSlot(@NotNull Instant startAt, @NotNull Instant endAt) {
}
