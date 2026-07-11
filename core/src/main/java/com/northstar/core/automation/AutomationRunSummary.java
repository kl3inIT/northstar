package com.northstar.core.automation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AutomationRunSummary(
        @NotNull UUID id,
        @NotNull UUID automationId,
        @NotNull Instant scheduledFor,
        @NotNull AutomationRunKind runKind,
        @NotNull AutomationRunStatus status,
        @NotNull int attempt,
        @Nullable @Schema(nullable = true) Instant startedAt,
        @Nullable @Schema(nullable = true) Instant finishedAt,
        @Nullable @Schema(nullable = true) String errorCode,
        @Nullable @Schema(nullable = true) String errorMessage,
        @Nullable @Schema(nullable = true) String outputType,
        @Nullable @Schema(nullable = true) UUID outputId,
        @NotNull Map<String, Object> metrics,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {
}
