package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
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
        VocabReviewLogRepository reviews = mock(VocabReviewLogRepository.class);
        VocabCard ielts = card("meticulous", VocabLanguage.ENGLISH, "IELTS", 0.8);
        VocabCard daily = card("errand", VocabLanguage.ENGLISH, null, 0.3);
        VocabCard hsk = card("磨蹭", VocabLanguage.CHINESE, "HSK4", 0.1);
        when(cards.findBySuspendedFalse()).thenReturn(List.of(ielts, daily, hsk));
        when(reviews.countByCard(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        VocabService service = new VocabService(cards, reviews);

        assertThat(service.atRisk(VocabLanguage.ENGLISH, "IELTS", 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("meticulous");
        assertThat(service.atRisk(VocabLanguage.ENGLISH, "General", 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("errand");
        assertThat(service.atRisk(VocabLanguage.CHINESE, null, 20, NOW))
                .extracting(VocabCardSummary::front).containsExactly("磨蹭");
    }

    @Test
    void deckCanonicalizationReusesCasingAndGeneralIsStoredAsNull() {
        VocabCard existing = card("precise", VocabLanguage.ENGLISH, "IELTS", 0.5);
        assertThat(VocabService.canonicalDeck(" ielts ", VocabLanguage.ENGLISH,
                List.of(existing))).isEqualTo("IELTS");
        assertThat(VocabService.canonicalDeck("General", VocabLanguage.ENGLISH,
                List.of(existing))).isNull();
    }

    @Test
    void classificationEditDoesNotResetMemory() {
        VocabCard card = card("detail", VocabLanguage.ENGLISH, null, 0.5);
        double alpha = card.getAlpha();
        double beta = card.getBeta();
        double halfLife = card.getHalflifeHours();
        Instant reviewedAt = card.getLastReviewedAt();

        card.edit(card.getFront(), card.getBack(), card.getMetadata(),
                VocabLanguage.ENGLISH, "IELTS", card.getDisciplineId(), false);

        assertThat(card.getAlpha()).isEqualTo(alpha);
        assertThat(card.getBeta()).isEqualTo(beta);
        assertThat(card.getHalflifeHours()).isEqualTo(halfLife);
        assertThat(card.getLastReviewedAt()).isEqualTo(reviewedAt);
    }

    private static VocabCard card(String front, VocabLanguage language, String deck,
            double alpha) {
        return new VocabCard(UUID.randomUUID(), front, "meaning", null, language, deck,
                null, alpha, 2, 24, NOW);
    }
}
