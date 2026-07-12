package com.northstar.api.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.brief.BriefFeed;
import com.northstar.core.brief.BriefFeedProvider;
import com.northstar.core.brief.BriefStory;
import com.northstar.core.brief.BriefStoryDetail;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BriefControllerTests {

    private final BriefFeedProvider provider = mock(BriefFeedProvider.class);
    private final BriefController controller = new BriefController(provider);

    @Test
    void delegatesFeedWithoutLeakingProviderSpecificTypes() {
        BriefFeed expected = new BriefFeed("HuggingNews", Instant.parse("2026-07-12T00:00:00Z"),
                false, List.of(), List.of());
        when(provider.feed()).thenReturn(expected);

        assertThat(controller.huggingNews()).isSameAs(expected);
        verify(provider).feed();
    }

    @Test
    void delegatesStoryRouteToProvider() {
        BriefStory story = new BriefStory("story-1", "ai", "story-slug", "Story title",
                Instant.parse("2026-07-12T00:00:00Z"), 1, 0.8, 4, 3,
                true, false, false, List.of());
        BriefStoryDetail expected = new BriefStoryDetail(story, "Summary", List.of(), null, List.of());
        when(provider.story("ai", "story-slug")).thenReturn(expected);

        assertThat(controller.huggingNewsStory("ai", "story-slug")).isSameAs(expected);
        verify(provider).story("ai", "story-slug");
    }
}
