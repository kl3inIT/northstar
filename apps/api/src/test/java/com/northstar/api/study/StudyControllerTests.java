package com.northstar.api.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.study.SpeakingCoach;
import com.northstar.core.study.SpeakingService;
import com.northstar.core.study.SpeechAssessor;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabCoach;
import com.northstar.core.study.VocabEnrichmentField;
import com.northstar.core.study.VocabEnrichmentPreview;
import com.northstar.core.study.VocabLanguage;
import com.northstar.core.study.VocabReviewLog;
import com.northstar.core.study.VocabService;
import com.northstar.core.study.WritingService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StudyControllerTests {

    private final StudyService study = mock(StudyService.class);
    private final VocabService vocab = mock(VocabService.class);
    private final VocabCoach coach = mock(VocabCoach.class);
    private final WritingService writing = mock(WritingService.class);
    private final SpeakingService speaking = mock(SpeakingService.class);
    private StudyController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new StudyController(study, vocab, coach, writing, speaking,
                (ObjectProvider<SpeechAssessor>) mock(ObjectProvider.class),
                (ObjectProvider<SpeakingCoach>) mock(ObjectProvider.class));
    }

    @Test
    void manualRatingIsTheOnlyReviewEndpointMemoryWrite() {
        UUID id = UUID.randomUUID();
        VocabCardSummary expected = card(id);
        when(vocab.recordReview(id, 0.6, VocabReviewLog.Rating.HARD,
                VocabReviewLog.ReviewSource.MANUAL)).thenReturn(expected);

        VocabCardSummary actual = controller.recordVocabReview(id,
                new StudyRequest.VocabReviewRequest(VocabReviewLog.Rating.HARD));

        assertThat(actual).isSameAs(expected);
        verify(vocab).recordReview(id, 0.6, VocabReviewLog.Rating.HARD,
                VocabReviewLog.ReviewSource.MANUAL);
    }

    @Test
    void reviewQueueIsScopedByLanguageAndDeck() {
        controller.vocabReviewCards(VocabLanguage.ENGLISH, "IELTS", 10);

        verify(vocab).atRisk(VocabLanguage.ENGLISH, "IELTS", 10, null);
    }

    @Test
    void enrichmentReturnsPreviewWithoutUpdatingTheCard() {
        UUID id = UUID.randomUUID();
        VocabCardSummary card = card(id);
        Set<VocabEnrichmentField> fields = Set.of(VocabEnrichmentField.MNEMONIC);
        VocabEnrichmentPreview expected = new VocabEnrichmentPreview(null, List.of(), List.of(),
                List.of(), null, "Minute details.", "{\"mnemonic\":\"Minute details.\"}");
        when(vocab.find(id)).thenReturn(card);
        when(coach.enrich(card, fields)).thenReturn(expected);

        VocabEnrichmentPreview actual = controller.previewVocabEnrichment(id,
                new StudyRequest.VocabEnrichmentRequest(fields));

        assertThat(actual).isSameAs(expected);
        verify(vocab, never()).update(id, card.front(), card.back(), expected.metadata(),
                card.language(), card.deck(), card.disciplineId(), card.suspended());
    }

    private static VocabCardSummary card(UUID id) {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new VocabCardSummary(id, "meticulous", "tỉ mỉ",
                "{\"reading\":\"/məˈtɪkjələs/\",\"partOfSpeech\":\"adjective\"}",
                VocabLanguage.ENGLISH, "IELTS", null, 0.4, 24, now, 2, false, now, 1);
    }
}
