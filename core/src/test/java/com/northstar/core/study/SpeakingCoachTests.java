package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakingCoachTests {

    private static List<SpeakingIeltsEstimate.Criterion> validCriteria() {
        return List.of(
                criterion("FC", "I enjoy"),
                criterion("LR", "learning English"),
                criterion("GRA", "I enjoy learning"),
                criterion("P", "English"));
    }

    private static SpeakingIeltsEstimate.Criterion criterion(String key, String quote) {
        return new SpeakingIeltsEstimate.Criterion(key, 6.0, 6.5, "MEDIUM", quote,
                "This conservative range is grounded in the supplied evidence.");
    }

    @Test
    void acceptsWhitespaceTolerantVerbatimTranscriptQuotes() {
        var feedback = new SpeakingContentFeedback(70, 62, 80, validCriteria(),
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Verb tense", "Yesterday I  go", "Yesterday I went")),
                "Unofficial IELTS-style estimate. Improve past-tense consistency.");

        assertThat(SpeakingCoach.structuralProblems(feedback,
                "Yesterday I\ngo to class and talk to my teacher. I enjoy learning English."))
                .isNull();
    }

    @Test
    void rejectsInventedQuotesInvalidScoresAndIeltsBandClaims() {
        var feedback = new SpeakingContentFeedback(101, 50, 70, validCriteria(),
                List.of(new SpeakingContentFeedback.SpokenError(
                        "Article errors", "a university", "the university")),
                "This equals IELTS band 6.");

        assertThat(SpeakingCoach.structuralProblems(feedback,
                "I study every day. I enjoy learning English."))
                .contains("vocabulary score")
                .contains("not verbatim")
                .contains("direct or official IELTS score claim");
    }

    @Test
    void evaluatorEvidenceIncludesQuestionTranscriptAndMeasuredDelivery() {
        var delivery = new SpokenAnswerResult("I enjoy learning English.", 82, 76, 71.0, List.of());
        var metrics = new SpeakingCoach.SpeakingMetrics(12.5, 4, 19.2);

        assertThat(SpeakingCoach.evidence("What do you enjoy learning?",
                delivery.transcript(), delivery, metrics))
                .contains("Question:\nWhat do you enjoy learning?")
                .contains("Transcript:\nI enjoy learning English.")
                .contains("pronunciation=82.0, fluency=76.0, prosody=71.0")
                .contains("durationSeconds=12.5, wordCount=4, wordsPerMinute=19.2");
    }
}
