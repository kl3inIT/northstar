package com.northstar.core.web;

import java.net.URI;

public record WebPageProviderResult(
        URI finalUrl,
        String title,
        String content,
        String contentType,
        boolean truncated) {
}
