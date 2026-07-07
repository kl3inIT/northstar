package com.northstar.core.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link CalendarEvent}. Other modules go through {@link CalendarEventService}. */
interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    /** One-off events overlapping the visible window [from, to). */
    List<CalendarEvent> findByRruleIsNullAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(Instant to, Instant from);

    /** Recurring masters whose series has started before the window's end — expanded in code. */
    List<CalendarEvent> findByRruleIsNotNullAndStartAtLessThan(Instant to);

    /** Master rows linked to one discipline, including recurring series. */
    long countByDisciplineId(UUID disciplineId);
}
