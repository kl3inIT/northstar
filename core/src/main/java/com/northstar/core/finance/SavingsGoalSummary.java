package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Savings target with deterministic progress fields for UI and assistant reads. */
public record SavingsGoalSummary(
        @NotNull UUID id,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long targetAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long savedAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long remainingAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long monthlyContribution,
        LocalDate targetDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int progressPercent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean completed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @NotNull Instant createdAt) {
}
