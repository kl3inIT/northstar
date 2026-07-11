package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for one graded essay (page table, assistant reads).
 * {@code criteria} and {@code topErrors} are raw JSON strings — the web
 * client parses them leniently (the vocab-metadata precedent) and the
 * assistant reads JSON natively, so no intermediate DTO tree is needed.
 */
public record WritingFeedbackSummary(
        @NotNull UUID id,
        @NotNull Instant submittedAt,
        @NotNull String taskLabel,
        @NotNull String rubric,
        @NotNull String essayMarkdown,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int wordCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double overallMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double overallMax,
        @NotNull String criteria,
        @NotNull String topErrors,
        @NotNull String summary,
        @NotNull String graderModel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {

    static WritingFeedbackSummary of(WritingFeedback feedback) {
        return new WritingFeedbackSummary(feedback.getId(), feedback.getSubmittedAt(),
                feedback.getTaskLabel(), feedback.getRubric(), feedback.getEssayMarkdown(),
                feedback.getWordCount(), feedback.getOverallMin(), feedback.getOverallMax(),
                feedback.getCriteria(), feedback.getTopErrors(), feedback.getSummary(),
                feedback.getGraderModel(), feedback.getVersion());
    }
}
