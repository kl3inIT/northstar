package com.northstar.core.assistant;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.shared.ColorName;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared argument parsing/formatting for the tool classes. Tools speak the
 * user's local wall clock ("yyyy-MM-dd HH:mm"), never UTC instants: the caller
 * is an LLM reasoning about the user's day, and the server owns the zone.
 *
 * <p>Update tools use three-state string args: omitted/"" keeps the current
 * value, the literal {@code "none"} clears a nullable field, anything else is
 * the new value — so the model never has to resend a full object.
 */
final class ToolSupport {

    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Sentinel an update arg uses to clear a nullable field. */
    private static final String CLEAR = "none";

    private ToolSupport() {
    }

    /** Three-state resolve for update tools: keep (null/blank), clear ("none"), or parse. */
    static <T> T resolve(String arg, T current, Function<String, T> parse) {
        if (arg == null || arg.isBlank()) {
            return current;
        }
        if (arg.strip().equalsIgnoreCase(CLEAR)) {
            return null;
        }
        return parse.apply(arg);
    }

    /**
     * Resolves a discipline by (partial) name, case-insensitive; null/blank = none.
     * Ambiguous or unknown names fail loudly with the valid list so the agent can
     * self-correct instead of guessing an id.
     */
    static UUID disciplineIdByName(DisciplineService disciplines, String name) {
        if (name == null || name.isBlank() || name.strip().equalsIgnoreCase(CLEAR)) {
            return null;
        }
        String needle = name.strip().toLowerCase(Locale.ROOT);
        List<DisciplineSummary> all = disciplines.list();
        List<DisciplineSummary> hits = all.stream()
                .filter(d -> d.name().toLowerCase(Locale.ROOT).equals(needle)).toList();
        if (hits.isEmpty()) {
            hits = all.stream()
                    .filter(d -> d.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
        }
        if (hits.size() == 1) {
            return hits.getFirst().id();
        }
        String valid = all.stream().map(DisciplineSummary::name).collect(Collectors.joining(", "));
        throw new IllegalArgumentException((hits.isEmpty()
                ? "No discipline matches '" + name + "'"
                : "Discipline '" + name + "' is ambiguous") + " — the disciplines are: " + valid);
    }

    static ColorName parseColor(String value, ColorName fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ColorName.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "color must be one of BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE, GRAY — got '" + value + "'");
        }
    }

    /** Both api and mcp run on the user's own machine, so the system zone IS the user's zone. */
    static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    static String local(Instant instant, ZoneId zone) {
        return LOCAL_MINUTES.format(LocalDateTime.ofInstant(instant, zone));
    }

    static <T> T required(String field, T value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static LocalDate parseDate(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be yyyy-MM-dd, got '" + value + "'");
        }
    }

    static LocalTime parseTime(String field, String value) {
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
