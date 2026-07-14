package com.northstar.integration.news.huggingnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.northstar.core.brief.BriefStoryDetail;
import com.northstar.core.cache.ExactCache;
import com.northstar.core.cache.ExactCacheNames;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tools.jackson.databind.ObjectMapper;

class HuggingNewsFeedProviderTests {

    @Test
    void cachedStoryDetailSkipsTheUpstreamRequest() throws IOException {
        ObjectMapper json = new ObjectMapper();
        BriefStoryDetail detail = new HuggingNewsParser(json)
                .detail(fixture("huggingnews-detail-2026-07-12.json"));
        CacheManager caches = new ConcurrentMapCacheManager(ExactCacheNames.HUGGINGNEWS_DETAIL);
        ExactCache.<String, BriefStoryDetail>from(caches, ExactCacheNames.HUGGINGNEWS_DETAIL)
                .put("ai/cached-story", detail);
        HttpClient http = mock(HttpClient.class);
        HuggingNewsProperties properties = new HuggingNewsProperties(
                "https://huggingnews.com", Duration.ofSeconds(5), Duration.ofSeconds(15),
                Duration.ofMinutes(5), Duration.ofMinutes(30), 200);
        HuggingNewsFeedProvider provider = new HuggingNewsFeedProvider(
                properties, json, http, Clock.systemUTC(), caches);

        assertThat(provider.story("ai", "cached-story")).isEqualTo(detail);
        verifyNoInteractions(http);
    }

    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/" + name)) {
            if (input == null) throw new IOException("Missing test fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
