package com.northstar.core.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Read model for task lists (Today, upcoming, board and calendar views later). */
public record TaskSummary(
        UUID id,
        String title,
        String notes,
        TaskStatus status,
        LocalDate dueDate,
        LocalTime dueTime,
        Instant completedAt,
        Instant createdAt) {
}
