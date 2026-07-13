package com.northstar.core.habit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HabitScheduleRepository extends JpaRepository<HabitSchedule, UUID> {
    List<HabitSchedule> findByHabitIdOrderByEffectiveFromAsc(UUID habitId);
}

