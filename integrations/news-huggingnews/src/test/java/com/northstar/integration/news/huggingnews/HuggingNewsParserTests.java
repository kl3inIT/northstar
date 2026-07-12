package com.northstar.integration.news.huggingnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.northstar.core.brief.BriefFeed;
import com.northstar.core.brief.BriefStoryDetail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HuggingNewsParserTests {

    private final HuggingNewsParser parser = new HuggingNewsParser(new ObjectMapper());

    @Test
    void parsesCapturedPublicFeedPageData() throws IOException {
        BriefFeed feed = parser.feed(fixture("huggingnews-feed-2026-07-12.json"));

        assertThat(feed.provider()).isEqualTo("HuggingNews");
        assertThat(feed.updatedAt()).hasToString("2026-07-12T14:33:22.421Z");
        assertThat(feed.days()).singleElement().satisfies(day -> {
            assertThat(day.date()).hasToString("2026-07-12");
            assertThat(day.topics()).singleElement().satisfies(topic -> {
                assertThat(topic.topic()).isEqualTo("ai");
                assertThat(topic.count()).isOne();
            });
            assertThat(day.stories()).singleElement().satisfies(story -> {
                assertThat(story.slug()).endsWith("a846ee45");
                assertThat(story.topic()).isEqualTo("ai");
                assertThat(story.tweetCount()).isEqualTo(127);
                assertThat(story.tags()).extracting("name").containsExactly("AI", "AI Regulation");
            });
        });
        assertThat(feed.tldr()).singleElement().satisfies(item ->
                assertThat(item.text()).startsWith("Apple Trade Secrets Lawsuit"));
    }

    @Test
    void parsesCapturedPublicStoryDetailPageData() throws IOException {
        BriefStoryDetail detail = parser.detail(fixture("huggingnews-detail-2026-07-12.json"));

        assertThat(detail.summary()).contains("trade-secrets lawsuit");
        assertThat(detail.entities()).extracting("text").containsExactly("Apple", "OpenAI");
        assertThat(detail.sources()).singleElement().satisfies(source -> {
            assertThat(source.label()).isEqualTo("Source");
            assertThat(source.author()).isEqualTo("jukan05");
            assertThat(source.url()).startsWith("https://x.com/");
        });
        assertThat(detail.previousStory()).isNotNull();
        assertThat(detail.previousStory().title()).startsWith("UPDATE:");
    }

    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/" + name)) {
            if (input == null) throw new IOException("Missing test fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
