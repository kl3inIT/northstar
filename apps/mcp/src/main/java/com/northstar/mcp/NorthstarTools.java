package com.northstar.mcp;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.calendar.FreeSlot;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.shared.ColorName;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
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
 * Every tool carries MCP behavior hints ({@code readOnlyHint} on reads,
 * {@code destructiveHint=false} on the additive writes, {@code openWorldHint=false}
 * everywhere — this server only ever touches the user's own database).
 *
 * <p>Calendar times cross this boundary as zone-local {@code yyyy-MM-dd HH:mm}
 * strings, not UTC instants: the agent reasons in the user's wall clock, and the
 * server (running on the user's machine) owns the zone conversion.
 */
@Service
class NorthstarTools {

    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NoteService notes;
    private final TaskService tasks;
    private final CalendarEventService events;

    NorthstarTools(NoteService notes, TaskService tasks, CalendarEventService events) {
        this.notes = notes;
        this.tasks = tasks;
        this.events = events;
    }

    @McpTool(name = "search_notes",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = """
            Full-text search over the user's personal knowledge base (study notes for \
            IELTS/HSK, scholarship research, project notes, journal). Returns title, slug, \
            folder, tags and a highlighted snippet per hit. Use this BEFORE answering \
            questions about the user's studies, plans or previously saved knowledge.""")
    List<NoteSummary> searchNotes(
            @McpToolParam(description = "Plain keyword query; quoted \"phrases\" and -exclusions are supported",
                    required = true) String query) {
        return notes.search(query);
    }

    @McpTool(name = "get_note",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = """
            Read one note in full (Markdown body, tags, outgoing links and backlinks) \
            by its slug — slugs come from search_notes results.""")
    NoteDetail getNote(
            @McpToolParam(description = "The note's slug, e.g. 'kinh-nghiem-apply-hoc-bong'",
                    required = true) String slug) {
        return notes.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No note with slug '" + slug + "' — find slugs via search_notes."));
    }

    @McpTool(name = "create_note",
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false),
            description = """
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
        // Machine-drafted → STAGING: the user reviews it in the Notes staging tab.
        return notes.create(title, folderPath, contentMarkdown, tags, NoteStatus.STAGING);
    }

    @McpTool(name = "today_tasks",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = """
            The user's tasks for today: overdue + due today (open), plus what was already \
            completed today. Use to answer 'what should I/the user do today?'.""")
    List<TaskSummary> todayTasks() {
        return tasks.today(zone());
    }

    @McpTool(name = "upcoming_tasks",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = "Open tasks due within the next N days (after today).")
    List<TaskSummary> upcomingTasks(
            @McpToolParam(description = "Days ahead to look, 1-60; defaults to 7", required = false) Integer days) {
        return tasks.upcoming(zone(), Math.clamp(days == null ? 7 : days, 1, 60));
    }

    @McpTool(name = "create_task",
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false),
            description = """
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
        return tasks.create(title, taskNotes, parseDate("dueDate", dueDate), parseTime("dueTime", dueTime), null);
    }

    @McpTool(name = "complete_task",
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false),
            description = "Mark a task as done by its id (ids come from today_tasks/upcoming_tasks).")
    TaskSummary completeTask(
            @McpToolParam(description = "The task's UUID", required = true) String taskId) {
        return tasks.setDone(UUID.fromString(taskId), true);
    }

    @McpTool(name = "upcoming_events",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = """
            The user's calendar for today plus the next N days: classes, study blocks, \
            appointments, deadlines-as-banners. Recurring series come pre-expanded, one \
            entry per occurrence. Times are the user's local wall clock.""")
    List<EventView> upcomingEvents(
            @McpToolParam(description = "Days ahead beyond today, 0-31; defaults to 7",
                    required = false) Integer days) {
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(Math.clamp(days == null ? 7 : days, 0, 31) + 1L)
                .atStartOfDay(zone).toInstant();
        return events.range(from, to, zone).stream().map(e -> EventView.of(e, zone)).toList();
    }

    @McpTool(name = "create_event",
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false),
            description = """
            Put a block on the user's calendar (class, study block, appointment). Times are \
            the user's local wall clock; resolve relative dates yourself before calling. For \
            a repeating block pass an RFC 5545 rrule limited to FREQ=DAILY|WEEKLY with \
            optional INTERVAL, BYDAY (weekly, e.g. BYDAY=MO,WE), UNTIL=yyyyMMdd or COUNT.""")
    EventView createEvent(
            @McpToolParam(description = "Short event title", required = true) String title,
            @McpToolParam(description = "Event date as yyyy-MM-dd (the first date, for a recurring event)",
                    required = true) String date,
            @McpToolParam(description = "Start time as HH:mm", required = true) String startTime,
            @McpToolParam(description = "End time as HH:mm, after startTime", required = true) String endTime,
            @McpToolParam(description = "Extra detail beyond the title", required = false) String eventNotes,
            @McpToolParam(description = "Recurrence rule in the supported subset, e.g. 'FREQ=WEEKLY;BYDAY=MO,WE'; omit for a one-off",
                    required = false) String rrule,
            @McpToolParam(description = "Display color: BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE or GRAY; defaults to BLUE",
                    required = false) String color) {
        ZoneId zone = zone();
        LocalDate day = required("date", parseDate("date", date));
        Instant startAt = day.atTime(required("startTime", parseTime("startTime", startTime)))
                .atZone(zone).toInstant();
        Instant endAt = day.atTime(required("endTime", parseTime("endTime", endTime)))
                .atZone(zone).toInstant();
        return EventView.of(events.create(title, eventNotes, startAt, endAt, false,
                parseColor(color), null, rrule), zone);
    }

    @McpTool(name = "find_free_slots",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
            description = """
            Gaps in the user's calendar on one date that fit a block of the given length — \
            use before proposing a study session or picking a meeting time. All-day \
            deadline banners do not block a slot.""")
    List<FreeSlotView> findFreeSlots(
            @McpToolParam(description = "The date to inspect, yyyy-MM-dd", required = true) String date,
            @McpToolParam(description = "Minimum slot length in minutes, 15-480; defaults to 60",
                    required = false) Integer durationMinutes,
            @McpToolParam(description = "Start of the day window as HH:mm; defaults to 07:00",
                    required = false) String dayStart,
            @McpToolParam(description = "End of the day window as HH:mm; defaults to 22:00",
                    required = false) String dayEnd) {
        ZoneId zone = zone();
        LocalTime windowStart = dayStart == null || dayStart.isBlank()
                ? LocalTime.of(7, 0) : parseTime("dayStart", dayStart);
        LocalTime windowEnd = dayEnd == null || dayEnd.isBlank()
                ? LocalTime.of(22, 0) : parseTime("dayEnd", dayEnd);
        Duration minDuration = Duration.ofMinutes(Math.clamp(durationMinutes == null ? 60 : durationMinutes, 15, 480));
        return events.freeSlots(required("date", parseDate("date", date)), windowStart, windowEnd, minDuration, zone).stream()
                .map(slot -> FreeSlotView.of(slot, zone)).toList();
    }

    /** Calendar entry as the agent should read it: user-local wall-clock times. */
    record EventView(String id, String title, String start, String end, boolean allDay,
            boolean recurring, String notes) {

        static EventView of(CalendarEventSummary event, ZoneId zone) {
            return new EventView(event.id().toString(), event.title(),
                    local(event.startAt(), zone), local(event.endAt(), zone),
                    event.allDay(), event.rrule() != null, event.notes());
        }
    }

    /** A free gap, user-local, with the length spelled out so the agent need not subtract. */
    record FreeSlotView(String start, String end, long minutes) {

        static FreeSlotView of(FreeSlot slot, ZoneId zone) {
            return new FreeSlotView(local(slot.startAt(), zone), local(slot.endAt(), zone),
                    Duration.between(slot.startAt(), slot.endAt()).toMinutes());
        }
    }

    /** The mcp app runs on the user's own machine, so the system zone IS the user's zone. */
    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private static String local(Instant instant, ZoneId zone) {
        return LOCAL_MINUTES.format(LocalDateTime.ofInstant(instant, zone));
    }

    /** find_free_slots' date param goes through the same guard. */
    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static ColorName parseColor(String value) {
        if (value == null || value.isBlank()) {
            return ColorName.BLUE;
        }
        try {
            return ColorName.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "color must be one of BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE, GRAY — got '" + value + "'");
        }
    }

    private static LocalDate parseDate(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be yyyy-MM-dd, got '" + value + "'");
        }
    }

    private static LocalTime parseTime(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be HH:mm, got '" + value + "'");
        }
    }
}
