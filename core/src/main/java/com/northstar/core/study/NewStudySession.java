package com.northstar.core.study;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input for one study-log entry, already resolved (absolute date, integer
 * minutes/scores) — natural-language parsing lives in capture and the
 * assistant, and callers resolve "hôm qua"/today to an absolute
 * {@code occurredOn} in the user's zone. {@code kind} null means PRACTICE.
 */
public record NewStudySession(
        LocalDate occurredOn,
        String skill,
        StudyKind kind,
        Integer durationMinutes,
        Integer scoreRaw,
        Integer scoreMax,
        String notes,
        UUID disciplineId) {
}
