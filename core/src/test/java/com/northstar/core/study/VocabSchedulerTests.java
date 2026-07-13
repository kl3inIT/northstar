package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VocabSchedulerTests {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    void newCardRatingsProduceFourRealLearningOutcomes() {
        VocabSchedulingCard card = fresh();
        Map<VocabReviewLog.Rating, VocabScheduler.Outcome> outcomes = new EnumMap<>(
                VocabReviewLog.Rating.class);
        for (VocabReviewLog.Rating rating : VocabReviewLog.Rating.values()) {
            outcomes.put(rating, VocabScheduler.schedule(card, rating, NOW));
        }

        assertThat(outcomes.get(VocabReviewLog.Rating.AGAIN).dueAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(outcomes.get(VocabReviewLog.Rating.HARD).dueAt()).isEqualTo(NOW.plusSeconds(330));
        assertThat(outcomes.get(VocabReviewLog.Rating.GOOD).dueAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(outcomes.get(VocabReviewLog.Rating.EASY).state())
                .isEqualTo(VocabSchedulingState.REVIEW);
        assertThat(outcomes.values()).extracting(VocabScheduler.Outcome::dueAt)
                .doesNotHaveDuplicates();
    }

    @Test
    void previewAndPersistedOutcomeAreDeterministicWithFuzzing() {
        VocabSchedulingCard card = fresh();
        VocabScheduler.Outcome first = VocabScheduler.schedule(card, VocabReviewLog.Rating.EASY, NOW);
        VocabScheduler.Outcome second = VocabScheduler.schedule(card, VocabReviewLog.Rating.EASY, NOW);

        assertThat(second).isEqualTo(first);
        assertThat(VocabScheduler.previews(card, NOW))
                .filteredOn(preview -> preview.rating() == VocabReviewLog.Rating.EASY)
                .singleElement()
                .satisfies(preview -> assertThat(preview.dueAt()).isEqualTo(first.dueAt()));
    }

    @Test
    void onlyAgainFromReviewIsALapse() {
        VocabSchedulingCard learning = fresh();
        assertThat(VocabScheduler.isLapse(learning, VocabReviewLog.Rating.AGAIN)).isFalse();

        learning.apply(VocabScheduler.schedule(learning, VocabReviewLog.Rating.EASY, NOW), false);
        assertThat(learning.getState()).isEqualTo(VocabSchedulingState.REVIEW);
        assertThat(VocabScheduler.isLapse(learning, VocabReviewLog.Rating.AGAIN)).isTrue();
        assertThat(VocabScheduler.isLapse(learning, VocabReviewLog.Rating.HARD)).isFalse();
    }

    @Test
    void leechStartsAtEighthReviewLapse() {
        VocabSchedulingCard card = fresh();
        card.apply(VocabScheduler.schedule(card, VocabReviewLog.Rating.EASY, NOW), false);
        for (int index = 0; index < VocabScheduler.LEECH_THRESHOLD; index++) {
            card.apply(new VocabScheduler.Outcome(VocabSchedulingState.REVIEW, null, 1.0, 5.0,
                    NOW.plusSeconds(index + 1), NOW.plusSeconds(index + 1)), true);
        }

        assertThat(card.getLapseCount()).isEqualTo(8);
        assertThat(card.isLeech()).isTrue();
    }

    @Test
    void intervalLabelsStayCompact() {
        assertThat(VocabScheduler.intervalLabel(java.time.Duration.ofMinutes(10))).isEqualTo("10m");
        assertThat(VocabScheduler.intervalLabel(java.time.Duration.ofDays(14))).isEqualTo("14d");
        assertThat(VocabScheduler.intervalLabel(java.time.Duration.ofDays(90))).isEqualTo("3mo");
    }

    private static VocabSchedulingCard fresh() {
        return new VocabSchedulingCard(UUID.fromString("f9671dc8-02c8-4d65-8e6c-030922659b81"),
                UUID.randomUUID(), VocabReviewDirection.RECOGNITION, NOW);
    }
}
