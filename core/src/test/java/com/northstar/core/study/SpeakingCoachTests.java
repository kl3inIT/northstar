package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakingCoachTests {

    @Test
    void acceptsWhitespaceTolerantVerbatimTranscriptQuotes() {
        var feedback = new SpeakingContentFeedback(70, 62, 80,
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Verb tense", "Yesterday I  go", "Yesterday I went")),
                "Unofficial content feedback. Improve past-tense consistency.");

        assertThat(SpeakingCoach.structuralProblems(feedback,
                "Yesterday I\ngo to class and talk to my teacher.")).isNull();
    }

    @Test
    void rejectsInventedQuotesInvalidScoresAndIeltsBandClaims() {
        var feedback = new SpeakingContentFeedback(101, 50, 70,
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Article errors", "a university", "the university")),
                "This equals IELTS band 6.");

        assertThat(SpeakingCoach.structuralProblems(feedback, "I study every day."))
                .contains("vocabulary score")
                .contains("not verbatim")
                .contains("IELTS band");
    }
}
