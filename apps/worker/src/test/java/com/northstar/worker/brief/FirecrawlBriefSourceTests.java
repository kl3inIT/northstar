package com.northstar.worker.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.brief.BriefCollectionRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class FirecrawlBriefSourceTests {

    @Test
    void capsQueriesToTheCreditBudgetAndUsesCheapScrapeOptions() {
        BriefHttpClient http = mock(BriefHttpClient.class);
        when(http.postJson(any(), anyString(), anyMap(), any())).thenReturn("""
                {"success":true,"creditsUsed":5,"data":{"web":[{
                  "title":"Spring AI release",
                  "url":"https://spring.io/blog/release",
                  "description":"Release notes",
                  "markdown":"# Spring AI\\nA focused release for tool calling."
                }]}}
                """);
        var source = new FirecrawlBriefSource(http, new ObjectMapper(),
                new FirecrawlBriefProperties("https://api.firecrawl.dev", "fc-test", Duration.ofSeconds(30)));

        var result = source.collect(request(10));

        assertThat(result.metrics()).containsEntry("creditsUsed", 10).containsEntry("creditBudget", 10);
        assertThat(result.items()).hasSize(2);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(http, times(2)).postJson(any(), payload.capture(), anyMap(), any());
        assertThat(payload.getAllValues()).allSatisfy(body -> assertThat(body)
                .contains("\"limit\":3", "\"proxy\":\"basic\"", "\"formats\":[{\"type\":\"markdown\"}]",
                        "\"sources\":[{\"type\":\"web\"}]", "\"ignoreInvalidURLs\":true")
                .doesNotContain("enhanced", "agent", "json"));
    }

    private static BriefCollectionRequest request(int budget) {
        return new BriefCollectionRequest(Instant.parse("2026-07-10T00:00:00Z"), 10,
                List.of("Codex", "Claude Code", "Flutter", "Spring AI"), List.of(), List.of(),
                List.of(), List.of(), List.of(), budget);
    }
}
