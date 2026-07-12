package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;

/** A gateway-specific identifier for a non-chat AI capability. */
public record AiCapabilityTarget(
        String gatewayId,
        String id,
        String displayName,
        AiGatewayCapability capability) {
}
