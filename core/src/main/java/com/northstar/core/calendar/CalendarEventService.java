package com.northstar.core.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The calendar module's public API. The calendar UI always shows a window
 * (month/week/day), so reads are window-overlap queries; writes are plain CRUD
 * plus {@link #reschedule} for drag-drop and resize.
 */
@Service
public class CalendarEventService {

    private final CalendarEventRepository events;

    CalendarEventService(CalendarEventRepository events) {
        this.events = events;
    }

    /** Events overlapping [from, to) — the visible month/week/day window. */
    @Transactional(readOnly = true)
    public List<CalendarEventSummary> range(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
        return events.findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(to, from)
                .stream().map(this::summary).toList();
    }

    @Transactional
    public CalendarEventSummary create(String title, String notes, Instant startAt, Instant endAt,
            boolean allDay, EventColor color) {
        requireValidSpan(startAt, endAt);
        CalendarEvent event = new CalendarEvent(UUID.randomUUID(), title.strip(), clean(notes),
                startAt, endAt, allDay, color);
        events.save(event);
        return summary(event);
    }

    @Transactional
    public CalendarEventSummary update(UUID id, String title, String notes, Instant startAt,
            Instant endAt, boolean allDay, EventColor color) {
        requireValidSpan(startAt, endAt);
        CalendarEvent event = events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id));
        event.edit(title.strip(), clean(notes), startAt, endAt, allDay, color);
        return summary(event);
    }

    /** Drag-drop / resize: move the block, keep title/notes/color. */
    @Transactional
    public CalendarEventSummary reschedule(UUID id, Instant startAt, Instant endAt) {
        requireValidSpan(startAt, endAt);
        CalendarEvent event = events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id));
        event.reschedule(startAt, endAt);
        return summary(event);
    }

    @Transactional
    public void delete(UUID id) {
        if (!events.existsById(id)) {
            throw new CalendarEventNotFoundException(id);
        }
        events.deleteById(id);
    }

    private static void requireValidSpan(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
    }

    private static String clean(String notes) {
        return notes == null || notes.isBlank() ? null : notes.strip();
    }

    private CalendarEventSummary summary(CalendarEvent event) {
        return new CalendarEventSummary(event.getId(), event.getTitle(), event.getNotes(),
                event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getColor());
    }
}
