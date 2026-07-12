package com.northstar.core.web;

import com.northstar.core.ai.AiGatewayType;
import java.net.URI;
import java.util.Set;

public interface WebPageReader {

    String id();

    String displayName();

    boolean configured();

    boolean supports(URI url);

    WebPageProviderResult read(WebPageRequest request);

    default boolean routeRequired() {
        return false;
    }

    default Set<AiGatewayType> gatewayTypes() {
        return Set.of();
    }

    default boolean configured(WebProviderRoute route) {
        return configured();
    }

    default WebPageProviderResult read(WebPageRequest request, WebProviderRoute route) {
        return read(request);
    }
}
