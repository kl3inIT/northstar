package com.northstar.core.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Time-based Finance read model: trend, category composition, and daily rhythm. */
public record FinanceInsights(
        @NotNull LocalDate through,
        @NotNull List<MonthlyPoint> months,
        @NotNull List<MonthSummary.CategoryTotal> categories,
        @NotNull List<DailyPoint> days) {

    public record MonthlyPoint(
            @NotNull String month,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expenseTotal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long incomeTotal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long net,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long exceptionalTotal) {
    }

    public record DailyPoint(
            @NotNull LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expenseTotal) {
    }
}
