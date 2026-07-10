package com.northstar.core.finance;

import jakarta.validation.constraints.NotNull;

/** Result of confirming one subscription payment. */
public record SubscriptionPaymentSummary(
        @NotNull TransactionSummary transaction,
        @NotNull SubscriptionSummary subscription) {
}
