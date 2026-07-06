package com.northstar.api.discipline;

import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.project.ProjectSummary;
import com.northstar.core.task.TaskSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The discipline slice: everything one discipline currently holds, in one
 * response — projects, open tasks, this week's events, and the notes tagged
 * with the discipline's name (the MFI tag bridge).
 */
record DisciplineOverview(
        @NotNull DisciplineSummary discipline,
        @NotNull List<ProjectSummary> projects,
        @NotNull List<TaskSummary> openTasks,
        @NotNull List<CalendarEventSummary> upcomingEvents,
        @NotNull List<NoteSummary> recentNotes,
        long noteCount) {
}
