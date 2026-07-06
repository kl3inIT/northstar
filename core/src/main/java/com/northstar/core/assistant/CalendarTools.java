package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.local;
import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.parseTime;
import static com.northstar.core.assistant.ToolSupport.required;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.calendar.FreeSlot;
import com.northstar.core.shared.ColorName;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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

    private final CalendarEventService events;

    CalendarTools(CalendarEventService events) {
        this.events = events;
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
            @ToolParam(description = "Display color: BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE or GRAY; defaults to BLUE", required = false)
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
}
