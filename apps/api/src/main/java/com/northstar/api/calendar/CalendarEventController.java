package com.northstar.api.calendar;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST delivery for the calendar module. The client sends UTC instants and
 * computes its visible window locally, so no timezone header is needed here.
 * Body validation is Bean Validation on the request records; violations become
 * 400 ProblemDetail via the global advice.
 */
@RestController
@RequestMapping("/api/calendar/events")
class CalendarEventController {

    /** Widest window a client may request — a year view plus margin. */
    private static final long MAX_RANGE_DAYS = 400;

    private final CalendarEventService events;

    CalendarEventController(CalendarEventService events) {
        this.events = events;
    }

    @GetMapping
    List<CalendarEventSummary> range(
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to) {
        if (!to.isAfter(from) || from.plus(java.time.Duration.ofDays(MAX_RANGE_DAYS)).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid range");
        }
        return events.range(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CalendarEventSummary create(@Valid @RequestBody CalendarEventRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.create(request.title(), request.notes(), request.startAt(), request.endAt(),
                request.allDay(), request.color());
    }

    @PutMapping("/{id}")
    CalendarEventSummary update(@PathVariable UUID id, @Valid @RequestBody CalendarEventRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.update(id, request.title(), request.notes(), request.startAt(), request.endAt(),
                request.allDay(), request.color());
    }

    /** Drag-drop / resize: move the block without resending the text fields. */
    @PatchMapping("/{id}/schedule")
    CalendarEventSummary reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.reschedule(id, request.startAt(), request.endAt());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        events.delete(id);
    }

    private static void requireValidSpan(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
        }
    }
}
