package com.northstar.core.automation;

import java.util.Map;
import java.util.UUID;

public record AutomationHandlerResult(String outputType, UUID outputId, Map<String, Object> metrics) {
    public AutomationHandlerResult {
        outputType = outputType == null || outputType.isBlank() ? null : outputType.strip();
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public static AutomationHandlerResult none(Map<String, Object> metrics) {
        return new AutomationHandlerResult(null, null, metrics);
    }
}
