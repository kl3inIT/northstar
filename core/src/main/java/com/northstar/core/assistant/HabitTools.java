package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.resolve;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.habit.HabitCheckInStatus;
import com.northstar.core.habit.HabitFrequencyType;
import com.northstar.core.habit.HabitService;
import com.northstar.core.habit.HabitSummary;
import com.northstar.core.habit.HabitTodaySummary;
import com.northstar.core.shared.ColorName;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Habit tools share the same behavior for in-app Chat and external MCP clients. */
@Component
class HabitTools implements NorthstarTool {

    private static final String LIST = """
            List active habits with their cue, schedule, pause state and identity. Use this \
            before editing, pausing or archiving a habit when its id is not already known.""";
    private static final String TODAY = """
            List today's habits with seven-day evidence, weekly progress, consistency and \
            secondary streak data. Use for 'how are my habits today?' and before recording \
            a check-in.""";
    private static final String CREATE = """
            Create a repeated behaviour, not a one-off obligation. Use ON_DAYS with ISO \
            weekdays (MONDAY..SUNDAY), or WEEKLY_TARGET for any N distinct days per week. \
            Include a stable cue such as 'After dinner' when the user gives one.""";
    private static final String UPDATE = """
            Edit a habit definition or make a schedule change effective today. Omitted \
            fields keep their values; pass 'none' to clear cue or notes. Never turn a \
            finite reminder into a habit.""";
    private static final String CHECK_IN = """
            Record one local-date habit repetition as DONE, or an intentional neutral \
            exception as EXCUSED. Omit date for today. This is idempotent and replaces \
            the status for that date.""";
    private static final String CLEAR = """
            Clear a mistaken habit check-in for one date. This restores the derived \
            scheduled/open/missed state; it does not delete the habit.""";
    private static final String PAUSE = """
            Pause a habit from a local date so the interval is neutral in consistency \
            calculations. Omit date for today.""";
    private static final String RESUME = """
            Resume a paused habit on a local date. That date is active again. Omit date \
            for today.""";
    private static final String ARCHIVE = """
            Archive or restore a habit while preserving all schedules and check-ins. Use \
            archive instead of deletion when the user no longer tracks a behaviour.""";

    private final HabitService habits;

    HabitTools(HabitService habits) {
        this.habits = habits;
    }

    @Tool(name = "list_habits", description = LIST)
    @McpTool(name = "list_habits", description = LIST,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<HabitSummary> listHabits() {
        return habits.list(false, LocalDate.now(zone()));
    }

    @Tool(name = "today_habits", description = TODAY)
    @McpTool(name = "today_habits", description = TODAY,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<HabitTodaySummary> todayHabits() {
        return habits.today(zone());
    }

    @Tool(name = "create_habit", description = CREATE)
    @McpTool(name = "create_habit", description = CREATE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    HabitSummary createHabit(
            @ToolParam(description = "Short behaviour name, e.g. 'Shadow English for 15 minutes'")
            @McpToolParam(description = "Short behaviour name", required = true) String title,
            @ToolParam(description = "Stable context cue, e.g. 'After dinner'", required = false)
            @McpToolParam(description = "Stable context cue", required = false) String cue,
            @ToolParam(description = "ON_DAYS or WEEKLY_TARGET", required = false)
            @McpToolParam(description = "ON_DAYS or WEEKLY_TARGET", required = false)
            HabitFrequencyType frequencyType,
            @ToolParam(description = "ISO weekdays for ON_DAYS; omit for every day", required = false)
            @McpToolParam(description = "ISO weekdays for ON_DAYS", required = false)
            List<DayOfWeek> days,
            @ToolParam(description = "Required for WEEKLY_TARGET, from 1 to 7", required = false)
            @McpToolParam(description = "Required for WEEKLY_TARGET, from 1 to 7", required = false)
            Integer weeklyTarget,
            @ToolParam(description = "Optional detail about the minimum version or purpose", required = false)
            @McpToolParam(description = "Optional detail", required = false) String notes,
            @ToolParam(description = "BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE, or GRAY", required = false)
            @McpToolParam(description = "Display color", required = false) String color) {
        HabitFrequencyType type = frequencyType == null ? HabitFrequencyType.ON_DAYS : frequencyType;
        Set<DayOfWeek> selected = selectedDays(type, days);
        int target = weeklyTarget == null ? 1 : weeklyTarget;
        return habits.create(title, cue, notes, parseColor(color, ColorName.GREEN), zone(), type,
                selected, target, LocalDate.now(zone()));
    }

    @Tool(name = "update_habit", description = UPDATE)
    @McpTool(name = "update_habit", description = UPDATE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitSummary updateHabit(
            @ToolParam(description = "Habit UUID from list_habits/today_habits")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "New title; omit to keep", required = false)
            @McpToolParam(description = "New title; omit to keep", required = false) String title,
            @ToolParam(description = "New cue; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New cue; omit to keep, 'none' to clear", required = false) String cue,
            @ToolParam(description = "New notes; omit to keep, 'none' to clear", required = false)
            @McpToolParam(description = "New notes; omit to keep, 'none' to clear", required = false) String notes,
            @ToolParam(description = "ON_DAYS or WEEKLY_TARGET; omit to keep", required = false)
            @McpToolParam(description = "ON_DAYS or WEEKLY_TARGET; omit to keep", required = false)
            HabitFrequencyType frequencyType,
            @ToolParam(description = "Replacement ISO weekdays for ON_DAYS; omit to keep", required = false)
            @McpToolParam(description = "Replacement ISO weekdays; omit to keep", required = false)
            List<DayOfWeek> days,
            @ToolParam(description = "Replacement weekly target 1-7; omit to keep", required = false)
            @McpToolParam(description = "Replacement weekly target 1-7; omit to keep", required = false)
            Integer weeklyTarget,
            @ToolParam(description = "New display color; omit to keep", required = false)
            @McpToolParam(description = "New display color; omit to keep", required = false) String color) {
        UUID id = UUID.fromString(habitId);
        LocalDate today = LocalDate.now(zone());
        HabitSummary current = habits.find(id, today);
        HabitFrequencyType type = frequencyType == null ? current.schedule().frequencyType() : frequencyType;
        Set<DayOfWeek> selected = days == null
                ? current.schedule().days() : selectedDays(type, days);
        int target = weeklyTarget == null ? current.schedule().weeklyTarget() : weeklyTarget;
        return habits.update(id,
                title == null || title.isBlank() ? current.title() : title,
                resolve(cue, current.cue(), String::strip),
                resolve(notes, current.notes(), String::strip),
                parseColor(color, current.color()), zone(), type, selected, target, today);
    }

    @Tool(name = "set_habit_check_in", description = CHECK_IN)
    @McpTool(name = "set_habit_check_in", description = CHECK_IN,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitTodaySummary setHabitCheckIn(
            @ToolParam(description = "Habit UUID")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "Local date yyyy-MM-dd; omit for today", required = false)
            @McpToolParam(description = "Local date yyyy-MM-dd; omit for today", required = false) String date,
            @ToolParam(description = "DONE or EXCUSED")
            @McpToolParam(description = "DONE or EXCUSED", required = true) HabitCheckInStatus status) {
        LocalDate day = date == null || date.isBlank() ? LocalDate.now(zone()) : parseDate("date", date);
        return habits.checkIn(UUID.fromString(habitId), day, status, zone());
    }

    @Tool(name = "clear_habit_check_in", description = CLEAR)
    @McpTool(name = "clear_habit_check_in", description = CLEAR,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitTodaySummary clearHabitCheckIn(
            @ToolParam(description = "Habit UUID")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "Local date yyyy-MM-dd; omit for today", required = false)
            @McpToolParam(description = "Local date yyyy-MM-dd; omit for today", required = false) String date) {
        LocalDate day = date == null || date.isBlank() ? LocalDate.now(zone()) : parseDate("date", date);
        return habits.clearCheckIn(UUID.fromString(habitId), day, zone());
    }

    @Tool(name = "pause_habit", description = PAUSE)
    @McpTool(name = "pause_habit", description = PAUSE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitSummary pauseHabit(
            @ToolParam(description = "Habit UUID")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "Pause start yyyy-MM-dd; omit for today", required = false)
            @McpToolParam(description = "Pause start yyyy-MM-dd; omit for today", required = false) String date) {
        return habits.pause(UUID.fromString(habitId), optionalDate(date));
    }

    @Tool(name = "resume_habit", description = RESUME)
    @McpTool(name = "resume_habit", description = RESUME,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitSummary resumeHabit(
            @ToolParam(description = "Habit UUID")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "Resume date yyyy-MM-dd; omit for today", required = false)
            @McpToolParam(description = "Resume date yyyy-MM-dd; omit for today", required = false) String date) {
        return habits.resume(UUID.fromString(habitId), optionalDate(date));
    }

    @Tool(name = "set_habit_archived", description = ARCHIVE)
    @McpTool(name = "set_habit_archived", description = ARCHIVE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    HabitSummary setHabitArchived(
            @ToolParam(description = "Habit UUID")
            @McpToolParam(description = "Habit UUID", required = true) String habitId,
            @ToolParam(description = "true archives, false restores")
            @McpToolParam(description = "true archives, false restores", required = true) boolean archived) {
        return habits.setArchived(UUID.fromString(habitId), archived, LocalDate.now(zone()));
    }

    private static Set<DayOfWeek> selectedDays(HabitFrequencyType type, List<DayOfWeek> days) {
        if (type == HabitFrequencyType.WEEKLY_TARGET) return Set.of();
        return days == null || days.isEmpty()
                ? Set.copyOf(Arrays.asList(DayOfWeek.values())) : Set.copyOf(days);
    }

    private static ColorName parseColor(String value, ColorName fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return ColorName.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            String valid = Arrays.stream(ColorName.values()).map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("color must be one of: " + valid, exception);
        }
    }

    private static LocalDate optionalDate(String value) {
        return value == null || value.isBlank() ? LocalDate.now(zone()) : parseDate("date", value);
    }
}

