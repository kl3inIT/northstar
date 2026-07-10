package com.northstar.core.finance;

import jakarta.validation.constraints.NotNull;

/** Compact correction example safe to include in the Capture extraction prompt. */
public record CategoryCorrectionSummary(
        @NotNull TransactionType type,
        @NotNull String description,
        @NotNull String category) {
}
