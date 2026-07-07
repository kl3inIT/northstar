package com.northstar.core.assistant;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.project.ProjectService;
import com.northstar.core.shared.ColorName;
import com.northstar.core.task.TaskService;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Discipline tools — the LDP spine. Mostly read: other tools accept a
 * disciplineName and resolve it, so the agent needs the list to pick valid
 * names, and occasionally creates a new area on request.
 */
@Component
class DisciplineTools implements NorthstarTool {

    private static final String LIST_DISCIPLINES = """
            The user's disciplines — the life areas everything else hangs off (e.g. \
            'English · IELTS', 'Chinese · HSK'). Use to pick a valid disciplineName \
            before creating or moving tasks, events or projects.""";

    private static final String CREATE_DISCIPLINE = """
            Create a new discipline (life area). Rare — only when the user explicitly \
            starts a new area, not for one-off topics (those are tags or folders).""";

    private static final String UPDATE_DISCIPLINE = """
            Rename or recolor an existing discipline by id. Use list_disciplines first \
            to resolve the exact discipline id; omitted fields keep their current value.""";

    private static final String DELETE_DISCIPLINE = """
            Permanently delete an empty discipline by id. Only use when the user \
            explicitly asks to remove that discipline. Fails if projects, tasks, or \
            calendar events are still linked; move or delete those first.""";

    private final DisciplineService disciplines;
    private final ProjectService projects;
    private final TaskService tasks;
    private final CalendarEventService events;

    DisciplineTools(DisciplineService disciplines, ProjectService projects, TaskService tasks,
            CalendarEventService events) {
        this.disciplines = disciplines;
        this.projects = projects;
        this.tasks = tasks;
        this.events = events;
    }

    @Tool(name = "list_disciplines", description = LIST_DISCIPLINES)
    @McpTool(name = "list_disciplines", description = LIST_DISCIPLINES,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<DisciplineSummary> listDisciplines() {
        return disciplines.list();
    }

    @Tool(name = "create_discipline", description = CREATE_DISCIPLINE)
    @McpTool(name = "create_discipline", description = CREATE_DISCIPLINE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    DisciplineSummary createDiscipline(
            @ToolParam(description = "Discipline name, e.g. 'Japanese · JLPT'")
            @McpToolParam(description = "Discipline name, e.g. 'Japanese · JLPT'",
                    required = true) String name,
            @ToolParam(description = "Display color; defaults to GRAY", required = false)
            @McpToolParam(description = "Display color; defaults to GRAY",
                    required = false) ColorName color) {
        return disciplines.create(name, color == null ? ColorName.GRAY : color);
    }

    @Tool(name = "update_discipline", description = UPDATE_DISCIPLINE)
    @McpTool(name = "update_discipline", description = UPDATE_DISCIPLINE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    DisciplineSummary updateDiscipline(
            @ToolParam(description = "The discipline UUID from list_disciplines")
            @McpToolParam(description = "The discipline UUID from list_disciplines",
                    required = true) String disciplineId,
            @ToolParam(description = "New discipline name; omit or pass blank to keep", required = false)
            @McpToolParam(description = "New discipline name; omit or pass blank to keep",
                    required = false) String name,
            @ToolParam(description = "New display color; omit to keep", required = false)
            @McpToolParam(description = "New display color; omit to keep",
                    required = false) ColorName color) {
        UUID id = UUID.fromString(disciplineId);
        DisciplineSummary current = disciplines.find(id);
        return disciplines.update(id,
                name == null || name.isBlank() ? current.name() : name,
                color == null ? current.color() : color);
    }

    @Tool(name = "delete_discipline", description = DELETE_DISCIPLINE)
    @McpTool(name = "delete_discipline", description = DELETE_DISCIPLINE,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteDiscipline(
            @ToolParam(description = "The discipline UUID from list_disciplines")
            @McpToolParam(description = "The discipline UUID from list_disciplines",
                    required = true) String disciplineId) {
        UUID id = UUID.fromString(disciplineId);
        DisciplineSummary victim = disciplines.find(id);
        long projectCount = projects.countByDiscipline(id);
        long taskCount = tasks.countByDiscipline(id);
        long eventCount = events.countByDiscipline(id);
        if (projectCount > 0 || taskCount > 0 || eventCount > 0) {
            throw new IllegalArgumentException(
                    "Move or delete linked work before deleting discipline \"%s\": %s projects, %s tasks, %s events"
                            .formatted(victim.name(), projectCount, taskCount, eventCount));
        }
        disciplines.delete(id);
        return "Deleted discipline \"" + victim.name() + "\"";
    }
}
