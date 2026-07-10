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
import java.util.UUID;

/**
 * One ledger entry. {@code amount} is VND as a plain long — the currency has no
 * decimals, so there is nothing a BigDecimal would add. {@code occurredOn} is
 * when the money moved (the user often logs "hôm qua đổ xăng 80k" a day late),
 * distinct from the audit {@code createdAt}. {@code exceptional} marks a one-off
 * atypical purchase; it is AI-suggested at capture time and user-overridable —
 * the weekly review aggregates exceptional spend separately from routine
 * category totals.
 */
@Entity
@Table(name = "finance_transaction")
public class Transaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;

    @Positive
    @Column(nullable = false)
    private long amount;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String description;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false)
    private boolean exceptional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionSource source;

    protected Transaction() {
        // for JPA
    }

    public Transaction(UUID id, TransactionType type, long amount, LocalDate occurredOn,
            String description, String category, boolean exceptional, TransactionSource source) {
        super(id);
        this.type = type;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.description = description;
        this.category = category;
        this.exceptional = exceptional;
        this.source = source;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public boolean isExceptional() {
        return exceptional;
    }

    public TransactionSource getSource() {
        return source;
    }

    /** Full edit of the user-facing fields; {@code type} and {@code source} never change. */
    public void edit(long amount, LocalDate occurredOn, String description, String category,
            boolean exceptional) {
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.description = description;
        this.category = category;
        this.exceptional = exceptional;
    }
}
