package com.northstar.core.brief;

import java.time.Instant;

/** One normalized public item returned by a Morning Brief source adapter. */
public record BriefCandidate(
        BriefKind kind,
        String title,
        String url,
        String summary,
        String source,
        String author,
        Instant publishedAt,
        int score) {

    public BriefCandidate {
        kind = kind == null ? BriefKind.COMMUNITY : kind;
        title = clean(title);
        url = clean(url);
        summary = clean(summary);
        source = clean(source);
        author = clean(author);
        score = Math.max(0, score);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
