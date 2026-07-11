package com.northstar.core.capture;

import java.util.List;

/**
 * LLM-parsed study-log entries. One captured message can carry several ("sáng
 * làm listening 25p đúng 18/25, chiều viết task 2 40p") — each activity becomes
 * one item. Field values arrive as strings for the same provider-structured-
 * output reason as {@link TaskDraft}: "" means "not stated" and the client
 * resolves defaults (occurredOn "" = today, kind "" = PRACTICE). {@code skill}
 * is one of the constrained vocabulary values the prompt carries; scores are
 * already-split integers ("18/25" -> raw 18, max 25) or "" when unscored.
 */
public record StudyDraft(List<StudyItem> items) {

    public record StudyItem(
            String skill,
            String kind,
            String durationMinutes,
            String scoreRaw,
            String scoreMax,
            String notes,
            String occurredOn,
            String disciplineName) {
    }
}
