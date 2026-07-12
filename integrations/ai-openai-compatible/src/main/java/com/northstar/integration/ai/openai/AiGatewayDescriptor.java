package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayType;
import java.util.List;
import java.util.Set;

public record AiGatewayDescriptor(
        String id,
        String displayName,
        AiGatewayType type,
        Set<AiGatewayCapability> capabilities,
        boolean configured,
        AiGatewaySource source,
        boolean editable,
        String baseUrl,
        List<String> configuredModels,
        List<String> configuredTtsTargets,
        List<String> configuredWebSearchTargets,
        List<String> configuredWebFetchTargets,
        List<String> configuredSttTargets,
        List<String> configuredImageTargets,
        List<String> configuredEmbeddingTargets,
        boolean discoverModels,
        int timeoutSeconds) {
}
