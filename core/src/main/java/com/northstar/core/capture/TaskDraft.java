package com.northstar.core.capture;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * LLM-parsed task suggestion. Relative dates in the source ("hôm nay", "mai",
 * "thứ 6") are resolved against the capture-time date the prompt carries.
 * {@code disciplineName} is one of the user's existing disciplines by exact
 * name (the client maps it to an id) — or null when none clearly fits.
 */
public record TaskDraft(
        String title,
        String notes,
        LocalDate dueDate,
        LocalTime dueTime,
        String disciplineName) {
}
