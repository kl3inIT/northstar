package com.northstar.core.study;

import java.time.Instant;
import java.util.UUID;

public record VocabAudioAttemptSummary(
        UUID id,
        UUID cardId,
        VocabAudioPracticeMode mode,
        String referenceText,
        String recognizedText,
        Double accuracy,
        Double fluency,
        Double prosody,
        String wordsJson,
        String providerId,
        String providerRevision,
        String dictationAnswer,
        String dictationDiff,
        Double durationSeconds,
        boolean recordingAvailable,
        Instant recordingExpiresAt,
        boolean recordingPinned,
        Instant createdAt) {
}
