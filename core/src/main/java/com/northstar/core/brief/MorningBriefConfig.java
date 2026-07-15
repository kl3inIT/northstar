package com.northstar.core.brief;

import java.util.List;

public record MorningBriefConfig(
        String language,
        int lookbackHours,
        int maxItems,
        List<String> topics,
        List<String> queries,
        List<String> blockedDomains,
        boolean saveAsNote,
        List<String> sourceIds,
        List<String> githubRepositories,
        List<String> feedUrls,
        List<String> blueskyHandles,
        Integer firecrawlCreditBudget) {

    private static final List<String> DEFAULT_SOURCES = List.of(
            "github", "rss", "hacker-news", "bluesky", "firecrawl");
    private static final List<String> DEFAULT_REPOSITORIES = List.of(
            "openai/codex",
            "anthropics/claude-code",
            "flutter/flutter",
            "dart-lang/sdk",
            "spring-projects/spring-ai",
            "facebook/react");
    private static final List<String> DEFAULT_FEEDS = List.of(
            "https://openai.com/news/rss.xml",
            "https://simonwillison.net/atom/everything/",
            "https://github.blog/feed/",
            "https://spring.io/blog.atom",
            "https://react.dev/rss.xml",
            "https://inside.java/feed.xml");
    private static final List<String> DEFAULT_BLUESKY_HANDLES = List.of(
            "bcherny.bsky.social",
            "simonwillison.net",
            "gergely.pragmaticengineer.com",
            "addyosmani.bsky.social");

    public MorningBriefConfig {
        language = language == null ? "" : language.strip();
        topics = clean(topics);
        queries = clean(queries);
        blockedDomains = clean(blockedDomains).stream().map(MorningBriefConfig::lower).toList();
        sourceIds = sourceIds == null ? DEFAULT_SOURCES : clean(sourceIds).stream()
                .map(MorningBriefConfig::lower).toList();
        githubRepositories = githubRepositories == null ? DEFAULT_REPOSITORIES : clean(githubRepositories);
        feedUrls = feedUrls == null ? DEFAULT_FEEDS : clean(feedUrls);
        blueskyHandles = blueskyHandles == null ? DEFAULT_BLUESKY_HANDLES : clean(blueskyHandles).stream()
                .map(MorningBriefConfig::lower).toList();
        firecrawlCreditBudget = firecrawlCreditBudget == null || firecrawlCreditBudget == 0
                ? 25 : firecrawlCreditBudget;
    }

    /** Compatibility constructor for persisted V1 configs and focused tests. */
    public MorningBriefConfig(String language, int lookbackHours, int maxItems, List<String> topics,
            List<String> queries, List<String> blockedDomains, boolean saveAsNote) {
        this(language, lookbackHours, maxItems, topics, queries, blockedDomains, saveAsNote,
                null, null, null, null, 25);
    }

    public static MorningBriefConfig defaults() {
        return new MorningBriefConfig("vi", 24, 6,
                List.of("AI agents", "Claude Code", "Codex", "Flutter", "Dart", "Java", "Spring AI", "React"),
                List.of(), List.of(), true, DEFAULT_SOURCES, DEFAULT_REPOSITORIES,
                DEFAULT_FEEDS, DEFAULT_BLUESKY_HANDLES, 25);
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    // Locale.ROOT, matching how the handler normalizes hosts/source ids — the
    // default locale (e.g. tr-TR) would lowercase 'I' to a dotless 'ı'.
    private static String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
