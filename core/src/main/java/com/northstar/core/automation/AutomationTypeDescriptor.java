package com.northstar.core.automation;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record AutomationTypeDescriptor(
        @NotNull String type,
        @NotNull String displayName,
        @NotNull String description,
        @NotNull int configVersion,
        @NotNull Map<String, Object> defaultConfig) {
}
