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

class BlueskyBriefSourceTests {

    @Test
    void mapsARecentAuthorPostToAPeopleCandidate() {
        BriefHttpClient http = mock(BriefHttpClient.class);
        when(http.get(any(), any(), anyMap())).thenReturn("""
                {"feed":[{"post":{
                  "uri":"at://did:plc:test/app.bsky.feed.post/3abc",
                  "record":{"text":"Codex adds a safer execution mode","createdAt":"2026-07-11T01:00:00Z"},
                  "author":{"handle":"builder.bsky.social","displayName":"Builder"},
                  "likeCount":20,"repostCount":5,"replyCount":3
                }}]}
                """);

        var result = new BlueskyBriefSource(http, new ObjectMapper()).collect(request());

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(BriefKind.PEOPLE);
            assertThat(item.url()).isEqualTo("https://bsky.app/profile/builder.bsky.social/post/3abc");
            assertThat(item.author()).isEqualTo("Builder");
            assertThat(item.score()).isEqualTo(28);
        });
    }

    private static BriefCollectionRequest request() {
        return new BriefCollectionRequest(Instant.parse("2026-07-10T00:00:00Z"), 6,
                List.of("Codex"), List.of(), List.of(), List.of(), List.of(),
                List.of("builder.bsky.social"), 25);
    }
}
