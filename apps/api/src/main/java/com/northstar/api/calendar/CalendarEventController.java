package com.northstar.api.calendar;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST delivery for the calendar module. The client sends UTC instants and
 * computes its visible window locally; X-Timezone (same header the task
 * endpoints use) tells the server which local time-of-day recurring series
 * expand at. Body validation is Bean Validation on the request records;
 * violations become 400 ProblemDetail via the global advice.
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
            @RequestParam("to") Instant to,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        if (!to.isAfter(from) || from.plus(Duration.ofDays(MAX_RANGE_DAYS)).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid range");
        }
        return events.range(from, to, zone(tz));
    }

    /** The raw master row — what the "edit series" form prefills from. */
    @GetMapping("/{id}")
    CalendarEventSummary find(@PathVariable UUID id) {
        return events.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CalendarEventSummary create(@Valid @RequestBody CalendarEventRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.create(request.title(), request.notes(), request.startAt(), request.endAt(),
                request.allDay(), request.color(), request.disciplineId(), request.rrule());
    }

    /** Edits the row itself — for a recurring event that means the whole series. */
    @PutMapping("/{id}")
    CalendarEventSummary update(@PathVariable UUID id, @Valid @RequestBody CalendarEventRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.update(id, request.title(), request.notes(), request.startAt(), request.endAt(),
                request.allDay(), request.color(), request.disciplineId(), request.rrule());
    }

    /** Drag-drop / resize: move the block without resending the text fields. */
    @PatchMapping("/{id}/schedule")
    CalendarEventSummary reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        requireValidSpan(request.startAt(), request.endAt());
        return events.reschedule(id, request.startAt(), request.endAt());
    }

    /**
     * Without {@code occurrenceStart}: delete the event (a recurring series
     * entirely — "cả chuỗi"). With it: cancel just that occurrence ("chỉ buổi
     * này").
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id,
            @RequestParam(name = "occurrenceStart", required = false) Instant occurrenceStart) {
        if (occurrenceStart == null) {
            events.delete(id);
        } else {
            events.cancelOccurrence(id, occurrenceStart);
        }
    }

    private static void requireValidSpan(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
        }
    }

    private static ZoneId zone(String tz) {
        try {
            return tz == null ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
