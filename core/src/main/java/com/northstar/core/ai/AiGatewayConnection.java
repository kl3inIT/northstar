package com.northstar.core.ai;

import java.time.Duration;

/** Server-only resolved connection. Never expose this type from an HTTP controller. */
public record AiGatewayConnection(
        String id,
        String displayName,
        AiGatewayType type,
        String baseUrl,
        String apiKey,
        Duration timeout) {

    public boolean supports(AiGatewayCapability capability) {
        return type.supports(capability);
    }
}
