package com.northstar.api.finance;

import com.northstar.core.finance.TransactionType;
import com.northstar.core.finance.SubscriptionCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Request bodies for the finance endpoints. One item shape serves both the
 * batch record (a confirmed capture — possibly several items from one message)
 * and the full-edit update; values arrive already resolved (VND longs, absolute
 * dates) because parsing natural language is capture's job, not the API's.
 */
final class FinanceRequest {

    private FinanceRequest() {
    }

    record TransactionItemRequest(
            @NotNull TransactionType type,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
            @NotNull LocalDate occurredOn,
            @NotBlank @Size(max = 255) String description,
            @Size(max = 64) String category,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exceptional) {
    }

    /** POST body: every confirmed capture item in one transaction. */
    record RecordTransactionsRequest(@NotEmpty @Valid List<TransactionItemRequest> items) {
    }

    /** PUT body: full edit of the user-facing fields (type/source never change). */
    record UpdateTransactionRequest(
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
            @NotNull LocalDate occurredOn,
            @NotBlank @Size(max = 255) String description,
            @Size(max = 64) String category,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exceptional) {
    }

    record BudgetRequest(
            @NotBlank String month,
            @NotBlank @Size(max = 64) String category,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long limitAmount) {
    }

    record SavingsGoalRequest(
            @NotBlank @Size(max = 120) String name,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long targetAmount,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long savedAmount,
            LocalDate targetDate,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long monthlyContribution) {
    }

    record UpdateSavingsGoalRequest(
            @NotBlank @Size(max = 120) String name,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long targetAmount,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long savedAmount,
            LocalDate targetDate,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long monthlyContribution,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record ContributionRequest(
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount) {
    }

    record BalanceCheckInRequest(
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long bankBalance,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cashBalance,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eWalletBalance,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long otherBalance,
            @NotNull LocalDate checkedOn) {
    }

    record SubscriptionRequest(
            @NotBlank @Size(max = 120) String name,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
            @NotBlank @Size(max = 64) String category,
            @NotNull SubscriptionCycle cycle,
            @NotNull LocalDate nextDueOn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
            LocalDate cancelReminderOn) {
    }

    record UpdateSubscriptionRequest(
            @NotBlank @Size(max = 120) String name,
            @Positive @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amount,
            @NotBlank @Size(max = 64) String category,
            @NotNull SubscriptionCycle cycle,
            @NotNull LocalDate nextDueOn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
            LocalDate cancelReminderOn,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record SubscriptionPaymentRequest(
            @NotNull LocalDate occurredOn,
            @NotNull LocalDate expectedDueOn,
            @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }
}
