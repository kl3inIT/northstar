package com.northstar.worker.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GitHubBriefSourceTests {

    @Test
    void returnsRecentPublishedReleasesAndSkipsDrafts() {
        BriefHttpClient http = mock(BriefHttpClient.class);
        when(http.get(any(), any(), anyMap())).thenReturn("""
                [{
                  "name":"Codex 2.0",
                  "tag_name":"v2.0.0",
                  "html_url":"https://github.com/openai/codex/releases/tag/v2.0.0",
                  "body":"Safer long-running tasks.",
                  "draft":false,
                  "published_at":"2026-07-11T01:00:00Z",
                  "author":{"login":"openai"}
                },{
                  "name":"Hidden draft",
                  "draft":true,
                  "published_at":"2026-07-11T02:00:00Z"
                }]
                """);

        var result = new GitHubBriefSource(http, new ObjectMapper(), new GitHubBriefProperties(""))
                .collect(request());

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(BriefKind.OFFICIAL);
            assertThat(item.title()).isEqualTo("Codex 2.0");
            assertThat(item.source()).contains("openai/codex");
        });
    }

    private static BriefCollectionRequest request() {
        return new BriefCollectionRequest(Instant.parse("2026-07-10T00:00:00Z"), 6,
                List.of(), List.of(), List.of(), List.of("openai/codex"), List.of(), List.of(), 25);
    }
}
