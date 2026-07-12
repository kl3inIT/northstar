package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakingCoachTests {

    @Test
    void acceptsWhitespaceTolerantVerbatimTranscriptQuotes() {
        var feedback = new SpeakingContentFeedback(70, 62, 80, List.of(),
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Verb tense", "Yesterday I  go", "Yesterday I went")),
                "Unofficial content feedback. Improve past-tense consistency.");

        assertThat(SpeakingCoach.structuralProblems(feedback,
                "Yesterday I\ngo to class and talk to my teacher.")).isNull();
    }

    @Test
    void rejectsInventedQuotesInvalidScoresAndIeltsBandClaims() {
        var feedback = new SpeakingContentFeedback(101, 50, 70, List.of(),
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Article errors", "a university", "the university")),
                "This equals IELTS band 6.");

        assertThat(SpeakingCoach.structuralProblems(feedback, "I study every day."))
                .contains("vocabulary score")
                .contains("not verbatim")
                .contains("IELTS band");
    }

    @Test
    void evaluatorEvidenceIncludesQuestionTranscriptAndMeasuredDelivery() {
        var delivery = new SpokenAnswerResult("I enjoy learning English.", 82, 76, 71.0, List.of());

        assertThat(SpeakingCoach.evidence("What do you enjoy learning?",
                delivery.transcript(), delivery))
                .contains("Question:\nWhat do you enjoy learning?")
                .contains("Transcript:\nI enjoy learning English.")
                .contains("pronunciation=82.0, fluency=76.0, prosody=71.0");
    }
}
