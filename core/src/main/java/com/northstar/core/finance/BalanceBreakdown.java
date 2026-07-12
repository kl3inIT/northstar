package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;

/** The explicit components of one aggregate end-of-day balance. */
public record BalanceBreakdown(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long bankBalance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cashBalance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eWalletBalance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long otherBalance) {

    public long totalBalance() {
        try {
            return Math.addExact(Math.addExact(bankBalance, cashBalance),
                    Math.addExact(eWalletBalance, otherBalance));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("balance breakdown exceeds the supported VND range",
                    exception);
        }
    }
}
