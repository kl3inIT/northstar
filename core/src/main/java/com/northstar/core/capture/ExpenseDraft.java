package com.northstar.core.capture;

import com.northstar.core.finance.TransactionType;
import java.util.List;

/**
 * LLM-parsed money entries. One captured message can carry several ("nay tiêu:
 * sáng 30k, cafe 45k, grab 62k") — each amount becomes one item, so a whole
 * day can be logged in one send. Amounts are already-expanded VND longs (the
 * prompt teaches the 35k/2tr shorthand grammar); {@code occurredOn} stays an
 * ISO STRING for the same provider-structured-output reason as
 * {@link TaskDraft}, "" meaning "no date mentioned" (the client defaults to
 * today). {@code category} is one of the constrained vocabulary values the
 * prompt carries; {@code exceptional} marks a one-off atypical purchase.
 */
public record ExpenseDraft(List<ExpenseItem> items) {

    public record ExpenseItem(
            TransactionType type,
            long amount,
            String description,
            String category,
            String occurredOn,
            boolean exceptional) {
    }
}
