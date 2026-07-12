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
        List<String> ttsTargets,
        List<String> webSearchTargets,
        List<String> webFetchTargets,
        List<String> sttTargets,
        List<String> imageTargets,
        List<String> embeddingTargets,
        boolean discoverModels,
        Duration timeout,
        AiGatewaySource source) {

    boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    AiGatewayDescriptor descriptor(AiCredentialSource credentialSource,
            boolean deploymentBacked, boolean overridden) {
        return new AiGatewayDescriptor(id, displayName, type, type.capabilities(), configured(), source,
                credentialSource, deploymentBacked, overridden, true, baseUrl, models, ttsTargets,
                webSearchTargets, webFetchTargets, sttTargets, imageTargets, embeddingTargets,
                discoverModels,
                Math.toIntExact(timeout.toSeconds()));
    }
}
