package com.northstar.core.capture;

/**
 * LLM-parsed calendar-event suggestion. Same field vocabulary as the
 * assistant's create_event tool (date / startTime / endTime, "" when absent)
 * so the model sees ONE shape for "an event" everywhere. Times stay ISO
 * STRINGS for the same provider-structured-output reason as {@link TaskDraft}.
 * No startTime means an all-day event; no endTime means the client applies a
 * default duration.
 */
public record EventDraft(
        String title,
        String notes,
        String date,
        String startTime,
        String endTime,
        String disciplineName) {
}
