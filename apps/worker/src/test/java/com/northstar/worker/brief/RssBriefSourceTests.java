package com.northstar.worker.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssBriefSourceTests {

    @Test
    void parsesARecentOfficialAtomEntry() {
        BriefHttpClient http = mock(BriefHttpClient.class);
        when(http.get(any(), any(), anyMap())).thenReturn("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>OpenAI News</title>
                  <entry>
                    <title>Codex gets a safer execution mode</title>
                    <link rel="alternate" href="https://openai.com/news/codex-safe" />
                    <updated>2026-07-11T01:00:00Z</updated>
                    <author><name>OpenAI</name></author>
                    <summary>New controls for long-running coding tasks.</summary>
                  </entry>
                </feed>
                """);

        var result = new RssBriefSource(http).collect(request(
                List.of("Codex"), List.of("https://openai.com/news/rss.xml"), 25));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(BriefKind.OFFICIAL);
            assertThat(item.title()).contains("Codex");
            assertThat(item.author()).isEqualTo("OpenAI");
        });
        assertThat(result.metrics()).containsEntry("failedFeeds", 0L);
    }

    private static BriefCollectionRequest request(List<String> topics, List<String> feeds, int budget) {
        return new BriefCollectionRequest(Instant.parse("2026-07-10T00:00:00Z"), 10, topics, List.of(),
                List.of(), List.of(), feeds, List.of(), budget);
    }
}
