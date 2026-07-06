package com.northstar.core.assistant;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.project.MilestoneSummary;
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
import java.util.stream.Collectors;
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

    private static final String CREATE_PROJECT = """
            Start a new project (staged work under a discipline, e.g. a scholarship \
            application or an exam campaign). Add its stages afterwards with add_milestone.""";

    private static final String UPDATE_PROJECT = """
            Edit a project by its id (ids come from list_projects): rename, change notes \
            or dates, move to another discipline, or set status ACTIVE/DONE. Only pass \
            the fields to change — pass '' or omit to keep, 'none' to clear a date or \
            the discipline.""";

    private static final String DELETE_PROJECT = """
            Permanently delete a project and its milestones (attached tasks survive, \
            detached). Only for a project the user explicitly asked to remove; for a \
            finished project set status DONE via update_project instead.""";

    private static final String MANAGE_MILESTONE = """
            Manage a project's stages: ADD creates a milestone, TOGGLE flips one between \
            done and open ('đánh dấu Essays xong'), REMOVE deletes one. Toggle/remove \
            match the milestone by name within the project. Returns the refreshed \
            project with recomputed progress.""";

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

    @Tool(name = "create_project", description = CREATE_PROJECT)
    @McpTool(name = "create_project", description = CREATE_PROJECT,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    ProjectView createProject(
            @ToolParam(description = "Project name, e.g. 'Chevening 2027'")
            @McpToolParam(description = "Project name, e.g. 'Chevening 2027'", required = true) String name,
            @ToolParam(description = "Discipline name it belongs to (see list_disciplines); omit for none", required = false)
            @McpToolParam(description = "Discipline name it belongs to (see list_disciplines); omit for none",
                    required = false) String disciplineName,
            @ToolParam(description = "Start date yyyy-MM-dd; omit for today-ish/unset", required = false)
            @McpToolParam(description = "Start date yyyy-MM-dd; omit for today-ish/unset",
                    required = false) String startDate,
            @ToolParam(description = "Target/finish date yyyy-MM-dd, on or after startDate; omit if unknown", required = false)
            @McpToolParam(description = "Target/finish date yyyy-MM-dd, on or after startDate; omit if unknown",
                    required = false) String targetDate,
            @ToolParam(description = "Extra context beyond the name", required = false)
            @McpToolParam(description = "Extra context beyond the name",
                    required = false) String projectNotes) {
        ProjectSummary created = projects.create(name, projectNotes,
                ToolSupport.disciplineIdByName(disciplines, disciplineName),
                ToolSupport.parseDate("startDate", startDate),
                ToolSupport.parseDate("targetDate", targetDate));
        return view(created, disciplineNames());
    }

    @Tool(name = "update_project", description = UPDATE_PROJECT)
    @McpTool(name = "update_project", description = UPDATE_PROJECT,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    ProjectView updateProject(
            @ToolParam(description = "The project's UUID")
            @McpToolParam(description = "The project's UUID", required = true) String projectId,
            @ToolParam(description = "New name; pass '' or omit to keep", required = false)
            @McpToolParam(description = "New name; pass '' or omit to keep", required = false) String name,
            @ToolParam(description = "New notes; pass '' or omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New notes; pass '' or omit to keep, 'none' to clear",
                    required = false) String projectNotes,
            @ToolParam(description = "New discipline name; pass '' or omit to keep, 'none' to detach", required = false)
            @McpToolParam(description = "New discipline name; pass '' or omit to keep, 'none' to detach",
                    required = false) String disciplineName,
            @ToolParam(description = "New start date yyyy-MM-dd; pass '' or omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New start date yyyy-MM-dd; pass '' or omit to keep, 'none' to clear",
                    required = false) String startDate,
            @ToolParam(description = "New target date yyyy-MM-dd; pass '' or omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New target date yyyy-MM-dd; pass '' or omit to keep, 'none' to clear",
                    required = false) String targetDate,
            @ToolParam(description = "New status, ACTIVE or DONE; pass '' or omit to keep", required = false)
            @McpToolParam(description = "New status, ACTIVE or DONE; pass '' or omit to keep",
                    required = false) String status) {
        UUID id = UUID.fromString(projectId);
        ProjectSummary current = projects.find(id);
        UUID disciplineId = disciplineName == null || disciplineName.isBlank()
                ? current.disciplineId()
                : ToolSupport.disciplineIdByName(disciplines, disciplineName);
        ProjectSummary updated = projects.update(id,
                name == null || name.isBlank() ? current.name() : name,
                ToolSupport.resolve(projectNotes, current.notes(), String::strip),
                disciplineId,
                ToolSupport.resolve(startDate, current.startDate(), v -> ToolSupport.parseDate("startDate", v)),
                ToolSupport.resolve(targetDate, current.targetDate(), v -> ToolSupport.parseDate("targetDate", v)));
        if (status != null && !status.isBlank()) {
            String normalized = status.strip().toUpperCase(Locale.ROOT);
            if (!normalized.equals("ACTIVE") && !normalized.equals("DONE")) {
                throw new IllegalArgumentException("status must be ACTIVE or DONE — got '" + status + "'");
            }
            updated = projects.setDone(id, normalized.equals("DONE"));
        }
        return view(updated, disciplineNames());
    }

    @Tool(name = "delete_project", description = DELETE_PROJECT)
    @McpTool(name = "delete_project", description = DELETE_PROJECT,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteProject(
            @ToolParam(description = "The project's UUID")
            @McpToolParam(description = "The project's UUID", required = true) String projectId) {
        UUID id = UUID.fromString(projectId);
        ProjectSummary victim = projects.find(id);
        projects.delete(id);
        return "Deleted project \"" + victim.name() + "\"";
    }

    @Tool(name = "manage_milestone", description = MANAGE_MILESTONE)
    @McpTool(name = "manage_milestone", description = MANAGE_MILESTONE,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    ProjectView manageMilestone(
            @ToolParam(description = "The project's UUID")
            @McpToolParam(description = "The project's UUID", required = true) String projectId,
            @ToolParam(description = "What to do with the milestone")
            @McpToolParam(description = "What to do with the milestone",
                    required = true) MilestoneAction action,
            @ToolParam(description = "Milestone name, e.g. 'Essays' (for toggle/remove a unique part matches)")
            @McpToolParam(description = "Milestone name, e.g. 'Essays' (for toggle/remove a unique part matches)",
                    required = true) String name,
            @ToolParam(description = "Due date yyyy-MM-dd, only for ADD; omit if none", required = false)
            @McpToolParam(description = "Due date yyyy-MM-dd, only for ADD; omit if none",
                    required = false) String dueDate) {
        UUID id = UUID.fromString(projectId);
        ProjectSummary result = switch (action) {
            case ADD -> projects.addMilestone(id, name, ToolSupport.parseDate("dueDate", dueDate));
            case TOGGLE -> projects.toggleMilestone(id, milestoneByName(projects.find(id), name).id());
            case REMOVE -> projects.removeMilestone(id, milestoneByName(projects.find(id), name).id());
        };
        return view(result, disciplineNames());
    }

    /** Schema-level constraint: the model cannot send an action outside this set. */
    enum MilestoneAction {
        ADD, TOGGLE, REMOVE
    }

    /** Milestone by (partial) name — fails loudly with the valid list on 0 or 2+ hits. */
    private static MilestoneSummary milestoneByName(ProjectSummary project, String name) {
        String needle = name.strip().toLowerCase(Locale.ROOT);
        List<MilestoneSummary> hits = project.milestones().stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).equals(needle)).toList();
        if (hits.isEmpty()) {
            hits = project.milestones().stream()
                    .filter(m -> m.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
        }
        if (hits.size() == 1) {
            return hits.getFirst();
        }
        String valid = project.milestones().stream().map(MilestoneSummary::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException((hits.isEmpty()
                ? "No milestone matches '" + name + "'"
                : "Milestone '" + name + "' is ambiguous")
                + " in \"" + project.name() + "\" — its milestones are: "
                + (valid.isEmpty() ? "(none)" : valid));
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
