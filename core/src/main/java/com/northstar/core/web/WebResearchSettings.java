package com.northstar.core.web;

public record WebResearchSettings(
        boolean enabled,
        String searchProviderId,
        WebProviderRoute searchRoute,
        String pageReaderId,
        WebProviderRoute pageReaderRoute,
        boolean fallbackEnabled,
        boolean overridden) {

    public WebResearchSettings {
        searchRoute = searchRoute == null ? WebProviderRoute.none() : searchRoute;
        pageReaderRoute = pageReaderRoute == null ? WebProviderRoute.none() : pageReaderRoute;
    }
}
