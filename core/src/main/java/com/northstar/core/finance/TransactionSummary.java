package com.northstar.core.finance;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model for ledger rows (page table, assistant reads). The {@code @NotNull}
 * marks make every field required in the generated OpenAPI client — including
 * the primitives, which springdoc would otherwise emit as optional.
 */
public record TransactionSummary(
        @NotNull UUID id,
        @NotNull TransactionType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
        @NotNull LocalDate occurredOn,
        @NotNull String description,
        @NotNull String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exceptional,
        @NotNull TransactionSource source,
        @NotNull Instant createdAt) {
}
