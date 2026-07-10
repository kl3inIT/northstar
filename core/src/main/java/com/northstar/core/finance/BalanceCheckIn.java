package com.northstar.core.finance;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/** One aggregate end-of-day balance anchor for the capture-first ledger. */
@Entity
@Table(name = "finance_balance_check_in")
public class BalanceCheckIn extends BaseEntity {

    @Column(name = "checked_on", nullable = false)
    private LocalDate checkedOn;

    @PositiveOrZero
    @Column(name = "actual_balance", nullable = false)
    private long actualBalance;

    @Column(name = "expected_balance", nullable = false)
    private long expectedBalance;

    @Column(nullable = false)
    private long discrepancy;

    @Column(name = "adjustment_transaction_id")
    private UUID adjustmentTransactionId;

    protected BalanceCheckIn() {
        // for JPA
    }

    BalanceCheckIn(UUID id, LocalDate checkedOn, long actualBalance, long expectedBalance,
            long discrepancy, UUID adjustmentTransactionId) {
        super(id);
        this.checkedOn = checkedOn;
        this.actualBalance = actualBalance;
        this.expectedBalance = expectedBalance;
        this.discrepancy = discrepancy;
        this.adjustmentTransactionId = adjustmentTransactionId;
    }

    public LocalDate getCheckedOn() {
        return checkedOn;
    }

    public long getActualBalance() {
        return actualBalance;
    }

    public long getExpectedBalance() {
        return expectedBalance;
    }

    public long getDiscrepancy() {
        return discrepancy;
    }

    public UUID getAdjustmentTransactionId() {
        return adjustmentTransactionId;
    }
}
