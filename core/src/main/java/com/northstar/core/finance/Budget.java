package com.northstar.core.finance;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** One category limit for one calendar month; actual spend is always derived. */
@Entity
@Table(name = "finance_budget")
public class Budget extends BaseEntity {

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String category;

    @Positive
    @Column(name = "limit_amount", nullable = false)
    private long limitAmount;

    protected Budget() {
        // for JPA
    }

    Budget(UUID id, LocalDate monthStart, String category, long limitAmount) {
        super(id);
        this.monthStart = monthStart;
        this.category = category;
        this.limitAmount = limitAmount;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public String getCategory() {
        return category;
    }

    public long getLimitAmount() {
        return limitAmount;
    }

    void edit(LocalDate monthStart, String category, long limitAmount) {
        this.monthStart = monthStart;
        this.category = category;
        this.limitAmount = limitAmount;
    }
}
