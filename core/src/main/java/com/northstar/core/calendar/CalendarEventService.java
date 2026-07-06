package com.northstar.core.calendar;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.shared.ColorName;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The calendar module's public API. The calendar UI always shows a window
 * (month/week/day), so reads are window-overlap queries; recurring masters are
 * expanded server-side into one summary per occurrence (GCal semantics:
 * deleting one buổi records an exception, deleting the series removes the
 * master). Writes are plain CRUD plus {@link #reschedule} for drag-drop.
 */
@Service
public class CalendarEventService {

    private final CalendarEventRepository events;
    private final DisciplineService disciplines;

    CalendarEventService(CalendarEventRepository events, DisciplineService disciplines) {
        this.events = events;
        this.disciplines = disciplines;
    }

    /**
     * Events overlapping [from, to) — one-off rows as-is, recurring series
     * expanded in {@code zone} (occurrences keep the master's local
     * time-of-day, minus cancelled ones).
     */
    @Transactional(readOnly = true)
    public List<CalendarEventSummary> range(Instant from, Instant to, ZoneId zone) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
        List<CalendarEventSummary> result = new ArrayList<>(
                events.findByRruleIsNullAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(to, from)
                        .stream().map(this::summary).toList());
        for (CalendarEvent master : events.findByRruleIsNotNullAndStartAtLessThan(to)) {
            RecurrenceRule rule = RecurrenceRule.parse(master.getRrule());
            Duration duration = Duration.between(master.getStartAt(), master.getEndAt());
            for (Instant start : rule.occurrencesBetween(master.getStartAt(), duration, zone, from, to)) {
                if (!master.getCancelledOccurrences().contains(start)) {
                    result.add(occurrence(master, start, duration));
                }
            }
        }
        result.sort(Comparator.comparing(CalendarEventSummary::startAt));
        return result;
    }

    /**
     * Gaps of at least {@code minDuration} between the day's timed events,
     * inside [windowStart, windowEnd) local. All-day events do not block a
     * slot (a deadline banner is not a busy block); overlapping events merge
     * into one busy span.
     */
    @Transactional(readOnly = true)
    public List<FreeSlot> freeSlots(LocalDate date, LocalTime windowStart, LocalTime windowEnd,
            Duration minDuration, ZoneId zone) {
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        if (minDuration.isNegative() || minDuration.isZero()) {
            throw new IllegalArgumentException("minDuration must be positive");
        }
        Instant from = date.atTime(windowStart).atZone(zone).toInstant();
        Instant to = date.atTime(windowEnd).atZone(zone).toInstant();
        List<FreeSlot> slots = new ArrayList<>();
        Instant cursor = from;
        for (CalendarEventSummary event : range(from, to, zone)) {
            if (event.allDay()) {
                continue;
            }
            Instant busyStart = event.startAt().isBefore(from) ? from : event.startAt();
            if (busyStart.isAfter(cursor) && Duration.between(cursor, busyStart).compareTo(minDuration) >= 0) {
                slots.add(new FreeSlot(cursor, busyStart));
            }
            if (event.endAt().isAfter(cursor)) {
                cursor = event.endAt();
            }
        }
        if (to.isAfter(cursor) && Duration.between(cursor, to).compareTo(minDuration) >= 0) {
            slots.add(new FreeSlot(cursor, to));
        }
        return slots;
    }

    /** The raw master row — what an "edit series" form prefills from. */
    @Transactional(readOnly = true)
    public CalendarEventSummary find(UUID id) {
        return summary(events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id)));
    }

    @Transactional
    public CalendarEventSummary create(String title, String notes, Instant startAt, Instant endAt,
            boolean allDay, ColorName color, UUID disciplineId, String rrule) {
        requireValidSpan(startAt, endAt);
        requireDiscipline(disciplineId);
        CalendarEvent event = new CalendarEvent(UUID.randomUUID(), title.strip(), clean(notes),
                startAt, endAt, allDay, color, disciplineId, cleanRrule(rrule));
        events.save(event);
        return summary(event);
    }

    /** Edits the row itself — for a recurring event that means the whole series. */
    @Transactional
    public CalendarEventSummary update(UUID id, String title, String notes, Instant startAt,
            Instant endAt, boolean allDay, ColorName color, UUID disciplineId, String rrule) {
        requireValidSpan(startAt, endAt);
        requireDiscipline(disciplineId);
        CalendarEvent event = events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id));
        event.edit(title.strip(), clean(notes), startAt, endAt, allDay, color, disciplineId, cleanRrule(rrule));
        return summary(event);
    }

    /** Drag-drop / resize of a one-off event: move the block, keep title/notes/color. */
    @Transactional
    public CalendarEventSummary reschedule(UUID id, Instant startAt, Instant endAt) {
        requireValidSpan(startAt, endAt);
        CalendarEvent event = events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id));
        if (event.isRecurring()) {
            // Moving one buổi of a series = cancelOccurrence + a standalone event, driven by the client.
            throw new IllegalArgumentException("Cannot reschedule a recurring series; detach the occurrence instead");
        }
        event.reschedule(startAt, endAt);
        return summary(event);
    }

    /** "Chỉ buổi này": skip one occurrence of a series. The instant must be one the rule generates. */
    @Transactional
    public void cancelOccurrence(UUID id, Instant occurrenceStart) {
        CalendarEvent event = events.findById(id).orElseThrow(() -> new CalendarEventNotFoundException(id));
        if (!event.isRecurring()) {
            throw new IllegalArgumentException("Event " + id + " is not recurring");
        }
        event.cancelOccurrence(occurrenceStart);
    }

    /** Deletes the row — for a recurring event that means the whole series ("cả chuỗi"). */
    @Transactional
    public void delete(UUID id) {
        if (!events.existsById(id)) {
            throw new CalendarEventNotFoundException(id);
        }
        events.deleteById(id);
    }

    private void requireDiscipline(UUID disciplineId) {
        if (disciplineId != null && !disciplines.exists(disciplineId)) {
            throw new IllegalArgumentException("No discipline with id " + disciplineId);
        }
    }

    private static void requireValidSpan(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
    }

    private static String clean(String notes) {
        return notes == null || notes.isBlank() ? null : notes.strip();
    }

    /** Normalizes blank to null and rejects anything outside the supported RRULE subset. */
    private static String cleanRrule(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            return null;
        }
        String stripped = rrule.strip();
        RecurrenceRule.parse(stripped);
        return stripped;
    }

    private CalendarEventSummary summary(CalendarEvent event) {
        return new CalendarEventSummary(event.getId(), event.getTitle(), event.getNotes(),
                event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getColor(),
                event.getDisciplineId(), event.getRrule(), event.getCreatedAt());
    }

    private CalendarEventSummary occurrence(CalendarEvent master, Instant start, Duration duration) {
        return new CalendarEventSummary(master.getId(), master.getTitle(), master.getNotes(),
                start, start.plus(duration), master.isAllDay(), master.getColor(),
                master.getDisciplineId(), master.getRrule(), master.getCreatedAt());
    }
}
