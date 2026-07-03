package com.northstar.core.capture;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * LLM-parsed task suggestion. Relative dates in the source ("hôm nay", "mai",
 * "thứ 6") are resolved against the capture-time date the prompt carries.
 */
public record TaskDraft(
        String title,
        String notes,
        LocalDate dueDate,
        LocalTime dueTime) {
}
