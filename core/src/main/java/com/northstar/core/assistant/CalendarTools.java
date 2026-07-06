package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.disciplineIdByName;
import static com.northstar.core.assistant.ToolSupport.local;
import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.parseTime;
import static com.northstar.core.assistant.ToolSupport.required;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.calendar.FreeSlot;
import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.shared.ColorName;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Calendar tools — thin adapters over the calendar module's public API. Times
 * cross this boundary as zone-local {@code yyyy-MM-dd HH:mm} strings, not UTC
 * instants: the agent reasons in the user's wall clock and the server owns the
 * zone conversion.
 */
@Component
class CalendarTools implements NorthstarTool {

    private static final String UPCOMING_EVENTS = """
            The user's calendar for today plus the next N days: classes, study blocks, \
            appointments, deadlines-as-banners. Recurring series come pre-expanded, one \
            entry per occurrence. Times are the user's local wall clock.""";

    private static final String CREATE_EVENT = """
            Put a block on the user's calendar (class, study block, appointment). Times are \
            the user's local wall clock; resolve relative dates yourself before calling. For \
            a repeating block pass an RFC 5545 rrule limited to FREQ=DAILY|WEEKLY with \
            optional INTERVAL, BYDAY (weekly, e.g. BYDAY=MO,WE), UNTIL=yyyyMMdd or COUNT.""";

    private static final String FIND_FREE_SLOTS = """
            Gaps in the user's calendar on one date that fit a block of the given length — \
            use before proposing a study session or picking a meeting time. All-day \
            deadline banners do not block a slot.""";

    private static final String UPDATE_EVENT = """
            Edit an existing event by its id (ids come from upcoming_events). Only pass \
            the fields to change — omitted fields keep their value. For a recurring event \
            this edits the WHOLE series; to change just one occurrence, cancel_occurrence \
            it and create_event a standalone replacement.""";

    private static final String DELETE_EVENT = """
            Delete an event by its id — for a recurring event this removes the WHOLE \
            series. To drop a single occurrence use cancel_occurrence instead.""";

    private static final String CANCEL_OCCURRENCE = """
            Skip exactly one occurrence of a recurring event ('huỷ lớp tối nay'): the \
            series continues, only that date's occurrence disappears. The start must be \
            an occurrence's exact local start from upcoming_events.""";

    private final CalendarEventService events;
    private final DisciplineService disciplines;

    CalendarTools(CalendarEventService events, DisciplineService disciplines) {
        this.events = events;
        this.disciplines = disciplines;
    }

    @Tool(name = "upcoming_events", description = UPCOMING_EVENTS)
    @McpTool(name = "upcoming_events", description = UPCOMING_EVENTS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<EventView> upcomingEvents(
            @ToolParam(description = "Days ahead beyond today, 0-31; defaults to 7", required = false)
            @McpToolParam(description = "Days ahead beyond today, 0-31; defaults to 7",
                    required = false) Integer days) {
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(Math.clamp(days == null ? 7 : days, 0, 31) + 1L)
                .atStartOfDay(zone).toInstant();
        return events.range(from, to, zone).stream().map(e -> EventView.of(e, zone)).toList();
    }

    @Tool(name = "create_event", description = CREATE_EVENT)
    @McpTool(name = "create_event", description = CREATE_EVENT,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    EventView createEvent(
            @ToolParam(description = "Short event title")
            @McpToolParam(description = "Short event title", required = true) String title,
            @ToolParam(description = "Event date as yyyy-MM-dd (the first date, for a recurring event)")
            @McpToolParam(description = "Event date as yyyy-MM-dd (the first date, for a recurring event)",
                    required = true) String date,
            @ToolParam(description = "Start time as HH:mm")
            @McpToolParam(description = "Start time as HH:mm", required = true) String startTime,
            @ToolParam(description = "End time as HH:mm, after startTime")
            @McpToolParam(description = "End time as HH:mm, after startTime", required = true) String endTime,
            @ToolParam(description = "Extra detail beyond the title", required = false)
            @McpToolParam(description = "Extra detail beyond the title", required = false) String eventNotes,
            @ToolParam(description = "Recurrence rule in the supported subset, e.g. 'FREQ=WEEKLY;BYDAY=MO,WE'; omit for a one-off", required = false)
            @McpToolParam(description = "Recurrence rule in the supported subset, e.g. 'FREQ=WEEKLY;BYDAY=MO,WE'; omit for a one-off",
                    required = false) String rrule,
            @ToolParam(description = "Display color; defaults to BLUE", required = false)
            @McpToolParam(description = "Display color; defaults to BLUE",
                    required = false) ColorName color,
            @ToolParam(description = "Discipline name the event belongs to (see list_disciplines); omit for none", required = false)
            @McpToolParam(description = "Discipline name the event belongs to (see list_disciplines); omit for none",
                    required = false) String disciplineName) {
        ZoneId zone = zone();
        LocalDate day = required("date", parseDate("date", date));
        Instant startAt = day.atTime(required("startTime", parseTime("startTime", startTime)))
                .atZone(zone).toInstant();
        Instant endAt = day.atTime(required("endTime", parseTime("endTime", endTime)))
                .atZone(zone).toInstant();
        return EventView.of(events.create(title, eventNotes, startAt, endAt, false,
                color == null ? ColorName.BLUE : color,
                disciplineIdByName(disciplines, disciplineName), rrule), zone);
    }

    @Tool(name = "update_event", description = UPDATE_EVENT)
    @McpTool(name = "update_event", description = UPDATE_EVENT,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    EventView updateEvent(
            @ToolParam(description = "The event's UUID")
            @McpToolParam(description = "The event's UUID", required = true) String eventId,
            @ToolParam(description = "New title; omit to keep", required = false)
            @McpToolParam(description = "New title; omit to keep", required = false) String title,
            @ToolParam(description = "New date yyyy-MM-dd; omit to keep", required = false)
            @McpToolParam(description = "New date yyyy-MM-dd; omit to keep", required = false) String date,
            @ToolParam(description = "New start time HH:mm; omit to keep", required = false)
            @McpToolParam(description = "New start time HH:mm; omit to keep", required = false) String startTime,
            @ToolParam(description = "New end time HH:mm; omit to keep", required = false)
            @McpToolParam(description = "New end time HH:mm; omit to keep", required = false) String endTime,
            @ToolParam(description = "New notes; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New notes; omit to keep, 'none' to clear",
                    required = false) String eventNotes,
            @ToolParam(description = "New recurrence rule; omit to keep, 'none' to make it a one-off", required = false)
            @McpToolParam(description = "New recurrence rule; omit to keep, 'none' to make it a one-off",
                    required = false) String rrule,
            @ToolParam(description = "New display color; omit to keep", required = false)
            @McpToolParam(description = "New display color; omit to keep", required = false) ColorName color) {
        ZoneId zone = zone();
        CalendarEventSummary current = events.find(UUID.fromString(eventId));
        LocalDateTime curStart = LocalDateTime.ofInstant(current.startAt(), zone);
        LocalDateTime curEnd = LocalDateTime.ofInstant(current.endAt(), zone);
        LocalDate day = date == null || date.isBlank() ? curStart.toLocalDate() : parseDate("date", date);
        LocalTime start = startTime == null || startTime.isBlank()
                ? curStart.toLocalTime() : parseTime("startTime", startTime);
        LocalTime end = endTime == null || endTime.isBlank()
                ? curEnd.toLocalTime() : parseTime("endTime", endTime);
        // Editing times on an all-day banner turns it into a normal timed block.
        boolean allDay = current.allDay() && (startTime == null || startTime.isBlank());
        return EventView.of(events.update(current.id(),
                title == null || title.isBlank() ? current.title() : title,
                ToolSupport.resolve(eventNotes, current.notes(), String::strip),
                day.atTime(start).atZone(zone).toInstant(),
                day.atTime(end).atZone(zone).toInstant(),
                allDay,
                color == null ? current.color() : color,
                current.disciplineId(),
                ToolSupport.resolve(rrule, current.rrule(), String::strip)), zone);
    }

    @Tool(name = "delete_event", description = DELETE_EVENT)
    @McpTool(name = "delete_event", description = DELETE_EVENT,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteEvent(
            @ToolParam(description = "The event's UUID")
            @McpToolParam(description = "The event's UUID", required = true) String eventId) {
        UUID id = UUID.fromString(eventId);
        CalendarEventSummary victim = events.find(id);
        events.delete(id);
        return "Deleted event \"" + victim.title() + "\""
                + (victim.rrule() != null ? " (the whole series)" : "");
    }

    @Tool(name = "cancel_occurrence", description = CANCEL_OCCURRENCE)
    @McpTool(name = "cancel_occurrence", description = CANCEL_OCCURRENCE,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = true,
                    openWorldHint = false))
    String cancelOccurrence(
            @ToolParam(description = "The recurring event's UUID")
            @McpToolParam(description = "The recurring event's UUID", required = true) String eventId,
            @ToolParam(description = "The occurrence's local start, 'yyyy-MM-dd HH:mm', exactly as upcoming_events returned it")
            @McpToolParam(description = "The occurrence's local start, 'yyyy-MM-dd HH:mm', exactly as upcoming_events returned it",
                    required = true) String occurrenceStart) {
        ZoneId zone = zone();
        Instant start = LocalDateTime
                .parse(occurrenceStart.strip(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atZone(zone).toInstant();
        events.cancelOccurrence(UUID.fromString(eventId), start);
        return "Cancelled the " + occurrenceStart + " occurrence; the series continues.";
    }

    @Tool(name = "find_free_slots", description = FIND_FREE_SLOTS)
    @McpTool(name = "find_free_slots", description = FIND_FREE_SLOTS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<FreeSlotView> findFreeSlots(
            @ToolParam(description = "The date to inspect, yyyy-MM-dd")
            @McpToolParam(description = "The date to inspect, yyyy-MM-dd", required = true) String date,
            @ToolParam(description = "Minimum slot length in minutes, 15-480; defaults to 60", required = false)
            @McpToolParam(description = "Minimum slot length in minutes, 15-480; defaults to 60",
                    required = false) Integer durationMinutes,
            @ToolParam(description = "Start of the day window as HH:mm; defaults to 07:00", required = false)
            @McpToolParam(description = "Start of the day window as HH:mm; defaults to 07:00",
                    required = false) String dayStart,
            @ToolParam(description = "End of the day window as HH:mm; defaults to 22:00", required = false)
            @McpToolParam(description = "End of the day window as HH:mm; defaults to 22:00",
                    required = false) String dayEnd) {
        ZoneId zone = zone();
        LocalTime windowStart = dayStart == null || dayStart.isBlank()
                ? LocalTime.of(7, 0) : parseTime("dayStart", dayStart);
        LocalTime windowEnd = dayEnd == null || dayEnd.isBlank()
                ? LocalTime.of(22, 0) : parseTime("dayEnd", dayEnd);
        Duration minDuration = Duration.ofMinutes(Math.clamp(durationMinutes == null ? 60 : durationMinutes, 15, 480));
        return events.freeSlots(required("date", parseDate("date", date)), windowStart, windowEnd, minDuration, zone)
                .stream().map(slot -> FreeSlotView.of(slot, zone)).toList();
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

}
