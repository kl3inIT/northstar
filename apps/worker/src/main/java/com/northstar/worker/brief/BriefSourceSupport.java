package com.northstar.worker.brief;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BriefSourceSupport {

    private BriefSourceSupport() {
    }

    static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> values ? values : Map.of();
    }

    static String string(Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    static Instant instant(Object value) {
        String text = string(value);
        if (text.isBlank()) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException alsoIgnored) {
                try {
                    return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (DateTimeParseException unavailable) {
                    return null;
                }
            }
        }
    }

    static String plainText(String value, int limit) {
        String clean = value == null ? "" : value
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ").strip();
        if (clean.length() <= limit) return clean;
        return clean.substring(0, Math.max(0, limit - 3)).stripTrailing() + "...";
    }

    static boolean recent(Instant publishedAt, Instant since) {
        return publishedAt == null || !publishedAt.isBefore(since);
    }

    static boolean matchesTopics(String text, List<String> topics) {
        if (topics.isEmpty()) return true;
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String topic : topics) {
            String phrase = topic.toLowerCase(Locale.ROOT).strip();
            if (!phrase.isBlank() && haystack.contains(phrase)) return true;
            for (String token : phrase.split("[^a-z0-9+#.]+")) {
                if (token.length() >= 4 && haystack.contains(token)) return true;
            }
        }
        return false;
    }

    static List<String> effectiveQueries(List<String> queries, List<String> topics, int max) {
        if (!queries.isEmpty()) return queries.stream().limit(max).toList();
        List<String> generated = new ArrayList<>();
        for (String topic : topics) {
            generated.add("latest important " + topic + " release announcement developer news");
            if (generated.size() == max) break;
        }
        return generated;
    }

    static URI uri(String base, String path, Map<String, String> query) {
        StringBuilder value = new StringBuilder(base.replaceAll("/+$", "")).append(path);
        if (!query.isEmpty()) {
            value.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) value.append('&');
                first = false;
                value.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return URI.create(value.toString());
    }

    static List<String> terms(List<String> topics) {
        Set<String> terms = new LinkedHashSet<>();
        for (String topic : topics) {
            for (String token : topic.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
                if (token.length() >= 3) terms.add(token);
            }
        }
        return List.copyOf(terms);
    }
}
