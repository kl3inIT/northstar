package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * One recurring error pattern aggregated across the writing-feedback history -
 * the learner's personal grammar syllabus. {@code occurrences} counts gradings
 * that flagged the pattern, and {@code examples} carries recent verbatim
 * quote-to-fix pairs from the user's own essays, so drills can target the real
 * mistake, not a textbook category.
 */
public record GrammarWeakness(
        @NotNull String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int occurrences,
        @NotNull LocalDate lastSeen,
        @NotNull List<GrammarExample> examples) {

    public record GrammarExample(@NotNull String quote, @NotNull String fix) {
    }
}
