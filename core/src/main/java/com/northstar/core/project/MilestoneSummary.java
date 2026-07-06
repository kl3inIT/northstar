package com.northstar.core.project;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read model for one project stage. {@code doneAt} null = still open. */
public record MilestoneSummary(
        @NotNull UUID id,
        @NotNull String name,
        LocalDate dueDate,
        Instant doneAt,
        int sortOrder) {
}
