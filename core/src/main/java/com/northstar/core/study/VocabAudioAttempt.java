package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vocab_audio_attempt")
class VocabAudioAttempt extends BaseEntity {

    @Column(name = "vocab_card_id", nullable = false)
    private UUID vocabCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabAudioPracticeMode mode;

    @Column(name = "reference_text", nullable = false, length = 2000)
    private String referenceText;

    @Column(name = "recognized_text", length = 2000)
    private String recognizedText;

    private Double accuracy;
    private Double fluency;
    private Double prosody;

    @Column(name = "words_json", columnDefinition = "text")
    private String wordsJson;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "provider_revision", length = 128)
    private String providerRevision;

    @Column(name = "dictation_answer", length = 2000)
    private String dictationAnswer;

    @Column(name = "dictation_diff", columnDefinition = "text")
    private String dictationDiff;

    @Column(columnDefinition = "bytea")
    private byte[] recording;

    @Column(name = "recording_mime_type", length = 64)
    private String recordingMimeType;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "recording_expires_at")
    private Instant recordingExpiresAt;

    @Column(name = "recording_pinned", nullable = false)
    private boolean recordingPinned;

    protected VocabAudioAttempt() {
        // for JPA
    }

    private VocabAudioAttempt(UUID id, UUID cardId, VocabAudioPracticeMode mode, String referenceText) {
        super(id);
        this.vocabCardId = cardId;
        this.mode = mode;
        this.referenceText = referenceText;
    }

    static VocabAudioAttempt newSpeech(UUID id, UUID cardId, VocabAudioPracticeMode mode,
            String referenceText, PronunciationResult result, String wordsJson,
            String providerId, String providerRevision, byte[] recording,
            double durationSeconds, Instant expiresAt) {
        if (mode == VocabAudioPracticeMode.DICTATION) {
            throw new IllegalArgumentException("Dictation is not a speech assessment mode");
        }
        VocabAudioAttempt attempt = new VocabAudioAttempt(id, cardId, mode, referenceText);
        attempt.recognizedText = result.recognizedText();
        attempt.accuracy = result.accuracy();
        attempt.fluency = result.fluency();
        attempt.prosody = result.prosody();
        attempt.wordsJson = wordsJson;
        attempt.providerId = providerId;
        attempt.providerRevision = providerRevision;
        attempt.recording = recording.clone();
        attempt.recordingMimeType = "audio/wav";
        attempt.durationSeconds = durationSeconds;
        attempt.recordingExpiresAt = expiresAt;
        return attempt;
    }

    static VocabAudioAttempt newDictation(UUID id, UUID cardId, String referenceText,
            String answer, double accuracy, String diffJson) {
        VocabAudioAttempt attempt = new VocabAudioAttempt(id, cardId,
                VocabAudioPracticeMode.DICTATION, referenceText);
        attempt.accuracy = accuracy;
        attempt.dictationAnswer = answer;
        attempt.dictationDiff = diffJson;
        return attempt;
    }

    UUID vocabCardId() { return vocabCardId; }
    VocabAudioPracticeMode mode() { return mode; }
    String referenceText() { return referenceText; }
    String recognizedText() { return recognizedText; }
    Double accuracy() { return accuracy; }
    Double fluency() { return fluency; }
    Double prosody() { return prosody; }
    String wordsJson() { return wordsJson; }
    String providerId() { return providerId; }
    String providerRevision() { return providerRevision; }
    String dictationAnswer() { return dictationAnswer; }
    String dictationDiff() { return dictationDiff; }
    byte[] recording() { return recording == null ? null : recording.clone(); }
    String recordingMimeType() { return recordingMimeType; }
    Double durationSeconds() { return durationSeconds; }
    Instant recordingExpiresAt() { return recordingExpiresAt; }
    boolean recordingPinned() { return recordingPinned; }

    void setRecordingPinned(boolean pinned) {
        this.recordingPinned = pinned;
    }

    void clearRecording() {
        recording = null;
        recordingMimeType = null;
        recordingExpiresAt = null;
        recordingPinned = false;
    }
}
