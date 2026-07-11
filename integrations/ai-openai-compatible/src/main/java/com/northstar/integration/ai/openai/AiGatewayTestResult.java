package com.northstar.integration.ai.openai;

import java.util.List;

public record AiGatewayTestResult(
        boolean success,
        long latencyMillis,
        List<AiModelDescriptor> models,
        String message) {
}
