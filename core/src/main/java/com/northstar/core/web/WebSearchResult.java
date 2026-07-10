package com.northstar.core.web;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record WebSearchResult(
        String query,
        String providerId,
        String answer,
        List<WebSource> sources,
        Instant fetchedAt,
        @Nullable String fallbackFrom) {
}
