package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.disciplineIdByName;
import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.parseTime;
import static com.northstar.core.assistant.ToolSupport.resolve;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Task tools — thin adapters over the task module's public API, full CRUD:
 * the chat is a first-class client, so anything the Tasks page can do to a
 * task, a sentence can too.
 */
@Component
class TaskTools implements NorthstarTool {

    private static final String TODAY_TASKS = """
            The user's tasks for today: overdue + due today (open), plus what was already \
            completed today. Use to answer 'what should I/the user do today?'.""";

    private static final String UPCOMING_TASKS = "Open tasks due within the next N days (after today).";

    private static final String FIND_TASKS = """
            Find tasks whose title contains the query (case-insensitive), across open, \
            done and someday tasks. Use to resolve which task the user means before \
            updating, completing or deleting one that is not in today/upcoming.""";

    private static final String CREATE_TASK = """
            Create a task/reminder for the user (e.g. 'remind me to submit the essay \
            tomorrow'). Resolve relative dates yourself before calling.""";

    private static final String UPDATE_TASK = """
            Edit an existing task: retitle, reschedule the deadline, change notes, plan a \
            'do' date, move it to another discipline, or file it under a project. Only pass \
            the fields to change — omitted fields keep their value; pass 'none' to clear a \
            date, discipline or project.""";

    private static final String SET_TASK_DONE = """
            Mark a task done (the user finished it) or reopen it (done=false undoes a \
            mistaken completion). Task ids come from today_tasks/upcoming_tasks/find_tasks.""";

    private static final String DELETE_TASK = """
            Permanently delete a task. Only for a task the user explicitly asked to remove; \
            for 'I did it' use set_task_done instead.""";

    private final TaskService tasks;
    private final DisciplineService disciplines;

    TaskTools(TaskService tasks, DisciplineService disciplines) {
        this.tasks = tasks;
        this.disciplines = disciplines;
    }

    @Tool(name = "today_tasks", description = TODAY_TASKS)
    @McpTool(name = "today_tasks", description = TODAY_TASKS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<TaskSummary> todayTasks() {
        return tasks.today(zone());
    }

    @Tool(name = "upcoming_tasks", description = UPCOMING_TASKS)
    @McpTool(name = "upcoming_tasks", description = UPCOMING_TASKS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<TaskSummary> upcomingTasks(
            @ToolParam(description = "Days ahead to look, 1-60; defaults to 7", required = false)
            @McpToolParam(description = "Days ahead to look, 1-60; defaults to 7",
                    required = false) Integer days) {
        return tasks.upcoming(zone(), Math.clamp(days == null ? 7 : days, 1, 60));
    }

    @Tool(name = "find_tasks", description = FIND_TASKS)
    @McpTool(name = "find_tasks", description = FIND_TASKS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<TaskSummary> findTasks(
            @ToolParam(description = "Part of the task title, e.g. 'essay'")
            @McpToolParam(description = "Part of the task title, e.g. 'essay'",
                    required = true) String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(zone());
        List<TaskSummary> pool = new ArrayList<>(tasks.someday());
        pool.addAll(tasks.range(today.minusDays(180), today.plusDays(365)));
        return pool.stream()
                .filter(t -> t.title().toLowerCase(Locale.ROOT).contains(needle))
                .distinct()
                .limit(20)
                .toList();
    }

    @Tool(name = "create_task", description = CREATE_TASK)
    @McpTool(name = "create_task", description = CREATE_TASK,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    TaskSummary createTask(
            @ToolParam(description = "Short imperative title")
            @McpToolParam(description = "Short imperative title", required = true) String title,
            @ToolParam(description = "Due date as yyyy-MM-dd; omit for someday", required = false)
            @McpToolParam(description = "Due date as yyyy-MM-dd; omit for someday",
                    required = false) String dueDate,
            @ToolParam(description = "Due time as HH:mm, only when a clock time matters", required = false)
            @McpToolParam(description = "Due time as HH:mm, only when a clock time matters",
                    required = false) String dueTime,
            @ToolParam(description = "Extra detail beyond the title", required = false)
            @McpToolParam(description = "Extra detail beyond the title",
                    required = false) String taskNotes,
            @ToolParam(description = "Discipline name the task belongs to (see list_disciplines), e.g. 'IELTS'; omit for none", required = false)
            @McpToolParam(description = "Discipline name the task belongs to (see list_disciplines), e.g. 'IELTS'; omit for none",
                    required = false) String disciplineName) {
        return tasks.create(title, taskNotes, parseDate("dueDate", dueDate), parseTime("dueTime", dueTime),
                disciplineIdByName(disciplines, disciplineName));
    }

    @Tool(name = "update_task", description = UPDATE_TASK)
    @McpTool(name = "update_task", description = UPDATE_TASK,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    TaskSummary updateTask(
            @ToolParam(description = "The task's UUID")
            @McpToolParam(description = "The task's UUID", required = true) String taskId,
            @ToolParam(description = "New title; omit to keep", required = false)
            @McpToolParam(description = "New title; omit to keep", required = false) String title,
            @ToolParam(description = "New due date yyyy-MM-dd; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New due date yyyy-MM-dd; omit to keep, 'none' to clear",
                    required = false) String dueDate,
            @ToolParam(description = "New due time HH:mm; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New due time HH:mm; omit to keep, 'none' to clear",
                    required = false) String dueTime,
            @ToolParam(description = "New notes; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New notes; omit to keep, 'none' to clear",
                    required = false) String taskNotes,
            @ToolParam(description = "Planned 'do' date yyyy-MM-dd (the star on the Tasks page); omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "Planned 'do' date yyyy-MM-dd (the star on the Tasks page); omit to keep, 'none' to clear",
                    required = false) String plannedDate,
            @ToolParam(description = "Discipline name; omit to keep, 'none' to detach", required = false)
            @McpToolParam(description = "Discipline name; omit to keep, 'none' to detach",
                    required = false) String disciplineName,
            @ToolParam(description = "Project UUID (from list_projects) to file the task under; omit to keep, 'none' to detach", required = false)
            @McpToolParam(description = "Project UUID (from list_projects) to file the task under; omit to keep, 'none' to detach",
                    required = false) String projectId) {
        TaskSummary current = tasks.find(UUID.fromString(taskId));
        UUID disciplineId = disciplineName == null || disciplineName.isBlank()
                ? current.disciplineId()
                : disciplineIdByName(disciplines, disciplineName);
        TaskSummary updated = tasks.update(current.id(),
                title == null || title.isBlank() ? current.title() : title,
                resolve(taskNotes, current.notes(), String::strip),
                resolve(dueDate, current.dueDate(), v -> parseDate("dueDate", v)),
                resolve(dueTime, current.dueTime(), v -> parseTime("dueTime", v)),
                resolve(plannedDate, current.plannedDate(), v -> parseDate("plannedDate", v)),
                disciplineId);
        if (projectId != null && !projectId.isBlank()) {
            updated = tasks.setProject(current.id(), resolve(projectId, current.projectId(),
                    v -> UUID.fromString(v.strip())));
        }
        return updated;
    }

    @Tool(name = "set_task_done", description = SET_TASK_DONE)
    @McpTool(name = "set_task_done", description = SET_TASK_DONE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    TaskSummary setTaskDone(
            @ToolParam(description = "The task's UUID")
            @McpToolParam(description = "The task's UUID", required = true) String taskId,
            @ToolParam(description = "true = mark done, false = reopen")
            @McpToolParam(description = "true = mark done, false = reopen",
                    required = true) boolean done) {
        return tasks.setDone(UUID.fromString(taskId), done);
    }

    @Tool(name = "delete_task", description = DELETE_TASK)
    @McpTool(name = "delete_task", description = DELETE_TASK,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteTask(
            @ToolParam(description = "The task's UUID")
            @McpToolParam(description = "The task's UUID", required = true) String taskId) {
        UUID id = UUID.fromString(taskId);
        TaskSummary victim = tasks.find(id);
        tasks.delete(id);
        return "Deleted task \"" + victim.title() + "\"";
    }

}
