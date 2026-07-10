package com.northstar.core.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record WebSource(String title, String url, String snippet, @Nullable Instant publishedAt) {

    public WebSource {
        title = title == null || title.isBlank() ? url : title.strip();
        url = url == null ? "" : url.strip();
        snippet = snippet == null ? "" : snippet.strip();
    }
}
