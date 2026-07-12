package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakingEstimatePolicyTests {

    private static SpeakingIeltsEstimate.Criterion criterion(String key, double min, double max,
            String quote) {
        return new SpeakingIeltsEstimate.Criterion(key, min, max, "MEDIUM", quote,
                "The quoted evidence and measured delivery support this conservative range.");
    }

    @Test
    void acceptsFourGroundedHalfBandRangesAndAggregatesInOfficialOrder() {
        List<SpeakingIeltsEstimate.Criterion> criteria = List.of(
                criterion("P", 6.0, 6.5, "learning English"),
                criterion("LR", 6.5, 7.0, "enjoy learning"),
                criterion("FC", 5.5, 6.0, "I enjoy"),
                criterion("GRA", 6.0, 6.5, "I enjoy learning"));

        assertThat(SpeakingEstimatePolicy.problems(criteria, "I enjoy learning English every day."))
                .isNull();
        SpeakingIeltsEstimate estimate = SpeakingEstimatePolicy.aggregate(criteria);

        assertThat(estimate.criteria()).extracting(SpeakingIeltsEstimate.Criterion::key)
                .containsExactly("FC", "LR", "GRA", "P");
        assertThat(estimate.overallMin()).isEqualTo(6.0);
        assertThat(estimate.overallMax()).isEqualTo(6.5);
        assertThat(estimate.confidence()).isEqualTo("LOW");
        assertThat(estimate.label()).isEqualTo(SpeakingEstimatePolicy.LABEL);
    }

    @Test
    void rejectsMissingDuplicateInvalidAndInventedEvidence() {
        List<SpeakingIeltsEstimate.Criterion> criteria = List.of(
                criterion("FC", 6.2, 7.5, "invented quote"),
                criterion("FC", 6.0, 6.5, "I study"),
                new SpeakingIeltsEstimate.Criterion("LR", 6.0, 6.5, "HIGH", "I study", ""),
                criterion("OTHER", 6.0, 6.5, "I study"));

        assertThat(SpeakingEstimatePolicy.problems(criteria, "I study every day."))
                .contains("Duplicate")
                .contains("Invalid band range")
                .contains("LOW or MEDIUM")
                .contains("not verbatim")
                .contains("Missing IELTS-style criterion: GRA")
                .contains("Missing IELTS-style criterion: P")
                .contains("Unknown IELTS-style criterion");
        assertThatThrownBy(() -> SpeakingEstimatePolicy.aggregate(criteria))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
