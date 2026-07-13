package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

class VocabServiceFsrsTests {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    void reviewAppliesPreviewedOutcomeAndBuriesSiblingToNextLocalDay() {
        Fixture fixture = fixture(true);

        VocabScheduler.Outcome expected = VocabScheduler.schedule(fixture.recognition,
                VocabReviewLog.Rating.GOOD, NOW);
        fixture.service.recordReview(fixture.card.getId(), VocabReviewDirection.RECOGNITION,
                VocabReviewLog.Rating.GOOD, VocabReviewLog.ReviewSource.MANUAL,
                NOW, 0, ZoneId.of("Asia/Bangkok"));

        assertThat(fixture.recognition.getState()).isEqualTo(expected.state());
        assertThat(fixture.recognition.getDueAt()).isEqualTo(expected.dueAt());
        assertThat(fixture.production.getBuriedUntil())
                .isEqualTo(Instant.parse("2026-07-12T17:00:00Z"));
        ArgumentCaptor<VocabReviewLog> log = ArgumentCaptor.forClass(VocabReviewLog.class);
        verify(fixture.reviews).save(log.capture());
        assertThat(log.getValue().getRating()).isEqualTo(VocabReviewLog.Rating.GOOD);
        assertThat(log.getValue().isLapse()).isFalse();
    }

    @Test
    void againFromReviewEntersRelearningAndCountsALapse() {
        Fixture fixture = fixture(false);
        fixture.recognition.apply(VocabScheduler.schedule(fixture.recognition,
                VocabReviewLog.Rating.EASY, NOW.minusSeconds(172_800)), false);
        Instant reviewTime = NOW.plusSeconds(172_800);

        fixture.service.recordReview(fixture.card.getId(), VocabReviewDirection.RECOGNITION,
                VocabReviewLog.Rating.AGAIN, VocabReviewLog.ReviewSource.MANUAL,
                reviewTime, 0, ZoneId.of("UTC"));

        assertThat(fixture.recognition.getState()).isEqualTo(VocabSchedulingState.RELEARNING);
        assertThat(fixture.recognition.getDueAt()).isEqualTo(reviewTime.plusSeconds(600));
        assertThat(fixture.recognition.getLapseCount()).isEqualTo(1);
    }

    @Test
    void stalePreviewVersionIsRejectedBeforeWriting() {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.service.recordReview(fixture.card.getId(),
                VocabReviewDirection.RECOGNITION, VocabReviewLog.Rating.GOOD,
                VocabReviewLog.ReviewSource.MANUAL, NOW, 9, ZoneId.of("UTC")))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("modified concurrently");
    }

    @Test
    void ordinaryQueueExcludesFutureAndBuriedDirections() {
        Fixture fixture = fixture(true);
        fixture.recognition.apply(VocabScheduler.schedule(fixture.recognition,
                VocabReviewLog.Rating.GOOD, NOW), false);
        fixture.production.buryUntil(NOW.plusSeconds(3_600));

        assertThat(fixture.service.reviewQueue(VocabLanguage.ENGLISH, "IELTS", 20, NOW))
                .isEmpty();
    }

    private static Fixture fixture(boolean productionEnabled) {
        VocabCardRepository cards = mock(VocabCardRepository.class);
        VocabSchedulingCardRepository schedules = mock(VocabSchedulingCardRepository.class);
        VocabReviewLogRepository reviews = mock(VocabReviewLogRepository.class);
        VocabCard card = new VocabCard(UUID.randomUUID(), "meticulous", "tỉ mỉ", null,
                VocabLanguage.ENGLISH, "IELTS", null);
        card.setProductionEnabled(productionEnabled);
        VocabSchedulingCard recognition = new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                VocabReviewDirection.RECOGNITION, NOW);
        VocabSchedulingCard production = new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                VocabReviewDirection.PRODUCTION, NOW);
        when(cards.findById(card.getId())).thenReturn(Optional.of(card));
        when(cards.findBySuspendedFalse()).thenReturn(List.of(card));
        when(schedules.findByVocabCardIdAndDirection(card.getId(),
                VocabReviewDirection.RECOGNITION)).thenReturn(Optional.of(recognition));
        when(schedules.findByVocabCardIdAndDirection(card.getId(),
                VocabReviewDirection.PRODUCTION)).thenReturn(Optional.of(production));
        when(schedules.findByVocabCardIdIn(anyList())).thenReturn(productionEnabled
                ? List.of(recognition, production) : List.of(recognition));
        when(reviews.countByCard(anyList())).thenReturn(List.of());
        return new Fixture(new VocabService(cards, schedules, reviews,
                mock(VocabDeckService.class)), card, recognition, production, reviews);
    }

    private record Fixture(VocabService service, VocabCard card,
            VocabSchedulingCard recognition, VocabSchedulingCard production,
            VocabReviewLogRepository reviews) {
    }
}
