package com.northstar.api.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.study.SpeakingCoach;
import com.northstar.core.study.SpeakingService;
import com.northstar.core.study.SpeechAssessor;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabAudioAttemptSummary;
import com.northstar.core.study.VocabAudioPracticeMode;
import com.northstar.core.study.VocabAudioPracticeService;
import com.northstar.core.study.VocabCoach;
import com.northstar.core.study.VocabEnrichmentField;
import com.northstar.core.study.VocabEnrichmentPreview;
import com.northstar.core.study.VocabDeckService;
import com.northstar.core.study.VocabLanguage;
import com.northstar.core.study.VocabReviewLog;
import com.northstar.core.study.VocabReviewDirection;
import com.northstar.core.study.VocabService;
import com.northstar.core.study.VocabSchedulingState;
import com.northstar.core.study.WritingService;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

class StudyControllerTests {

    private final StudyService study = mock(StudyService.class);
    private final VocabService vocab = mock(VocabService.class);
    private final VocabCoach coach = mock(VocabCoach.class);
    private final VocabDeckService decks = mock(VocabDeckService.class);
    private final VocabEnrichmentJobService enrichmentJobs = mock(VocabEnrichmentJobService.class);
    private final VocabAudioPracticeService audioPractice = mock(VocabAudioPracticeService.class);
    private final WritingService writing = mock(WritingService.class);
    private final SpeakingService speaking = mock(SpeakingService.class);
    private final SpeechAssessor assessor = mock(SpeechAssessor.class);
    private ObjectProvider<SpeechAssessor> speechProvider;
    private StudyController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        speechProvider = (ObjectProvider<SpeechAssessor>) mock(ObjectProvider.class);
        when(speechProvider.getIfAvailable()).thenReturn(assessor);
        controller = new StudyController(study, vocab, coach, decks, enrichmentJobs, audioPractice,
                writing, speaking, speechProvider,
                (ObjectProvider<SpeakingCoach>) mock(ObjectProvider.class));
    }

    @Test
    void manualRatingIsTheOnlyReviewEndpointMemoryWrite() {
        UUID id = UUID.randomUUID();
        Instant previewedAt = Instant.parse("2026-07-12T00:00:00Z");
        VocabCardSummary expected = card(id);
        when(vocab.recordReview(id, VocabReviewDirection.RECOGNITION,
                VocabReviewLog.Rating.HARD, VocabReviewLog.ReviewSource.MANUAL,
                previewedAt, 3, java.time.ZoneId.of("Asia/Bangkok"))).thenReturn(expected);

        VocabCardSummary actual = controller.recordVocabReview(id,
                new StudyRequest.VocabReviewRequest(VocabReviewLog.Rating.HARD,
                        VocabReviewDirection.RECOGNITION, previewedAt, 3L), "Asia/Bangkok");

        assertThat(actual).isSameAs(expected);
        verify(vocab).recordReview(id, VocabReviewDirection.RECOGNITION,
                VocabReviewLog.Rating.HARD, VocabReviewLog.ReviewSource.MANUAL,
                previewedAt, 3, java.time.ZoneId.of("Asia/Bangkok"));
    }

    @Test
    void reviewQueueIsScopedByLanguageAndDeck() {
        controller.vocabReviewCards(VocabLanguage.ENGLISH, "IELTS", 10);

        verify(vocab).reviewQueue(VocabLanguage.ENGLISH, "IELTS", 10, null);
    }

    @Test
    void enrichmentReturnsPreviewWithoutUpdatingTheCard() {
        UUID id = UUID.randomUUID();
        VocabCardSummary card = card(id);
        Set<VocabEnrichmentField> fields = Set.of(VocabEnrichmentField.MNEMONIC);
        VocabEnrichmentPreview expected = new VocabEnrichmentPreview(null, List.of(), List.of(),
                List.of(), null, "Minute details.", null,
                "{\"mnemonic\":\"Minute details.\"}");
        when(vocab.find(id)).thenReturn(card);
        when(coach.enrich(card, fields)).thenReturn(expected);

        VocabEnrichmentPreview actual = controller.previewVocabEnrichment(id,
                new StudyRequest.VocabEnrichmentRequest(fields));

        assertThat(actual).isSameAs(expected);
        verify(vocab, never()).update(id, card.front(), card.back(), expected.metadata(),
                card.language(), card.deck(), card.disciplineId(), card.suspended());
    }

    @Test
    void successfulPronunciationAssessmentPersistsTheRecordingAndProviderFacts() {
        UUID cardId = UUID.randomUUID();
        byte[] wav = wav(3_200);
        var result = new com.northstar.core.study.PronunciationResult(
                91, 84, 80.0, "meticulous", List.of());
        VocabAudioAttemptSummary saved = new VocabAudioAttemptSummary(UUID.randomUUID(), cardId,
                VocabAudioPracticeMode.WORD, "meticulous", "meticulous", 91.0, 84.0, 80.0,
                "[]", "azure", "speech-sdk-1.50.0", null, null, 0.1, true,
                Instant.now().plusSeconds(86_400), false, Instant.now());
        when(audioPractice.referenceText(cardId, VocabAudioPracticeMode.WORD))
                .thenReturn("meticulous");
        when(assessor.assessReading(any(), any(), any())).thenReturn(result);
        when(assessor.providerId()).thenReturn("azure");
        when(assessor.providerRevision()).thenReturn("speech-sdk-1.50.0");
        when(audioPractice.recordSpeech(any(), any(), any(), any(), any(), any(), any(),
                anyDouble())).thenReturn(saved);

        VocabAudioAttemptView response = controller.assessVocabPronunciation(cardId,
                VocabAudioPracticeMode.WORD,
                new MockMultipartFile("audio", "attempt.wav", "audio/wav", wav));

        assertThat(response.id()).isEqualTo(saved.id());
        assertThat(response.recordingUrl()).contains(saved.id().toString());
        verify(audioPractice).recordSpeech(cardId, VocabAudioPracticeMode.WORD, "meticulous",
                result, "azure", "speech-sdk-1.50.0", wav, 0.1);
    }

    private static byte[] wav(int pcmBytes) {
        ByteBuffer result = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
        result.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcmBytes);
        result.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        result.putShort((short) 1).putShort((short) 1).putInt(16_000).putInt(32_000);
        result.putShort((short) 2).putShort((short) 16);
        result.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcmBytes);
        result.put(new byte[pcmBytes]);
        return result.array();
    }

    private static VocabCardSummary card(UUID id) {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new VocabCardSummary(id, "meticulous", "tỉ mỉ",
                "{\"reading\":\"/məˈtɪkjələs/\",\"partOfSpeech\":\"adjective\"}",
                VocabLanguage.ENGLISH, "IELTS", null, 0.4, 24.0, now, null, now,
                VocabSchedulingState.REVIEW, 0, false, 2, false, now, 1,
                false, null, null, null, null, null, null, null, null);
    }
}
