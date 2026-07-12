package com.northstar.core.web;

import com.northstar.core.ai.AiGatewayType;
import java.util.Set;

public interface WebSearchProvider {

    String id();

    String displayName();

    boolean configured();

    WebSearchProviderResult search(WebSearchRequest request);

    default boolean routeRequired() {
        return false;
    }

    default Set<AiGatewayType> gatewayTypes() {
        return Set.of();
    }

    default boolean configured(WebProviderRoute route) {
        return configured();
    }

    default WebSearchProviderResult search(WebSearchRequest request, WebProviderRoute route) {
        return search(request);
    }
}
