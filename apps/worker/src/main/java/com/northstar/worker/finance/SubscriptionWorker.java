package com.northstar.worker.finance;

import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.SubscriptionSummary;
import com.northstar.core.finance.TransactionSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Subscriptions maintain themselves: when a due date arrives this sweep writes
 * the expense and advances the cycle (source SUBSCRIPTION), so the user never
 * has to remember a mark-paid ritual — a reminder that depends on the user
 * acting is just a chore. A subscription can also carry a cancel-reminder date
 * ("nhắc tôi hủy trước ngày X" — a trial about to convert, a planned
 * cancellation): the sweep turns it into ONE task due that day; the created
 * task id is stored on the subscription so it never duplicates, and moving or
 * clearing the date re-arms it.
 *
 * <p>Runs every few hours instead of a midnight cron: {@code postDueSubscriptions}
 * is idempotent (posted charges move the due date into the future), and a
 * machine asleep at midnight still catches up on the next tick.
 */
@NullMarked
@Component
class SubscriptionWorker {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionWorker.class);

    /** Reminder tasks appear this many days before their cancel date (due = the date itself). */
    private static final int REMINDER_LEAD_DAYS = 7;

    private final FinanceService finance;
    private final TaskService tasks;

    SubscriptionWorker(FinanceService finance, TaskService tasks) {
        this.finance = finance;
        this.tasks = tasks;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 6 * 60 * 60 * 1000)
    void sweep() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        postDueCharges(today);
        createCancelReminders(today);
    }

    private void postDueCharges(LocalDate today) {
        try {
            for (TransactionSummary posted : finance.postDueSubscriptions(today)) {
                log.info("Posted subscription charge: {} {} VND on {}",
                        posted.description(), posted.amount(), posted.occurredOn());
            }
        } catch (Exception e) {
            log.warn("Subscription auto-post failed — next tick retries", e);
        }
    }

    private void createCancelReminders(LocalDate today) {
        try {
            for (SubscriptionSummary subscription : finance.subscriptionsNeedingCancelReminder(
                    today.plusDays(REMINDER_LEAD_DAYS))) {
                LocalDate remindOn = subscription.cancelReminderOn();
                TaskSummary task = tasks.create(
                        "Cancel %s?".formatted(subscription.name()),
                        ("You asked to be reminded to cancel/review %s by %s "
                                + "(%,d VND %s, next charge %s). Cancel the service, "
                                + "or keep it and just tick this task off.")
                                .formatted(subscription.name(), remindOn, subscription.amount(),
                                        subscription.cycle().name().toLowerCase(Locale.ROOT),
                                        subscription.nextDueOn()),
                        // Due on the chosen date; a date already past lands today (late beats never).
                        remindOn.isBefore(today) ? today : remindOn,
                        null, null);
                finance.markCancelReminderCreated(subscription.id(), task.id());
                log.info("Created cancel reminder for {} (task {})", subscription.name(), task.id());
            }
        } catch (Exception e) {
            log.warn("Cancel-reminder sweep failed — next tick retries", e);
        }
    }
}
