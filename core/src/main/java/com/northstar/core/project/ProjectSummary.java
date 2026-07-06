package com.northstar.core.project;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read model for a project, milestones included (a project has a handful of
 * stages — always worth one round-trip). {@code progressPercent} is derived
 * from done milestones; {@code notes}, {@code disciplineId} and the dates are
 * genuinely nullable.
 */
public record ProjectSummary(
        @NotNull UUID id,
        @NotNull String name,
        String notes,
        @NotNull Project.ProjectStatus status,
        UUID disciplineId,
        LocalDate startDate,
        LocalDate targetDate,
        @NotNull List<MilestoneSummary> milestones,
        int progressPercent,
        @NotNull Instant createdAt) {
}
