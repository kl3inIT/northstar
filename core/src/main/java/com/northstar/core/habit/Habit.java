package com.northstar.core.habit;

import com.northstar.core.shared.BaseEntity;
import com.northstar.core.shared.ColorName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "habit")
class Habit extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String cue;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ColorName color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HabitStatus status = HabitStatus.ACTIVE;

    @Column(nullable = false, length = 64)
    private String timezone;

    protected Habit() {
    }

    Habit(UUID id, String title, String cue, String notes, ColorName color, String timezone) {
        super(id);
        edit(title, cue, notes, color, timezone);
    }

    String title() { return title; }
    String cue() { return cue; }
    String notes() { return notes; }
    ColorName color() { return color; }
    HabitStatus status() { return status; }
    String timezone() { return timezone; }

    void edit(String title, String cue, String notes, ColorName color, String timezone) {
        this.title = title;
        this.cue = cue;
        this.notes = notes;
        this.color = color;
        this.timezone = timezone;
    }

    void setArchived(boolean archived) {
        status = archived ? HabitStatus.ARCHIVED : HabitStatus.ACTIVE;
    }
}

