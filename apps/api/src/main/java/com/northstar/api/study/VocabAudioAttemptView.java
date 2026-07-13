package com.northstar.api.study;

import com.northstar.core.study.VocabAudioAttemptSummary;
import com.northstar.core.study.VocabAudioPracticeMode;
import java.time.Instant;
import java.util.UUID;

record VocabAudioAttemptView(
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
        String recordingUrl,
        Instant recordingExpiresAt,
        boolean recordingPinned,
        Instant createdAt) {

    static VocabAudioAttemptView from(VocabAudioAttemptSummary value) {
        return new VocabAudioAttemptView(value.id(), value.cardId(), value.mode(),
                value.referenceText(), value.recognizedText(), value.accuracy(), value.fluency(),
                value.prosody(), value.wordsJson(), value.providerId(), value.providerRevision(),
                value.dictationAnswer(), value.dictationDiff(), value.durationSeconds(),
                value.recordingAvailable(), value.recordingAvailable()
                        ? "/api/study/vocab/audio-attempts/" + value.id() + "/recording" : null,
                value.recordingExpiresAt(), value.recordingPinned(), value.createdAt());
    }
}
