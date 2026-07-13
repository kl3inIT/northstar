package com.northstar.core.habit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HabitPauseRepository extends JpaRepository<HabitPause, UUID> {
    List<HabitPause> findByHabitIdOrderByStartDateAsc(UUID habitId);
}

