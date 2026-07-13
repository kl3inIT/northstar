package com.northstar.core.habit;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_pause")
class HabitPause extends BaseEntity {

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected HabitPause() {
    }

    HabitPause(UUID id, UUID habitId, LocalDate startDate) {
        super(id);
        this.habitId = habitId;
        this.startDate = startDate;
    }

    UUID habitId() { return habitId; }
    LocalDate startDate() { return startDate; }
    LocalDate endDate() { return endDate; }
    boolean includes(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }
    void close(LocalDate endDate) { this.endDate = endDate; }
}

