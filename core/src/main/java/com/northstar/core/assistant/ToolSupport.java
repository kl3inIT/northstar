package com.northstar.core.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared argument parsing/formatting for the tool classes. Tools speak the
 * user's local wall clock ("yyyy-MM-dd HH:mm"), never UTC instants: the caller
 * is an LLM reasoning about the user's day, and the server owns the zone.
 */
final class ToolSupport {

    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ToolSupport() {
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
