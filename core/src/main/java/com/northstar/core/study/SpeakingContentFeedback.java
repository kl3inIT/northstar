package com.northstar.core.study;

import java.util.List;

public record SpeakingContentFeedback(
        double vocabulary,
        double grammar,
        double topic,
        List<SpokenError> topErrors,
        String summary) {

    public SpeakingContentFeedback {
        topErrors = topErrors == null ? List.of() : List.copyOf(topErrors);
        summary = summary == null ? "" : summary;
    }

    public record SpokenError(String label, String quote, String fix) {
    }
}
