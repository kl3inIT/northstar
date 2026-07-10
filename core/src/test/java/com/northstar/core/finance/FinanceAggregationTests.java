package com.northstar.core.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure aggregation + vocabulary semantics: the month header math the page and
 * the weekly review depend on, and the category-merge rule that keeps the LLM's
 * vocabulary from drifting. No database — mirrors the RecurrenceRule test style.
 */
class FinanceAggregationTests {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    private static TransactionSummary row(TransactionType type, long amount, String category,
            boolean exceptional) {
        return new TransactionSummary(UUID.randomUUID(), type, amount,
                LocalDate.of(2026, 7, 10), "x", category, exceptional,
                TransactionSource.CAPTURE, Instant.parse("2026-07-10T03:00:00Z"));
    }

    @Test
    void monthSummaryAggregatesTotalsNetAndExceptional() {
        MonthSummary summary = MonthSummary.of(JULY, List.of(
                row(TransactionType.EXPENSE, 35_000, "Ăn uống", false),
                row(TransactionType.EXPENSE, 45_000, "Cafe", false),
                row(TransactionType.EXPENSE, 500_000, "Hiếu hỉ", true),
                row(TransactionType.EXPENSE, 690_000, "Mua sắm", true),
                row(TransactionType.INCOME, 18_500_000, "Lương", false)),
                7_600_000);

        assertThat(summary.month()).isEqualTo("2026-07");
        assertThat(summary.expenseTotal()).isEqualTo(1_270_000);
        assertThat(summary.incomeTotal()).isEqualTo(18_500_000);
        assertThat(summary.net()).isEqualTo(17_230_000);
        assertThat(summary.exceptionalTotal()).isEqualTo(1_190_000);
        assertThat(summary.exceptionalCount()).isEqualTo(2);
        assertThat(summary.previousMonthExpenseTotal()).isEqualTo(7_600_000);
    }

    @Test
    void categoryTotalsAreExpenseOnlySortedLargestFirstWithOneOffMarks() {
        MonthSummary summary = MonthSummary.of(JULY, List.of(
                row(TransactionType.EXPENSE, 35_000, "Ăn uống", false),
                row(TransactionType.EXPENSE, 120_000, "Ăn uống", false),
                row(TransactionType.EXPENSE, 690_000, "Mua sắm", true),
                row(TransactionType.INCOME, 1_000_000, "Thưởng", false)),
                0);

        assertThat(summary.categories()).containsExactly(
                new MonthSummary.CategoryTotal("Mua sắm", 690_000, true),
                new MonthSummary.CategoryTotal("Ăn uống", 155_000, false));
    }

    @Test
    void emptyMonthYieldsZeroesAndNoCategories() {
        MonthSummary summary = MonthSummary.of(JULY, List.of(), 0);

        assertThat(summary.expenseTotal()).isZero();
        assertThat(summary.net()).isZero();
        assertThat(summary.exceptionalCount()).isZero();
        assertThat(summary.categories()).isEmpty();
    }

    @Test
    void vocabularyKeepsSeedOrderAndAppendsUnknownUsedValuesSorted() {
        List<String> merged = FinanceService.mergeVocabulary(
                List.of("Ăn uống", "Cafe", "Khác"),
                List.of("Thú cưng", "cafe", "Ăn uống", "Bảo hiểm"));

        assertThat(merged).containsExactly("Ăn uống", "Cafe", "Khác", "Bảo hiểm", "Thú cưng");
    }
}
