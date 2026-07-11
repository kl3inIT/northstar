package com.northstar.core.web;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.web")
public record WebResearchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("openai") String defaultSearchProvider,
        @DefaultValue("direct") String defaultPageReader,
        @DefaultValue("false") boolean fallbackEnabled,
        List<String> searchFallbackOrder,
        List<String> pageReaderFallbackOrder,
        @DefaultValue("15m") Duration cacheTtl,
        @DefaultValue("200") long cacheMaxSize) {

    public WebResearchProperties {
        searchFallbackOrder = searchFallbackOrder == null ? List.of() : List.copyOf(searchFallbackOrder);
        pageReaderFallbackOrder = pageReaderFallbackOrder == null ? List.of() : List.copyOf(pageReaderFallbackOrder);
    }
}
