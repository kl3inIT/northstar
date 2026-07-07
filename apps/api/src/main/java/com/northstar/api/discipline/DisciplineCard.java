package com.northstar.api.discipline;

import com.northstar.core.discipline.DisciplineSummary;
import jakarta.validation.constraints.NotNull;

/** One discipline list card: identity plus the counts the /disciplines grid shows. */
record DisciplineCard(
        @NotNull DisciplineSummary discipline,
        long openTasks,
        long upcomingEvents,
        long notes,
        long projects,
        long linkedTasks,
        long linkedEvents) {
}
