package com.northstar.core.ai;

import java.util.Set;

public enum AiGatewayType {
    OPENAI(Set.of(
            AiGatewayCapability.CHAT,
            AiGatewayCapability.WEB_SEARCH,
            AiGatewayCapability.SPEECH_TO_TEXT,
            AiGatewayCapability.TEXT_TO_SPEECH,
            AiGatewayCapability.REALTIME)),
    NINE_ROUTER(Set.of(
            AiGatewayCapability.CHAT,
            AiGatewayCapability.WEB_SEARCH,
            AiGatewayCapability.WEB_FETCH,
            AiGatewayCapability.SPEECH_TO_TEXT,
            AiGatewayCapability.TEXT_TO_SPEECH)),
    OPENAI_CHAT_COMPATIBLE(Set.of(AiGatewayCapability.CHAT));

    private final Set<AiGatewayCapability> capabilities;

    AiGatewayType(Set<AiGatewayCapability> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
    }

    public Set<AiGatewayCapability> capabilities() {
        return capabilities;
    }

    public boolean supports(AiGatewayCapability capability) {
        return capabilities.contains(capability);
    }
}
