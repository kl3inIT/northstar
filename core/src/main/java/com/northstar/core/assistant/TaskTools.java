package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.parseTime;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Task tools — thin adapters over the task module's public API. Write scope is
 * deliberately create/complete only: no update, no delete.
 */
@Component
class TaskTools implements NorthstarTool {

    private static final String TODAY_TASKS = """
            The user's tasks for today: overdue + due today (open), plus what was already \
            completed today. Use to answer 'what should I/the user do today?'.""";

    private static final String UPCOMING_TASKS = "Open tasks due within the next N days (after today).";

    private static final String CREATE_TASK = """
            Create a task/reminder for the user (e.g. 'remind me to submit the essay \
            tomorrow'). Resolve relative dates yourself before calling.""";

    private static final String COMPLETE_TASK =
            "Mark a task as done by its id (ids come from today_tasks/upcoming_tasks).";

    private final TaskService tasks;

    TaskTools(TaskService tasks) {
        this.tasks = tasks;
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
                    required = false) String taskNotes) {
        return tasks.create(title, taskNotes, parseDate("dueDate", dueDate), parseTime("dueTime", dueTime), null);
    }

    @Tool(name = "complete_task", description = COMPLETE_TASK)
    @McpTool(name = "complete_task", description = COMPLETE_TASK,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    TaskSummary completeTask(
            @ToolParam(description = "The task's UUID")
            @McpToolParam(description = "The task's UUID", required = true) String taskId) {
        return tasks.setDone(UUID.fromString(taskId), true);
    }
}
