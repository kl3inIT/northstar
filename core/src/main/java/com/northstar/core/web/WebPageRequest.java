package com.northstar.core.web;

import java.net.URI;

public record WebPageRequest(URI url) {

    public WebPageRequest {
        if (url == null) {
            throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST, "URL is required");
        }
    }

    public static WebPageRequest of(String url) {
        try {
            return new WebPageRequest(URI.create(url == null ? "" : url.strip()));
        } catch (IllegalArgumentException exception) {
            throw new WebResearchException(WebResearchFailureCode.INVALID_REQUEST,
                    "URL is invalid", exception);
        }
    }
}
