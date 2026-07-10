package com.northstar.core.web;

import java.util.List;

public record WebSearchRequest(
        String query,
        WebRecency recency,
        int maxResults,
        List<String> allowedDomains,
        List<String> blockedDomains) {

    private static final int MAX_QUERY_LENGTH = 2_000;
    private static final int MAX_DOMAINS = 20;

    public WebSearchRequest {
        query = query == null ? "" : query.strip();
        if (query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
            throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST,
                    "Search query must contain 1 to " + MAX_QUERY_LENGTH + " characters");
        }
        recency = recency == null ? WebRecency.ANY : recency;
        if (maxResults < 1 || maxResults > 10) {
            throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST,
                    "maxResults must be between 1 and 10");
        }
        allowedDomains = normalizedDomains(allowedDomains);
        blockedDomains = normalizedDomains(blockedDomains);
    }

    public static WebSearchRequest of(String query) {
        return new WebSearchRequest(query, WebRecency.ANY, 5, List.of(), List.of());
    }

    private static List<String> normalizedDomains(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_DOMAINS) {
            throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST,
                    "At most " + MAX_DOMAINS + " domains may be supplied");
        }
        return values.stream()
                .map(value -> value == null ? "" : value.strip().toLowerCase())
                .map(value -> value.startsWith("www.") ? value.substring(4) : value)
                .peek(value -> {
                    if (value.isBlank() || value.contains(":") || value.contains("/")
                            || !value.matches("[a-z0-9.-]+")) {
                        throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST,
                                "Domains must be host names without a scheme or path");
                    }
                })
                .distinct()
                .toList();
    }
}
