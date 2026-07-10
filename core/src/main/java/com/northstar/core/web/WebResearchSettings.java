package com.northstar.core.web;

public record WebResearchSettings(
        boolean enabled,
        String searchProviderId,
        String pageReaderId,
        boolean fallbackEnabled,
        boolean overridden) {
}
