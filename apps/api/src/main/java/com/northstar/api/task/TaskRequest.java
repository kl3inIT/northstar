package com.northstar.api.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Create/update payload for a task. Title size mirrors the V4 column width;
 * {@code plannedDate} is the do-vs-due "do" day (independent of the deadline).
 * {@code projectId} files the task under a project on create; update ignores it
 * (moving a task between projects is the dedicated PATCH /{id}/project).
 */
record TaskRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 10_000) String notes,
        LocalDate dueDate,
        LocalTime dueTime,
        LocalDate plannedDate,
        UUID disciplineId,
        UUID projectId) {
}
