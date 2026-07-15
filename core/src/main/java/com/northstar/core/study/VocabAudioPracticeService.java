package com.northstar.core.study;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class VocabAudioPracticeService {

    static final Duration RECORDING_RETENTION = Duration.ofDays(180);
    static final int MAX_RECORDINGS_PER_MODE = 20;
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}']+");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private final VocabService vocab;
    private final VocabAudioAttemptRepository attempts;
    private final ObjectMapper json;

    public VocabAudioPracticeService(VocabService vocab, VocabAudioAttemptRepository attempts,
            ObjectMapper json) {
        this.vocab = vocab;
        this.attempts = attempts;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public String referenceText(UUID cardId, VocabAudioPracticeMode mode) {
        VocabCardSummary card = vocab.find(cardId);
        return switch (Objects.requireNonNull(mode, "mode is required")) {
            case WORD -> card.front();
            case SHADOWING -> example(card)
                    .filter(VocabPracticeText::supportsShadowing)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Shadowing requires a connected example with at least four words"));
            case DICTATION -> example(card).orElse(card.front());
        };
    }

    @Transactional
    public VocabAudioAttemptSummary recordSpeech(UUID cardId, VocabAudioPracticeMode mode,
            String referenceText, PronunciationResult result, String providerId,
            String providerRevision, byte[] wav, double durationSeconds) {
        Objects.requireNonNull(result, "result is required");
        VocabAudioPracticeMode requiredMode = Objects.requireNonNull(mode, "mode is required");
        if (requiredMode == VocabAudioPracticeMode.DICTATION) {
            throw new IllegalArgumentException("Dictation does not accept a microphone recording");
        }
        String currentReference = referenceText(cardId, requiredMode);
        if (!currentReference.equals(referenceText)) {
            throw new IllegalArgumentException("Card practice text changed before the attempt was saved");
        }
        if (wav == null || wav.length == 0) throw new IllegalArgumentException("recording is required");
        if (durationSeconds <= 0 || durationSeconds > 30) {
            throw new IllegalArgumentException("durationSeconds must be between 0 and 30");
        }
        Instant now = Instant.now();
        VocabAudioAttempt saved = attempts.saveAndFlush(VocabAudioAttempt.newSpeech(
                UUID.randomUUID(), cardId, requiredMode, currentReference, result,
                json.writeValueAsString(result.words()), required(providerId, "providerId", 64),
                required(providerRevision, "providerRevision", 128), wav, durationSeconds,
                now.plus(RECORDING_RETENTION)));
        prune(cardId, requiredMode, now);
        return summary(saved);
    }

    @Transactional
    public VocabAudioAttemptSummary recordDictation(UUID cardId, String answer) {
        String cleanAnswer = required(answer, "answer", 2000);
        String reference = referenceText(cardId, VocabAudioPracticeMode.DICTATION);
        DictationScore score = score(reference, cleanAnswer);
        VocabAudioAttempt saved = attempts.saveAndFlush(VocabAudioAttempt.newDictation(
                UUID.randomUUID(), cardId, reference, cleanAnswer, score.accuracy(),
                json.writeValueAsString(score.tokens())));
        return summary(saved);
    }

    @Transactional
    public List<VocabAudioAttemptSummary> list(UUID cardId) {
        vocab.find(cardId);
        Instant now = Instant.now();
        EnumSet.of(VocabAudioPracticeMode.WORD, VocabAudioPracticeMode.SHADOWING)
                .forEach(mode -> prune(cardId, mode, now));
        return attempts.findByVocabCardIdOrderByCreatedAtDesc(cardId).stream()
                .map(VocabAudioPracticeService::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public VocabAudioAttemptSummary find(UUID attemptId) {
        return summary(get(attemptId));
    }

    @Transactional(readOnly = true)
    public Optional<VocabAudioRecording> recording(UUID attemptId) {
        VocabAudioAttempt attempt = get(attemptId);
        byte[] data = attempt.recording();
        return data == null ? Optional.empty()
                : Optional.of(new VocabAudioRecording(attempt.recordingMimeType(), data));
    }

    @Transactional
    public VocabAudioAttemptSummary pin(UUID attemptId, boolean pinned) {
        VocabAudioAttempt attempt = get(attemptId);
        if (pinned && attempt.recording() == null) {
            throw new IllegalArgumentException("This attempt no longer has a recording to keep");
        }
        attempt.setRecordingPinned(pinned);
        return summary(attempt);
    }

    @Transactional
    public void delete(UUID attemptId) {
        attempts.delete(get(attemptId));
    }

    private void prune(UUID cardId, VocabAudioPracticeMode mode, Instant now) {
        List<VocabAudioAttempt> recordings = attempts
                .findByVocabCardIdAndModeOrderByCreatedAtDesc(cardId, mode).stream()
                .filter(attempt -> attempt.recording() != null)
                .toList();
        if (recordings.isEmpty()) return;

        Set<UUID> preserved = new LinkedHashSet<>();
        recordings.stream().filter(VocabAudioAttempt::recordingPinned)
                .map(VocabAudioAttempt::getId).forEach(preserved::add);
        preserved.add(recordings.getFirst().getId());
        preserved.add(recordings.getLast().getId());
        recordings.stream().filter(attempt -> attempt.accuracy() != null)
                .max(Comparator.comparingDouble(VocabAudioAttempt::accuracy))
                .map(VocabAudioAttempt::getId).ifPresent(preserved::add);

        for (int index = 0; index < recordings.size(); index++) {
            VocabAudioAttempt attempt = recordings.get(index);
            boolean expired = attempt.recordingExpiresAt() != null
                    && attempt.recordingExpiresAt().isBefore(now);
            boolean beyondLimit = index >= MAX_RECORDINGS_PER_MODE;
            if (!preserved.contains(attempt.getId()) && (expired || beyondLimit)) {
                attempt.clearRecording();
            }
        }
    }

    private Optional<String> example(VocabCardSummary card) {
        if (card.metadata() == null || card.metadata().isBlank()) return Optional.empty();
        Object parsed;
        try {
            parsed = json.readValue(card.metadata(), Object.class);
        } catch (JacksonException malformed) {
            // Card metadata has no JSON contract on the write path; a non-JSON
            // blob must not crash practice — just treat it as no example.
            return Optional.empty();
        }
        if (parsed instanceof Map<?, ?> map && map.get("example") instanceof String value
                && !value.isBlank()) {
            return VocabPracticeText.targetExample(value);
        }
        return Optional.empty();
    }

    private VocabAudioAttempt get(UUID id) {
        return attempts.findById(Objects.requireNonNull(id, "attempt id is required"))
                .orElseThrow(() -> new IllegalArgumentException("No vocabulary audio attempt " + id));
    }

    static DictationScore score(String reference, String answer) {
        List<String> expected = tokens(reference);
        List<String> actual = tokens(answer);
        int[][] distance = new int[expected.size() + 1][actual.size() + 1];
        for (int i = 0; i <= expected.size(); i++) distance[i][0] = i;
        for (int j = 0; j <= actual.size(); j++) distance[0][j] = j;
        for (int i = 1; i <= expected.size(); i++) {
            for (int j = 1; j <= actual.size(); j++) {
                int substitution = distance[i - 1][j - 1]
                        + (expected.get(i - 1).equals(actual.get(j - 1)) ? 0 : 1);
                distance[i][j] = Math.min(substitution,
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1));
            }
        }

        List<DictationToken> reversed = new ArrayList<>();
        int i = expected.size();
        int j = actual.size();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && expected.get(i - 1).equals(actual.get(j - 1))
                    && distance[i][j] == distance[i - 1][j - 1]) {
                reversed.add(new DictationToken("MATCH", expected.get(i - 1), actual.get(j - 1)));
                i--;
                j--;
            } else if (i > 0 && j > 0 && distance[i][j] == distance[i - 1][j - 1] + 1) {
                reversed.add(new DictationToken("SUBSTITUTION", expected.get(i - 1), actual.get(j - 1)));
                i--;
                j--;
            } else if (i > 0 && distance[i][j] == distance[i - 1][j] + 1) {
                reversed.add(new DictationToken("MISSING", expected.get(i - 1), null));
                i--;
            } else {
                reversed.add(new DictationToken("EXTRA", null, actual.get(j - 1)));
                j--;
            }
        }
        java.util.Collections.reverse(reversed);
        int denominator = Math.max(1, Math.max(expected.size(), actual.size()));
        double accuracy = Math.max(0, 100.0 * (1.0 - distance[expected.size()][actual.size()]
                / (double) denominator));
        return new DictationScore(Math.round(accuracy * 10.0) / 10.0, List.copyOf(reversed));
    }

    private static List<String> tokens(String value) {
        String normalized = Normalizer.normalize(required(value, "text", 2000),
                Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        normalized = SPACE.matcher(NON_WORD.matcher(normalized).replaceAll(" ").strip())
                .replaceAll(" ");
        return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String clean = value.strip();
        if (clean.length() > maximum) {
            throw new IllegalArgumentException(field + " must be at most " + maximum + " characters");
        }
        return clean;
    }

    private static VocabAudioAttemptSummary summary(VocabAudioAttempt attempt) {
        return new VocabAudioAttemptSummary(attempt.getId(), attempt.vocabCardId(), attempt.mode(),
                attempt.referenceText(), attempt.recognizedText(), attempt.accuracy(), attempt.fluency(),
                attempt.prosody(), attempt.wordsJson(), attempt.providerId(), attempt.providerRevision(),
                attempt.dictationAnswer(), attempt.dictationDiff(), attempt.durationSeconds(),
                attempt.recording() != null, attempt.recordingExpiresAt(), attempt.recordingPinned(),
                attempt.getCreatedAt());
    }

    record DictationScore(double accuracy, List<DictationToken> tokens) {
    }

    record DictationToken(String kind, String expected, String actual) {
    }
}
