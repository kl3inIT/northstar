package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VocabLanguageDeckTests {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

    @Test
    void languageDetectionIsDeterministicForExistingCards() {
        assertThat(VocabLanguage.detect("meticulous")).isEqualTo(VocabLanguage.ENGLISH);
        assertThat(VocabLanguage.detect("磨蹭")).isEqualTo(VocabLanguage.CHINESE);
        assertThat(VocabLanguage.detect(null)).isEqualTo(VocabLanguage.ENGLISH);
    }

    @Test
    void queueFiltersLanguageThenDeckWithoutMixing() {
        VocabCardRepository cards = mock(VocabCardRepository.class);
        VocabSchedulingCardRepository schedules = mock(VocabSchedulingCardRepository.class);
        VocabReviewLogRepository reviews = mock(VocabReviewLogRepository.class);
        VocabCard ielts = card("meticulous", VocabLanguage.ENGLISH, "IELTS");
        VocabCard daily = card("errand", VocabLanguage.ENGLISH, null);
        VocabCard hsk = card("磨蹭", VocabLanguage.CHINESE, "HSK4");
        when(cards.findBySuspendedFalse()).thenReturn(List.of(ielts, daily, hsk));
        when(schedules.findByVocabCardIdIn(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return List.of(ielts, daily, hsk).stream()
                    .filter(card -> ids.contains(card.getId()))
                    .map(VocabLanguageDeckTests::recognition)
                    .toList();
        });
        when(reviews.countByCard(anyList())).thenReturn(List.of());
        VocabService service = new VocabService(cards, schedules, reviews,
                mock(VocabDeckService.class));

        assertThat(service.atRisk(VocabLanguage.ENGLISH, "IELTS", 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("meticulous");
        assertThat(service.atRisk(VocabLanguage.ENGLISH, "General", 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("errand");
        assertThat(service.atRisk(VocabLanguage.CHINESE, null, 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("磨蹭");
    }

    @Test
    void deckCanonicalizationReusesCasingAndGeneralIsStoredAsNull() {
        VocabCard existing = card("precise", VocabLanguage.ENGLISH, "IELTS");
        assertThat(VocabService.canonicalDeck(" ielts ", VocabLanguage.ENGLISH,
                List.of(existing))).isEqualTo("IELTS");
        assertThat(VocabService.canonicalDeck("General", VocabLanguage.ENGLISH,
                List.of(existing))).isNull();
    }

    @Test
    void classificationEditDoesNotResetIndependentSchedule() {
        VocabCard card = card("detail", VocabLanguage.ENGLISH, null);
        VocabSchedulingCard schedule = recognition(card);
        VocabScheduler.Outcome learned = VocabScheduler.schedule(schedule,
                VocabReviewLog.Rating.EASY, NOW);
        schedule.apply(learned, false);

        card.edit(card.getFront(), card.getBack(), card.getMetadata(),
                VocabLanguage.ENGLISH, "IELTS", card.getDisciplineId(), false);

        assertThat(schedule.getState()).isEqualTo(learned.state());
        assertThat(schedule.getStabilityDays()).isEqualTo(learned.stabilityDays());
        assertThat(schedule.getDueAt()).isEqualTo(learned.dueAt());
    }

    @Test
    void enabledDirectionsHaveIndependentSchedulesAndOnlyOneSiblingIsQueued() {
        VocabCardRepository cards = mock(VocabCardRepository.class);
        VocabSchedulingCardRepository schedules = mock(VocabSchedulingCardRepository.class);
        VocabReviewLogRepository reviews = mock(VocabReviewLogRepository.class);
        VocabCard card = card("serendipity", VocabLanguage.ENGLISH, "IELTS");
        card.setProductionEnabled(true);
        VocabSchedulingCard recognition = recognition(card);
        VocabSchedulingCard production = new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                VocabReviewDirection.PRODUCTION, NOW);
        when(cards.findBySuspendedFalse()).thenReturn(List.of(card));
        when(schedules.findByVocabCardIdIn(anyList())).thenReturn(List.of(recognition, production));
        when(reviews.countByCard(anyList())).thenReturn(List.of());
        VocabService service = new VocabService(cards, schedules, reviews,
                mock(VocabDeckService.class));

        assertThat(service.reviewQueue(VocabLanguage.ENGLISH, "IELTS", 20, NOW))
                .singleElement()
                .extracting(VocabReviewCardSummary::direction)
                .isEqualTo(VocabReviewDirection.RECOGNITION);
        assertThat(recognition.getId()).isNotEqualTo(production.getId());
    }

    private static VocabCard card(String front, VocabLanguage language, String deck) {
        return new VocabCard(UUID.randomUUID(), front, "meaning", null, language, deck, null);
    }

    private static VocabSchedulingCard recognition(VocabCard card) {
        return new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                VocabReviewDirection.RECOGNITION, NOW);
    }
}
