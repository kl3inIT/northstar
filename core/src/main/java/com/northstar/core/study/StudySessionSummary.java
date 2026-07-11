package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model for study-log rows (page table, assistant reads). The
 * {@code @NotNull}/{@code @Schema} marks make required fields required in the
 * generated OpenAPI client; duration, score, notes, and discipline stay
 * genuinely optional.
 */
public record StudySessionSummary(
        @NotNull UUID id,
        @NotNull LocalDate occurredOn,
        @NotNull String skill,
        @NotNull StudyKind kind,
        Integer durationMinutes,
        Integer scoreRaw,
        Integer scoreMax,
        String notes,
        UUID disciplineId,
        @NotNull StudySource source,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {

    static StudySessionSummary of(StudySession session) {
        return new StudySessionSummary(session.getId(), session.getOccurredOn(),
                session.getSkill(), session.getKind(), session.getDurationMinutes(),
                session.getScoreRaw(), session.getScoreMax(), session.getNotes(),
                session.getDisciplineId(), session.getSource(), session.getCreatedAt(),
                session.getVersion());
    }
}
