package com.northstar.core.brief;

import java.util.List;

public record MorningBriefConfig(
        String language,
        int lookbackHours,
        int maxItems,
        List<String> topics,
        List<String> queries,
        List<String> blockedDomains,
        boolean saveAsNote) {

    public MorningBriefConfig {
        language = language == null ? "" : language.strip();
        topics = clean(topics);
        queries = clean(queries);
        blockedDomains = clean(blockedDomains).stream().map(String::toLowerCase).toList();
    }

    public static MorningBriefConfig defaults() {
        return new MorningBriefConfig("vi", 24, 6,
                List.of("AI agents", "Java", "Spring AI"), List.of(), List.of(), true);
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).distinct().toList();
    }
}
