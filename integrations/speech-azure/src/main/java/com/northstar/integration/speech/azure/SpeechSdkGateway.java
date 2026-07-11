package com.northstar.integration.speech.azure;

import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import com.northstar.core.study.SpeechAssessmentException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class SpeechSdkGateway implements AzureSpeechGateway {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ASSESSMENT_TIMEOUT = Duration.ofSeconds(90);

    private final String key;
    private final String region;

    SpeechSdkGateway(String key, String region) {
        if (key == null || key.isBlank() || region == null || region.isBlank()) {
            throw new IllegalArgumentException("Azure Speech key and region are required");
        }
        this.key = key;
        this.region = region;
    }

    @Override
    public List<String> assess(byte[] pcm, String referenceText, String locale, boolean continuous) {
        try (SpeechConfig speech = SpeechConfig.fromSubscription(key, region);
                PushAudioInputStream stream = PushAudioInputStream.create();
                AudioConfig audio = AudioConfig.fromStreamInput(stream);
                PronunciationAssessmentConfig assessment = configuration(referenceText, locale)) {
            speech.setSpeechRecognitionLanguage(locale);
            speech.setOutputFormat(OutputFormat.Detailed);
            if (continuous) {
                speech.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, "1500");
            }
            try (SpeechRecognizer recognizer = new SpeechRecognizer(speech, locale, audio)) {
                assessment.applyTo(recognizer);
                stream.write(pcm);
                stream.close();
                return continuous ? continuous(recognizer) : once(recognizer);
            }
        } catch (SpeechAssessmentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw mapped(exception);
        }
    }

    static PronunciationAssessmentConfig configuration(String referenceText, String locale) {
        PronunciationAssessmentConfig result = new PronunciationAssessmentConfig(
                referenceText,
                PronunciationAssessmentGradingSystem.HundredMark,
                PronunciationAssessmentGranularity.Phoneme,
                false);
        if ("en-US".equalsIgnoreCase(locale)) result.enableProsodyAssessment();
        return result;
    }

    private static List<String> once(SpeechRecognizer recognizer) throws Exception {
        try (var result = recognizer.recognizeOnceAsync().get(ASSESSMENT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            if (result.getReason() == ResultReason.NoMatch) {
                throw provider("Azure Speech could not recognize the recording");
            }
            if (result.getReason() == ResultReason.Canceled) {
                throw provider("Azure Speech canceled the assessment");
            }
            String json = result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
            if (json == null || json.isBlank()) throw provider("Azure Speech returned no detailed result");
            return List.of(json);
        }
    }

    private static List<String> continuous(SpeechRecognizer recognizer) throws Exception {
        List<String> responses = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> finished = new CompletableFuture<>();
        recognizer.recognized.addEventListener((_, event) -> {
            try (var result = event.getResult()) {
                if (result.getReason() != ResultReason.RecognizedSpeech) return;
                String json = result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                if (json != null && !json.isBlank()) responses.add(json);
            }
        });
        recognizer.canceled.addEventListener((_, event) -> {
            if (event.getReason() == CancellationReason.Error) {
                finished.completeExceptionally(mapped(event.getErrorDetails()));
            } else {
                finished.complete(null);
            }
        });
        recognizer.sessionStopped.addEventListener((_, _) -> finished.complete(null));

        recognizer.startContinuousRecognitionAsync().get(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        try {
            finished.get(ASSESSMENT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            recognizer.stopContinuousRecognitionAsync().get(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        if (responses.isEmpty()) throw provider("Azure Speech could not recognize the recording");
        return List.copyOf(responses);
    }

    private static SpeechAssessmentException mapped(Throwable failure) {
        Throwable cause = failure instanceof ExecutionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof SpeechAssessmentException speech) return speech;
        if (cause instanceof TimeoutException) {
            return new SpeechAssessmentException(SpeechAssessmentException.Failure.TIMEOUT,
                    "Azure Speech assessment timed out", cause);
        }
        return mapped(cause.getMessage(), cause);
    }

    private static SpeechAssessmentException mapped(String details) {
        return mapped(details, null);
    }

    private static SpeechAssessmentException mapped(String details, Throwable cause) {
        String normalized = details == null ? "" : details.toLowerCase(Locale.ROOT);
        SpeechAssessmentException.Failure failure;
        String message;
        if (normalized.contains("401") || normalized.contains("403")
                || normalized.contains("unauthorized") || normalized.contains("authentication")) {
            failure = SpeechAssessmentException.Failure.AUTHENTICATION;
            message = "Azure Speech key and region do not match";
        } else if (normalized.contains("429") || normalized.contains("quota")
                || normalized.contains("rate limit")) {
            failure = SpeechAssessmentException.Failure.QUOTA;
            message = "Azure Speech quota is exhausted";
        } else {
            failure = SpeechAssessmentException.Failure.PROVIDER;
            message = "Azure Speech assessment failed";
        }
        return cause == null ? new SpeechAssessmentException(failure, message)
                : new SpeechAssessmentException(failure, message, cause);
    }

    private static SpeechAssessmentException provider(String message) {
        return new SpeechAssessmentException(SpeechAssessmentException.Failure.PROVIDER, message);
    }
}
