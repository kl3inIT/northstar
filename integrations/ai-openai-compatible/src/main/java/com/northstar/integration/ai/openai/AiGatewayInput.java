package com.northstar.integration.ai.openai;

import java.util.List;

public record AiGatewayInput(
        String id,
        String displayName,
        String baseUrl,
        String apiKey,
        List<String> models,
        boolean discoverModels,
        int timeoutSeconds) {
}
