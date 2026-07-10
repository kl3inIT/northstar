package com.northstar.core.web;

import java.time.Duration;
import java.util.List;

public record WebResearchDefaults(
        boolean enabled,
        String searchProviderId,
        String pageReaderId,
        boolean fallbackEnabled,
        List<String> searchFallbackOrder,
        List<String> pageReaderFallbackOrder,
        Duration cacheTtl,
        long cacheMaxSize) {

    public WebResearchDefaults {
        searchProviderId = normalized(searchProviderId);
        pageReaderId = normalized(pageReaderId);
        searchFallbackOrder = searchFallbackOrder == null ? List.of() : List.copyOf(searchFallbackOrder);
        pageReaderFallbackOrder = pageReaderFallbackOrder == null ? List.of() : List.copyOf(pageReaderFallbackOrder);
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(15) : cacheTtl;
        cacheMaxSize = cacheMaxSize < 1 ? 200 : cacheMaxSize;
    }

    public static WebResearchDefaults disabled() {
        return new WebResearchDefaults(false, "", "", false, List.of(), List.of(),
                Duration.ofMinutes(15), 200);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }
}
