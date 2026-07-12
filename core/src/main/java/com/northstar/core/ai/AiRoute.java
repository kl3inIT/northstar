package com.northstar.core.ai;

import java.util.Map;

public record AiRoute(String gatewayId, String modelId, Map<String, String> options) {

    public AiRoute(String gatewayId, String modelId) {
        this(gatewayId, modelId, Map.of());
    }

    public AiRoute {
        gatewayId = required(gatewayId, "gatewayId").toLowerCase();
        modelId = required(modelId, "modelId");
        options = options == null ? Map.of() : options.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()
                        && entry.getValue() != null && !entry.getValue().isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().strip(), entry -> entry.getValue().strip()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
