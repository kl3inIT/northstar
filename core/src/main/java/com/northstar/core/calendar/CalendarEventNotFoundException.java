package com.northstar.core.calendar;

import java.util.UUID;

/** Thrown when a calendar event id does not exist. */
public class CalendarEventNotFoundException extends RuntimeException {

    public CalendarEventNotFoundException(UUID id) {
        super("Calendar event not found: " + id);
    }
}
