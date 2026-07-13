package com.northstar.core.habit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HabitRepository extends JpaRepository<Habit, UUID> {
    List<Habit> findAllByOrderByCreatedAtAsc();
}

