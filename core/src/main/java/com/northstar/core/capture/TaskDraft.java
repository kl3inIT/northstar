package com.northstar.core.capture;

/**
 * LLM-parsed task suggestion. Relative dates in the source ("hôm nay", "mai",
 * "thứ 6") are resolved against the capture-time date the prompt carries.
 * {@code dueDate}/{@code dueTime} stay ISO STRINGS ("2026-07-03" / "14:00"),
 * not java.time types: with provider structured output the schema for a
 * LocalTime advertises JSON-Schema "time" (offset included, e.g. "14:00:00Z"),
 * which Jackson's LocalTime then rejects — the consumer parses at the point of
 * use instead. {@code disciplineName} is one of the user's existing disciplines
 * by exact name (the client maps it to an id) — or null when none clearly fits.
 */
public record TaskDraft(
        String title,
        String notes,
        String dueDate,
        String dueTime,
        String disciplineName) {
}
