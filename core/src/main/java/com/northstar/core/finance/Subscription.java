package com.northstar.core.finance;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** A recurring charge definition; payments enter the ledger only when confirmed. */
@Entity
@Table(name = "finance_subscription")
public class Subscription extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Positive
    @Column(nullable = false)
    private long amount;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionCycle cycle;

    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;

    @Column(name = "billing_anchor_month", nullable = false)
    private int billingAnchorMonth;

    @Column(name = "billing_anchor_day", nullable = false)
    private int billingAnchorDay;

    @Column(nullable = false)
    private boolean active;

    /**
     * Optional "remind me to cancel/review by this date" — a trial about to
     * convert, or a planned cancellation. The worker turns it into one task.
     */
    @Column(name = "cancel_reminder_on")
    private LocalDate cancelReminderOn;

    /** The reminder task already created for {@code cancelReminderOn} — guards against duplicates. */
    @Column(name = "cancel_reminder_task_id")
    private UUID cancelReminderTaskId;

    protected Subscription() {
        // for JPA
    }

    Subscription(UUID id, String name, long amount, String category,
            SubscriptionCycle cycle, LocalDate nextDueOn, boolean active,
            LocalDate cancelReminderOn) {
        super(id);
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.cycle = cycle;
        this.nextDueOn = nextDueOn;
        this.active = active;
        this.cancelReminderOn = cancelReminderOn;
        resetBillingAnchor(nextDueOn);
    }

    public String getName() {
        return name;
    }

    public long getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public SubscriptionCycle getCycle() {
        return cycle;
    }

    public LocalDate getNextDueOn() {
        return nextDueOn;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getCancelReminderOn() {
        return cancelReminderOn;
    }

    public UUID getCancelReminderTaskId() {
        return cancelReminderTaskId;
    }

    void edit(String name, long amount, String category,
            SubscriptionCycle cycle, LocalDate nextDueOn, boolean active,
            LocalDate cancelReminderOn) {
        boolean scheduleChanged = this.cycle != cycle || !this.nextDueOn.equals(nextDueOn);
        // A different reminder date deserves a fresh task; clearing the date drops it.
        if (this.cancelReminderOn == null
                ? cancelReminderOn != null : !this.cancelReminderOn.equals(cancelReminderOn)) {
            this.cancelReminderTaskId = null;
        }
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.cycle = cycle;
        this.nextDueOn = nextDueOn;
        this.active = active;
        this.cancelReminderOn = cancelReminderOn;
        if (scheduleChanged) {
            resetBillingAnchor(nextDueOn);
        }
    }

    /** Remember the reminder task the worker created for the current cancel date. */
    void cancelReminderCreated(UUID taskId) {
        this.cancelReminderTaskId = taskId;
    }

    void advanceCycle() {
        YearMonth target = cycle == SubscriptionCycle.YEARLY
                ? YearMonth.of(nextDueOn.getYear() + 1, billingAnchorMonth)
                : YearMonth.from(nextDueOn).plusMonths(1);
        nextDueOn = target.atDay(Math.min(billingAnchorDay, target.lengthOfMonth()));
    }

    private void resetBillingAnchor(LocalDate dueOn) {
        billingAnchorMonth = dueOn.getMonthValue();
        billingAnchorDay = dueOn.getDayOfMonth();
    }
}
