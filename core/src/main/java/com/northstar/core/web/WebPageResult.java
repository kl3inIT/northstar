package com.northstar.core.web;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record WebPageResult(
        URI requestedUrl,
        URI finalUrl,
        String readerId,
        String title,
        String content,
        String contentType,
        boolean truncated,
        Instant fetchedAt,
        @Nullable String fallbackFrom) {
}
