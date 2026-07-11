package com.northstar.core.study;

import jakarta.validation.constraints.NotNull;

public record SpeakingAttemptResult(
        @NotNull SpeakingFeedbackSummary feedback,
        @NotNull SpokenAnswerResult delivery) {
}
