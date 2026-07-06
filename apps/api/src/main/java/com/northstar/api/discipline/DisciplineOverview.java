package com.northstar.api.discipline;

import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.task.TaskSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The discipline slice: everything one discipline currently holds, in one
 * response — open tasks, this week's events, and the notes tagged with the
 * discipline's name (the MFI tag bridge). Projects join this view when the
 * project module lands.
 */
record DisciplineOverview(
        @NotNull DisciplineSummary discipline,
        @NotNull List<TaskSummary> openTasks,
        @NotNull List<CalendarEventSummary> upcomingEvents,
        @NotNull List<NoteSummary> recentNotes,
        long noteCount) {
}
