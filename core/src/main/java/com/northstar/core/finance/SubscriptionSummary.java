package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recurring charge definition plus its normalized monthly cost.
 * {@code cancelReminderOn} is null unless the user asked to be reminded to
 * cancel/review by a date (trial conversion, planned cancellation).
 */
public record SubscriptionSummary(
        @NotNull UUID id,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
        @NotNull String category,
        @NotNull SubscriptionCycle cycle,
        @NotNull LocalDate nextDueOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long monthlyEquivalent,
        LocalDate cancelReminderOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @NotNull Instant createdAt) {
}
