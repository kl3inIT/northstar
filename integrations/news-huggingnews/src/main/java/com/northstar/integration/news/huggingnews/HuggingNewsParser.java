package com.northstar.integration.news.huggingnews;

import com.northstar.core.brief.BriefDay;
import com.northstar.core.brief.BriefEntity;
import com.northstar.core.brief.BriefFeed;
import com.northstar.core.brief.BriefFeedException;
import com.northstar.core.brief.BriefPreviousStory;
import com.northstar.core.brief.BriefSource;
import com.northstar.core.brief.BriefStory;
import com.northstar.core.brief.BriefStoryDetail;
import com.northstar.core.brief.BriefTag;
import com.northstar.core.brief.BriefTldrItem;
import com.northstar.core.brief.BriefTopicCount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class HuggingNewsParser {

    private static final String PROVIDER = "HuggingNews";

    private final HuggingNewsDataDecoder decoder;

    HuggingNewsParser(ObjectMapper json) {
        this.decoder = new HuggingNewsDataDecoder(json);
    }

    BriefFeed feed(String body) {
        JsonNode root = decoder.decode(body);
        JsonNode payload = root.path("recentStoryDays").path("data");
        JsonNode dayGroups = payload.path("dayGroups");
        if (!dayGroups.isArray()) throw changed("dayGroups");

        List<BriefDay> days = new ArrayList<>();
        List<BriefTldrItem> tldr = new ArrayList<>();
        for (JsonNode day : dayGroups) {
            List<BriefStory> stories = stories(day.path("stories"));
            days.add(new BriefDay(date(day, "dayKey"), topicCounts(day.path("routingTopicCounts")), stories));
            for (BriefStory story : stories) {
                if (tldr.size() == 8) break;
                if (!story.superseded()) {
                    tldr.add(new BriefTldrItem(story.id(), story.topic(), story.slug(),
                            story.title().replaceFirst("^UPDATE:\\s*", "")));
                }
            }
        }
        if (days.isEmpty()) throw changed("dayGroups[]");
        return new BriefFeed(PROVIDER, instant(payload, "lastUpdatedAt"), false,
                List.copyOf(tldr), List.copyOf(days));
    }

    BriefStoryDetail detail(String body) {
        JsonNode root = decoder.decode(body);
        JsonNode storyNode = root.path("focusedStory").path("data");
        JsonNode detail = root.path("focusedStoryDetail").path("data");
        if (!storyNode.isObject() || !detail.isObject()) throw changed("focusedStoryDetail");

        BriefStory story = story(storyNode);
        String summary = text(detail, "summary");
        if (summary.isBlank()) throw changed("summary");
        List<BriefEntity> entities = new ArrayList<>();
        for (JsonNode entity : detail.path("entities")) {
            String value = text(entity, "text");
            if (!value.isBlank() && value.length() <= 48) {
                entities.add(new BriefEntity(value, text(entity, "type")));
            }
        }
        List<BriefSource> sources = new ArrayList<>();
        for (JsonNode source : detail.path("selectedTweets")) {
            String url = text(source, "url");
            if (!url.startsWith("https://")) continue;
            sources.add(new BriefSource(text(source, "label"), text(source, "authorHandle"),
                    text(source, "bestBit"), text(source, "text"), url,
                    instant(source, "tweetedAt")));
        }
        return new BriefStoryDetail(story, summary, List.copyOf(entities),
                previous(detail.path("supersededStory")), List.copyOf(sources));
    }

    private static List<BriefStory> stories(JsonNode nodes) {
        if (!nodes.isArray()) throw changed("stories");
        List<BriefStory> stories = new ArrayList<>();
        for (JsonNode node : nodes) stories.add(story(node));
        return List.copyOf(stories);
    }

    private static BriefStory story(JsonNode node) {
        String id = text(node, "storyId");
        String slug = text(node, "slug");
        String title = text(node, "title");
        if (id.isBlank() || slug.isBlank() || title.isBlank()) throw changed("story identity");
        return new BriefStory(id, first(node.path("primaryRoutingTopic"),
                first(node.path("routingTopicKeys"), "ai")), slug, title,
                instant(node, "publishedAt"), integer(node.path("rank")),
                decimal(node.path("storyScore")), integer(node.path("tweetCount")),
                integer(node.path("authorCount")), node.path("isFreshStory").asBoolean(),
                node.path("isUpdateStory").asBoolean(), node.path("isSuperseded").asBoolean(),
                tags(node.path("topicTags")));
    }

    private static BriefPreviousStory previous(JsonNode node) {
        if (!node.isObject()) return null;
        String id = text(node, "storyId");
        String slug = text(node, "slug");
        String title = text(node, "title");
        if (id.isBlank() || slug.isBlank() || title.isBlank()) return null;
        return new BriefPreviousStory(id, first(node.path("routingTopicKeys"), "ai"), slug, title,
                instant(node, "publishedAt"), integer(node.path("tweetCount")),
                integer(node.path("authorCount")));
    }

    private static List<BriefTag> tags(JsonNode nodes) {
        List<BriefTag> tags = new ArrayList<>();
        if (!nodes.isArray()) return List.of();
        for (JsonNode node : nodes) {
            String name = text(node, "name");
            String slug = text(node, "slug");
            if (!name.isBlank() && !slug.isBlank()) tags.add(new BriefTag(name, slug));
        }
        return List.copyOf(tags);
    }

    private static List<BriefTopicCount> topicCounts(JsonNode nodes) {
        List<BriefTopicCount> topics = new ArrayList<>();
        if (!nodes.isArray()) return List.of();
        for (JsonNode node : nodes) {
            String topic = text(node, "routingTopicKey");
            if (!topic.isBlank()) topics.add(new BriefTopicCount(topic, integer(node.path("count"))));
        }
        return List.copyOf(topics);
    }

    private static String first(JsonNode node, String fallback) {
        if (node.isString()) return node.stringValue();
        if (node.isArray() && !node.isEmpty() && node.path(0).isString()) return node.path(0).stringValue();
        return fallback;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue().strip() : "";
    }

    private static int integer(JsonNode node) {
        return node.isNumber() && node.canConvertToInt() ? node.intValue() : 0;
    }

    private static double decimal(JsonNode node) {
        return node.isNumber() ? node.doubleValue() : 0;
    }

    private static LocalDate date(JsonNode node, String field) {
        try {
            return LocalDate.parse(text(node, field));
        } catch (DateTimeParseException exception) {
            throw changed(field);
        }
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode value = node.path(field);
        try {
            if (value.isNumber()) return Instant.ofEpochMilli(value.longValue());
            String text = value.isString() ? value.stringValue() : "";
            if (!text.isBlank()) return Instant.parse(text);
        } catch (DateTimeParseException | ArithmeticException exception) {
            throw changed(field);
        }
        throw changed(field);
    }

    private static BriefFeedException changed(String field) {
        return new BriefFeedException("HuggingNews response is missing or changed field: " + field);
    }
}
