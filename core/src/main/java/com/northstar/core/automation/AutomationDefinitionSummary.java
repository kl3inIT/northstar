package com.northstar.core.automation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AutomationDefinitionSummary(
        @NotNull UUID id,
        @NotNull String type,
        @NotNull String name,
        @NotNull boolean enabled,
        @NotNull AutomationTrigger trigger,
        @NotNull Map<String, Object> workflowConfig,
        @NotNull int configVersion,
        @NotNull long scheduleVersion,
        @NotNull long syncedScheduleVersion,
        @NotNull boolean scheduleSynced,
        @Nullable @Schema(nullable = true) Instant deletedAt,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull long version) {
}
