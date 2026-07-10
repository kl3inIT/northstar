package com.northstar.core.finance;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One month of the ledger, aggregated for the Finance page header: totals, the
 * exceptional (one-off) aggregate the research says feedback must separate, a
 * previous-month expense reference, and expense-only category totals sorted
 * largest first. Income is a total, never a category bar — the page answers
 * "where did the money go".
 */
public record MonthSummary(
        @NotNull String month,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expenseTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long incomeTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long net,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long exceptionalTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int exceptionalCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long previousMonthExpenseTotal,
        @NotNull List<CategoryTotal> categories) {

    /** Expense total of one category; {@code hasExceptional} marks bars containing one-offs. */
    public record CategoryTotal(
            @NotNull String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasExceptional) {
    }

    /** Pure aggregation over one month's rows — testable without a database. */
    static MonthSummary of(YearMonth month, List<TransactionSummary> rows,
            long previousMonthExpenseTotal) {
        long expense = 0;
        long income = 0;
        long exceptionalTotal = 0;
        int exceptionalCount = 0;
        Map<String, Accumulator> byCategory = new LinkedHashMap<>();
        for (TransactionSummary row : rows) {
            if (row.type() == TransactionType.INCOME) {
                income += row.amount();
                continue;
            }
            expense += row.amount();
            if (row.exceptional()) {
                exceptionalTotal += row.amount();
                exceptionalCount++;
            }
            Accumulator acc = byCategory.computeIfAbsent(row.category(), c -> new Accumulator());
            acc.total += row.amount();
            acc.hasExceptional |= row.exceptional();
        }
        List<CategoryTotal> categories = new ArrayList<>();
        byCategory.forEach((name, acc) -> categories.add(
                new CategoryTotal(name, acc.total, acc.hasExceptional)));
        categories.sort((a, b) -> Long.compare(b.total(), a.total()));
        return new MonthSummary(month.toString(), expense, income, income - expense,
                exceptionalTotal, exceptionalCount, previousMonthExpenseTotal,
                List.copyOf(categories));
    }

    private static final class Accumulator {
        long total;
        boolean hasExceptional;
    }
}
