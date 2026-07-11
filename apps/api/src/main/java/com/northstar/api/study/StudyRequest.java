package com.northstar.api.study;

import com.northstar.core.study.StudyKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request bodies for the study endpoints. One item shape serves both the batch
 * record (a confirmed capture — possibly several activities from one message)
 * and the full-edit update; values arrive already resolved (absolute dates,
 * split scores) because parsing natural language is capture's job, not the
 * API's. {@code kind} null means PRACTICE.
 */
final class StudyRequest {

    private StudyRequest() {
    }

    record StudyItemRequest(
            @NotNull LocalDate occurredOn,
            @NotBlank @Size(max = 64) String skill,
            StudyKind kind,
            @Positive @Max(1440) Integer durationMinutes,
            @PositiveOrZero Integer scoreRaw,
            @Positive Integer scoreMax,
            @Size(max = 2000) String notes,
            UUID disciplineId) {
    }

    /** POST body: every confirmed capture item in one transaction. */
    record RecordStudySessionsRequest(@NotEmpty @Valid List<StudyItemRequest> items) {
    }

    record VocabCardItemRequest(
            @NotBlank @Size(max = 255) String front,
            @NotBlank @Size(max = 1000) String back,
            @Size(max = 4000) String metadata,
            UUID disciplineId) {
    }

    /** POST body: every confirmed capture card in one transaction. */
    record RecordVocabCardsRequest(@NotEmpty @Valid List<VocabCardItemRequest> items) {
    }

    /** PUT body: full edit of a card's content; the memory model never changes here. */
    record UpdateVocabCardRequest(
            @NotBlank @Size(max = 255) String front,
            @NotBlank @Size(max = 1000) String back,
            @Size(max = 4000) String metadata,
            UUID disciplineId,
            @NotNull Boolean suspended) {
    }

    record SpeakingQuestionRequest(@Min(1) @Max(3) int part) {
    }
}
