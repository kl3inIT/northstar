package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** A deterministic ledger pattern the user may explicitly promote to a subscription. */
public record RecurringSuggestion(
        @NotNull String key,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
        @NotNull String category,
        @NotNull SubscriptionCycle cycle,
        @NotNull LocalDate nextExpectedOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int occurrences) {
}
