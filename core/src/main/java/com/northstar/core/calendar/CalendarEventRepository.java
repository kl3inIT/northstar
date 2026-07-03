package com.northstar.core.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link CalendarEvent}. Other modules go through {@link CalendarEventService}. */
interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    /** Events overlapping the visible window [from, to). */
    List<CalendarEvent> findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(Instant to, Instant from);
}
