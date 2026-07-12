package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayType;
import java.time.Duration;
import java.util.List;

record AiGatewayDefinition(
        String id,
        AiGatewayType type,
        String displayName,
        String baseUrl,
        String apiKey,
        List<String> models,
        boolean discoverModels,
        Duration timeout,
        AiGatewaySource source) {

    boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    AiGatewayDescriptor descriptor() {
        return new AiGatewayDescriptor(id, displayName, type, type.capabilities(), configured(), source,
                source == AiGatewaySource.SETTINGS, baseUrl, models, discoverModels,
                Math.toIntExact(timeout.toSeconds()));
    }
}
