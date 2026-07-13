package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class VocabAudioPracticeServiceTests {

    private final VocabService vocab = mock(VocabService.class);
    private final VocabAudioAttemptRepository attempts = mock(VocabAudioAttemptRepository.class);
    private final VocabAudioPracticeService service = new VocabAudioPracticeService(
            vocab, attempts, new ObjectMapper());

    @Test
    void shadowingUsesTheSavedExampleWhileWordUsesTheFront() {
        UUID cardId = UUID.randomUUID();
        VocabCardSummary card = mock(VocabCardSummary.class);
        when(card.front()).thenReturn("serendipity");
        when(card.metadata()).thenReturn("{\"example\":\"We met by pure serendipity.\"}");
        when(vocab.find(cardId)).thenReturn(card);

        assertThat(service.referenceText(cardId, VocabAudioPracticeMode.WORD))
                .isEqualTo("serendipity");
        assertThat(service.referenceText(cardId, VocabAudioPracticeMode.SHADOWING))
                .isEqualTo("We met by pure serendipity.");
        assertThat(service.referenceText(cardId, VocabAudioPracticeMode.DICTATION))
                .isEqualTo("We met by pure serendipity.");
    }

    @Test
    void dictationIgnoresCaseAndPunctuationButReportsMissingWords() {
        VocabAudioPracticeService.DictationScore score = VocabAudioPracticeService.score(
                "We met, by pure serendipity!", "we met by serendipity");

        assertThat(score.accuracy()).isEqualTo(80.0);
        assertThat(score.tokens()).anySatisfy(token -> {
            assertThat(token.kind()).isEqualTo("MISSING");
            assertThat(token.expected()).isEqualTo("pure");
        });
    }

    @Test
    void successfulSpeechStoresProviderFactsAndTheWav() {
        UUID cardId = UUID.randomUUID();
        VocabCardSummary card = mock(VocabCardSummary.class);
        when(card.front()).thenReturn("meticulous");
        when(vocab.find(cardId)).thenReturn(card);
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attempts.findByVocabCardIdAndModeOrderByCreatedAtDesc(cardId,
                VocabAudioPracticeMode.WORD)).thenReturn(List.of());
        PronunciationResult result = new PronunciationResult(88, 79, 76.5,
                "meticulous", List.of());

        service.recordSpeech(cardId, VocabAudioPracticeMode.WORD, "meticulous", result,
                "azure", "speech-sdk-1.50.0", new byte[] {1, 2, 3}, 1.25);

        ArgumentCaptor<VocabAudioAttempt> saved = ArgumentCaptor.forClass(VocabAudioAttempt.class);
        verify(attempts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().recording()).containsExactly(1, 2, 3);
        assertThat(saved.getValue().accuracy()).isEqualTo(88);
        assertThat(saved.getValue().providerId()).isEqualTo("azure");
        assertThat(saved.getValue().recordingExpiresAt()).isAfter(Instant.now().plusSeconds(170L * 86_400));
    }

    @Test
    void retentionKeepsLatestBestAndFirstButClearsExpiredMiddleRecording() {
        UUID cardId = UUID.randomUUID();
        VocabCardSummary card = mock(VocabCardSummary.class);
        when(vocab.find(cardId)).thenReturn(card);
        Instant expired = Instant.now().minusSeconds(1);
        VocabAudioAttempt latest = speech(cardId, 70, expired);
        VocabAudioAttempt best = speech(cardId, 99, expired);
        VocabAudioAttempt middle = speech(cardId, 75, expired);
        VocabAudioAttempt first = speech(cardId, 60, expired);
        when(attempts.findByVocabCardIdAndModeOrderByCreatedAtDesc(cardId,
                VocabAudioPracticeMode.WORD)).thenReturn(List.of(latest, best, middle, first));
        when(attempts.findByVocabCardIdAndModeOrderByCreatedAtDesc(cardId,
                VocabAudioPracticeMode.SHADOWING)).thenReturn(List.of());
        when(attempts.findByVocabCardIdOrderByCreatedAtDesc(cardId))
                .thenReturn(List.of(latest, best, middle, first));

        service.list(cardId);

        assertThat(latest.recording()).isNotNull();
        assertThat(best.recording()).isNotNull();
        assertThat(first.recording()).isNotNull();
        assertThat(middle.recording()).isNull();
    }

    private static VocabAudioAttempt speech(UUID cardId, double accuracy, Instant expiresAt) {
        return VocabAudioAttempt.newSpeech(UUID.randomUUID(), cardId, VocabAudioPracticeMode.WORD,
                "word", new PronunciationResult(accuracy, 80, 70.0, "word", List.of()), "[]",
                "azure", "1.50.0", new byte[] {1}, 1, expiresAt);
    }
}
