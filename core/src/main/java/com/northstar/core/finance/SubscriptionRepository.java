package com.northstar.core.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findAllByOrderByActiveDescNextDueOnAsc();

    /** Active charges due on or before {@code limit} — the weekly review's reminder window. */
    List<Subscription> findByActiveTrueAndNextDueOnLessThanEqualOrderByNextDueOnAsc(LocalDate limit);

    /** Active subscriptions whose cancel-reminder date falls in a window and has no task yet. */
    List<Subscription> findByActiveTrueAndCancelReminderTaskIdIsNullAndCancelReminderOnLessThanEqual(
            LocalDate limit);
}
