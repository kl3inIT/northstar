package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Monthly category limit plus spend derived from the ledger. {@code inherited}
 * marks a carry-forward view: this category has no row in the requested month,
 * so the latest earlier budgeted month's limit is shown against this month's
 * spend — zero-maintenance defaults, materialized per category when the user
 * edits one ({@code id} then still points at the SOURCE month's row; writers
 * must create for the requested month instead of updating that id).
 */
public record BudgetSummary(
        @NotNull UUID id,
        @NotNull String month,
        @NotNull String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long limitAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long spentAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long remainingAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int progressPercent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean overBudget,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean inherited,
        @NotNull Instant createdAt) {
}
