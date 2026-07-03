package com.northstar.core.task;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Read model for task lists (Today, upcoming, board and calendar views).
 * {@code notes}, {@code dueDate}, {@code dueTime} and {@code completedAt} are
 * genuinely nullable; the {@code @NotNull} marks make the rest required in the
 * generated OpenAPI client.
 */
public record TaskSummary(
        @NotNull UUID id,
        @NotNull String title,
        String notes,
        @NotNull TaskStatus status,
        LocalDate dueDate,
        LocalTime dueTime,
        Instant completedAt,
        @NotNull Instant createdAt) {
}
