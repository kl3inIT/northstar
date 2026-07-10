package com.northstar.core.web;

import java.util.Set;

public record WebProviderDescriptor(
        String id,
        String displayName,
        Set<WebResearchCapability> capabilities,
        boolean configured) {

    public WebProviderDescriptor {
        capabilities = Set.copyOf(capabilities);
    }
}
