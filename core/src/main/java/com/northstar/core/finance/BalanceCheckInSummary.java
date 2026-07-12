package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A persisted balance anchor and the adjustment that brought the ledger to it. */
public record BalanceCheckInSummary(
        @NotNull UUID id,
        @NotNull LocalDate checkedOn,
        @NotNull BalanceBreakdown breakdown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalBalance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expectedBalance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long discrepancy,
        TransactionSummary adjustment,
        @NotNull Instant createdAt) {
}
