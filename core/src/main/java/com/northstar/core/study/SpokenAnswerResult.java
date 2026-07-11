package com.northstar.core.study;

import java.util.List;

/** Azure-measured delivery only; content feedback is produced by SpeakingCoach. */
public record SpokenAnswerResult(
        String transcript,
        double pronunciation,
        double fluency,
        Double prosody,
        List<WordScore> words) {

    public SpokenAnswerResult {
        transcript = transcript == null ? "" : transcript;
        words = words == null ? List.of() : List.copyOf(words);
    }
}
