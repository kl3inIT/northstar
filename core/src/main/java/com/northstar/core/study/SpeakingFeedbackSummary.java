package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record SpeakingFeedbackSummary(
        @NotNull UUID id,
        @NotNull Instant submittedAt,
        @NotNull String question,
        @NotNull String transcript,
        Double pronunciation,
        Double fluency,
        Double prosody,
        @NotNull String contentScores,
        @NotNull String topErrors,
        @NotNull String summary,
        @NotNull String graderModel,
        @NotNull String deliveryProvider,
        @NotNull String providerRevision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {

    static SpeakingFeedbackSummary of(SpeakingFeedback feedback) {
        return new SpeakingFeedbackSummary(feedback.getId(), feedback.getSubmittedAt(),
                feedback.getQuestion(), feedback.getTranscript(), feedback.getPronunciation(),
                feedback.getFluency(), feedback.getProsody(), feedback.getContentScores(),
                feedback.getTopErrors(), feedback.getSummary(), feedback.getGraderModel(),
                feedback.getDeliveryProvider(), feedback.getProviderRevision(), feedback.getVersion());
    }
}
