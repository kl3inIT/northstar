package com.northstar.core.calendar;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * A time-blocked calendar entry — when something HAPPENS (a study session, a
 * class, an appointment), as opposed to a task's due date (when something is
 * DUE). All-day events span the local midnights the client chose; {@code allDay}
 * only switches how the UI renders the span.
 */
@Entity
@Table(name = "calendar_event")
public class CalendarEvent extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String notes;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventColor color = EventColor.BLUE;

    protected CalendarEvent() {
        // for JPA
    }

    public CalendarEvent(UUID id, String title, String notes, Instant startAt, Instant endAt,
            boolean allDay, EventColor color) {
        super(id);
        this.title = title;
        this.notes = notes;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.color = color;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public EventColor getColor() {
        return color;
    }

    public void edit(String title, String notes, Instant startAt, Instant endAt,
            boolean allDay, EventColor color) {
        this.title = title;
        this.notes = notes;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.color = color;
    }

    /** Drag-drop / resize: move the block without touching the text fields. */
    public void reschedule(Instant startAt, Instant endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
