package com.northstar.core.finance;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/** A user-reported saving target; no bank-account model is implied. */
@Entity
@Table(name = "finance_savings_goal")
public class SavingsGoal extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Positive
    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @PositiveOrZero
    @Column(name = "saved_amount", nullable = false)
    private long savedAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @PositiveOrZero
    @Column(name = "monthly_contribution", nullable = false)
    private long monthlyContribution;

    protected SavingsGoal() {
        // for JPA
    }

    SavingsGoal(UUID id, String name, long targetAmount, long savedAmount,
            LocalDate targetDate, long monthlyContribution) {
        super(id);
        edit(name, targetAmount, savedAmount, targetDate, monthlyContribution);
    }

    public String getName() {
        return name;
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public long getSavedAmount() {
        return savedAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public long getMonthlyContribution() {
        return monthlyContribution;
    }

    void edit(String name, long targetAmount, long savedAmount,
            LocalDate targetDate, long monthlyContribution) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.targetDate = targetDate;
        this.monthlyContribution = monthlyContribution;
    }

    void contribute(long amount) {
        try {
            savedAmount = Math.addExact(savedAmount, amount);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("savedAmount exceeds the supported VND range", e);
        }
    }
}
