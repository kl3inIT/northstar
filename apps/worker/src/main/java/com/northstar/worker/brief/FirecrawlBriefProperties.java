package com.northstar.worker.brief;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.brief.firecrawl")
record FirecrawlBriefProperties(
        @DefaultValue("https://api.firecrawl.dev") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("30s") Duration requestTimeout) {
}
