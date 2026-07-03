package com.northstar.mcp;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * The northstar_* MCP tools. A thin delivery adapter, exactly like the api's
 * controllers: every tool is one call into a {@code :core} service. The caller
 * is itself an LLM, so tools take structured arguments directly — no extra
 * classification round-trip (that is what the api's AI capture is for).
 *
 * <p>Write scope is deliberately create/complete only — no update, no delete.
 */
@Service
class NorthstarTools {

    private final NoteService notes;
    private final TaskService tasks;

    NorthstarTools(NoteService notes, TaskService tasks) {
        this.notes = notes;
        this.tasks = tasks;
    }

    @McpTool(name = "search_notes", description = """
            Full-text search over the user's personal knowledge base (study notes for \
            IELTS/HSK, scholarship research, project notes, journal). Returns title, slug, \
            folder, tags and a highlighted snippet per hit. Use this BEFORE answering \
            questions about the user's studies, plans or previously saved knowledge.""")
    List<NoteSummary> searchNotes(
            @McpToolParam(description = "Plain keyword query; quoted \"phrases\" and -exclusions are supported",
                    required = true) String query) {
        return notes.search(query);
    }

    @McpTool(name = "get_note", description = """
            Read one note in full (Markdown body, tags, outgoing links and backlinks) \
            by its slug — slugs come from search_notes results.""")
    NoteDetail getNote(
            @McpToolParam(description = "The note's slug, e.g. 'kinh-nghiem-apply-hoc-bong'",
                    required = true) String slug) {
        return notes.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No note with slug '" + slug + "' — find slugs via search_notes."));
    }

    @McpTool(name = "create_note", description = """
            Save new knowledge into the user's knowledge base as a Markdown note. Use when \
            the user learns something worth keeping or asks to note something down. Keep a \
            short capture short; reference related existing notes inline as [[Exact Title]] \
            only when clearly related.""")
    NoteDetail createNote(
            @McpToolParam(description = "Short, specific title", required = true) String title,
            @McpToolParam(description = "Folder path like 'English/IELTS'; empty or omitted = root",
                    required = false) String folderPath,
            @McpToolParam(description = "Note body in Markdown", required = true) String contentMarkdown,
            @McpToolParam(description = "1-4 lowercase tags, reusing the user's existing tags where possible",
                    required = false) List<String> tags) {
        return notes.create(title, folderPath, contentMarkdown, tags);
    }

    @McpTool(name = "today_tasks", description = """
            The user's tasks for today: overdue + due today (open), plus what was already \
            completed today. Use to answer 'what should I/the user do today?'.""")
    List<TaskSummary> todayTasks() {
        return tasks.today(ZoneId.systemDefault());
    }

    @McpTool(name = "upcoming_tasks", description = "Open tasks due within the next N days (after today).")
    List<TaskSummary> upcomingTasks(
            @McpToolParam(description = "Days ahead to look, 1-60; defaults to 7", required = false) Integer days) {
        return tasks.upcoming(ZoneId.systemDefault(), Math.clamp(days == null ? 7 : days, 1, 60));
    }

    @McpTool(name = "create_task", description = """
            Create a task/reminder for the user (e.g. 'remind me to submit the essay \
            tomorrow'). Resolve relative dates yourself before calling.""")
    TaskSummary createTask(
            @McpToolParam(description = "Short imperative title", required = true) String title,
            @McpToolParam(description = "Due date as yyyy-MM-dd; omit for someday",
                    required = false) String dueDate,
            @McpToolParam(description = "Due time as HH:mm, only when a clock time matters",
                    required = false) String dueTime,
            @McpToolParam(description = "Extra detail beyond the title",
                    required = false) String taskNotes) {
        return tasks.create(title, taskNotes, parseDate(dueDate), parseTime(dueTime));
    }

    @McpTool(name = "complete_task", description = "Mark a task as done by its id (ids come from today_tasks/upcoming_tasks).")
    TaskSummary completeTask(
            @McpToolParam(description = "The task's UUID", required = true) String taskId) {
        return tasks.setDone(UUID.fromString(taskId), true);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dueDate must be yyyy-MM-dd, got '" + value + "'");
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dueTime must be HH:mm, got '" + value + "'");
        }
    }
}
