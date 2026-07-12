package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayType;
import java.util.List;

public record AiGatewayInput(
        String id,
        String displayName,
        AiGatewayType type,
        String baseUrl,
        String apiKey,
        List<String> models,
        boolean discoverModels,
        int timeoutSeconds) {
}
