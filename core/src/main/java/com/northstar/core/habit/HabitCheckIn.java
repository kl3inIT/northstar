package com.northstar.core.habit;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_check_in")
class HabitCheckIn extends BaseEntity {

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HabitCheckInStatus status;

    protected HabitCheckIn() {
    }

    HabitCheckIn(UUID id, UUID habitId, LocalDate localDate, HabitCheckInStatus status) {
        super(id);
        this.habitId = habitId;
        this.localDate = localDate;
        this.status = status;
    }

    UUID habitId() { return habitId; }
    LocalDate localDate() { return localDate; }
    HabitCheckInStatus status() { return status; }
    void setStatus(HabitCheckInStatus status) { this.status = status; }
}

