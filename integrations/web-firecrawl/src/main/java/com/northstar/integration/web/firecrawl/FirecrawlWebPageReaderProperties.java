package com.northstar.integration.web.firecrawl;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.web.firecrawl")
public record FirecrawlWebPageReaderProperties(
        @DefaultValue("https://api.firecrawl.dev") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("2097152") int maxResponseBytes,
        @DefaultValue("40000") int maxCharacters,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("60s") Duration requestTimeout) {
}
