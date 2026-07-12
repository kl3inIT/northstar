package com.northstar.integration.news.huggingnews;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.briefs.huggingnews")
public record HuggingNewsProperties(
        @DefaultValue("https://huggingnews.com") String baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration requestTimeout,
        @DefaultValue("5m") Duration feedCacheTtl,
        @DefaultValue("30m") Duration detailCacheTtl) {
}
