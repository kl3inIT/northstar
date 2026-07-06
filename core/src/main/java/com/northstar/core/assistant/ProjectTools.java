package com.northstar.core.assistant;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.project.ProjectService;
import com.northstar.core.project.ProjectSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Project tools — read-only adapters over the project module. The views
 * resolve discipline ids to names and derive progress, so the model never
 * has to join ids itself.
 */
@Component
class ProjectTools implements NorthstarTool {

    private static final String LIST_PROJECTS = """
            The user's projects (staged work under a discipline, e.g. a scholarship \
            application or an exam campaign) with status, dates and progress. Use for \
            'what am I working on?' overviews.""";

    private static final String PROJECT_STATUS = """
            Detailed status of projects whose name contains the query (case-insensitive): \
            milestones with done-state plus the tasks attached to the project. Use for \
            'how is X going?' questions.""";

    private final ProjectService projects;
    private final DisciplineService disciplines;
    private final TaskService tasks;

    ProjectTools(ProjectService projects, DisciplineService disciplines, TaskService tasks) {
        this.projects = projects;
        this.disciplines = disciplines;
        this.tasks = tasks;
    }

    @Tool(name = "list_projects", description = LIST_PROJECTS)
    @McpTool(name = "list_projects", description = LIST_PROJECTS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<ProjectView> listProjects() {
        Map<UUID, String> names = disciplineNames();
        return projects.list().stream().map(p -> view(p, names)).toList();
    }

    @Tool(name = "project_status", description = PROJECT_STATUS)
    @McpTool(name = "project_status", description = PROJECT_STATUS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<ProjectStatusView> projectStatus(
            @ToolParam(description = "Part of the project name, e.g. 'Chevening'")
            @McpToolParam(description = "Part of the project name, e.g. 'Chevening'",
                    required = true) String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        Map<UUID, String> names = disciplineNames();
        return projects.list().stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(needle))
                .map(p -> new ProjectStatusView(view(p, names),
                        tasks.byProject(p.id()).stream().map(ProjectTools::taskLine).toList()))
                .toList();
    }

    private Map<UUID, String> disciplineNames() {
        Map<UUID, String> names = new HashMap<>();
        for (DisciplineSummary d : disciplines.list()) {
            names.put(d.id(), d.name());
        }
        return names;
    }

    private static ProjectView view(ProjectSummary p, Map<UUID, String> disciplineNames) {
        return new ProjectView(p.id(), p.name(), p.status().name(),
                p.disciplineId() == null ? null : disciplineNames.get(p.disciplineId()),
                p.startDate(), p.targetDate(), p.progressPercent(),
                p.milestones().stream()
                        .map(m -> new MilestoneView(m.name(), m.dueDate(), m.doneAt() != null))
                        .toList());
    }

    private static String taskLine(TaskSummary t) {
        String due = t.dueDate() == null ? "" : " (due " + t.dueDate() + ")";
        return (t.status().name().equals("DONE") ? "[done] " : "[open] ") + t.title() + due;
    }

    record MilestoneView(String name, LocalDate dueDate, boolean done) {
    }

    record ProjectView(UUID id, String name, String status, String disciplineName,
            LocalDate startDate, LocalDate targetDate, int progressPercent,
            List<MilestoneView> milestones) {
    }

    record ProjectStatusView(ProjectView project, List<String> tasks) {
    }
}
