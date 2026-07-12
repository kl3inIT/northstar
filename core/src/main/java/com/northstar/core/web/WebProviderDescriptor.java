package com.northstar.core.web;

import com.northstar.core.ai.AiGatewayType;
import java.util.Set;

public record WebProviderDescriptor(
        String id,
        String displayName,
        Set<WebResearchCapability> capabilities,
        boolean configured,
        boolean routeRequired,
        Set<AiGatewayType> gatewayTypes) {

    public WebProviderDescriptor {
        capabilities = Set.copyOf(capabilities);
        gatewayTypes = Set.copyOf(gatewayTypes);
    }
}
