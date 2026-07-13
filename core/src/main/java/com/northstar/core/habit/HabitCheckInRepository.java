package com.northstar.core.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, UUID> {
    Optional<HabitCheckIn> findByHabitIdAndLocalDate(UUID habitId, LocalDate localDate);
    List<HabitCheckIn> findByHabitIdAndLocalDateBetweenOrderByLocalDateAsc(
            UUID habitId, LocalDate from, LocalDate to);
}

