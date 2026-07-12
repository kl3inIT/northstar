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
    @Column(name = "bank_balance", nullable = false)
    private long bankBalance;

    @PositiveOrZero
    @Column(name = "cash_balance", nullable = false)
    private long cashBalance;

    @PositiveOrZero
    @Column(name = "e_wallet_balance", nullable = false)
    private long eWalletBalance;

    @PositiveOrZero
    @Column(name = "other_balance", nullable = false)
    private long otherBalance;

    @Column(name = "expected_balance", nullable = false)
    private long expectedBalance;

    @Column(nullable = false)
    private long discrepancy;

    @Column(name = "adjustment_transaction_id")
    private UUID adjustmentTransactionId;

    protected BalanceCheckIn() {
        // for JPA
    }

    BalanceCheckIn(UUID id, LocalDate checkedOn, BalanceBreakdown breakdown,
            long expectedBalance, long discrepancy, UUID adjustmentTransactionId) {
        super(id);
        this.checkedOn = checkedOn;
        this.bankBalance = breakdown.bankBalance();
        this.cashBalance = breakdown.cashBalance();
        this.eWalletBalance = breakdown.eWalletBalance();
        this.otherBalance = breakdown.otherBalance();
        this.expectedBalance = expectedBalance;
        this.discrepancy = discrepancy;
        this.adjustmentTransactionId = adjustmentTransactionId;
    }

    public LocalDate getCheckedOn() {
        return checkedOn;
    }

    public BalanceBreakdown getBreakdown() {
        return new BalanceBreakdown(bankBalance, cashBalance, eWalletBalance, otherBalance);
    }

    public long getTotalBalance() {
        return getBreakdown().totalBalance();
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
