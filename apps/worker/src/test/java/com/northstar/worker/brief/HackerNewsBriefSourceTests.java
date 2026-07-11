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

class HackerNewsBriefSourceTests {

    @Test
    void filtersStoriesByTopicAndCarriesCommunitySignal() {
        BriefHttpClient http = mock(BriefHttpClient.class);
        when(http.get(any(), any(), anyMap())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0).toString();
            if (url.contains("topstories") || url.contains("beststories")) return "[42]";
            return """
                    {"type":"story","title":"Spring AI ships a new tool API",
                     "url":"https://spring.io/blog/tool-api","time":1783731600,
                     "score":120,"descendants":35,"by":"builder"}
                    """;
        });

        var result = new HackerNewsBriefSource(http, new ObjectMapper()).collect(request());

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(BriefKind.COMMUNITY);
            assertThat(item.score()).isEqualTo(155);
            assertThat(item.summary()).contains("120 points", "35 comments");
        });
    }

    private static BriefCollectionRequest request() {
        return new BriefCollectionRequest(Instant.parse("2026-07-10T00:00:00Z"), 6,
                List.of("Spring AI"), List.of(), List.of(), List.of(), List.of(), List.of(), 25);
    }
}
