package com.northstar.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.core.finance.BalanceBreakdown;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.NewTransaction;
import com.northstar.core.finance.SubscriptionCycle;
import com.northstar.core.finance.SubscriptionSummary;
import com.northstar.core.finance.TransactionNotFoundException;
import com.northstar.core.finance.TransactionSource;
import com.northstar.core.finance.TransactionSummary;
import com.northstar.core.finance.TransactionType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Finance acceptance against the real PostgreSQL schema and generated HTTP contract. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class FinanceServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    FinanceService finance;

    @Autowired
    MockMvc mvc;

    @Test
    void planningOpenApiMarksDerivedPrimitivesAsRequired() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/finance/budgets'].post.operationId")
                        .value("createBudget"))
                .andExpect(jsonPath("$.paths['/api/finance/savings-goals/{id}/contributions']"
                        + ".post.operationId").value("contributeSavingsGoal"))
                .andExpect(jsonPath("$.paths['/api/finance/balance-check-ins'].post.operationId")
                        .value("createBalanceCheckIn"))
                .andExpect(jsonPath("$.paths['/api/finance/balance-check-ins/{id}'].delete.operationId")
                        .value("undoBalanceCheckIn"))
                .andExpect(jsonPath("$.paths['/api/finance/insights'].get.operationId")
                        .value("getFinanceInsights"))
                .andExpect(jsonPath("$.paths['/api/finance/recurring-suggestions'].get.operationId")
                        .value("listRecurringSuggestions"))
                .andExpect(jsonPath("$.components.schemas.BudgetSummary.required",
                        hasItems("limitAmount", "spentAmount", "remainingAmount",
                                "progressPercent", "overBudget")))
                .andExpect(jsonPath("$.components.schemas.SavingsGoalSummary.required",
                        hasItems("targetAmount", "savedAmount", "remainingAmount",
                                "monthlyContribution", "progressPercent", "completed", "version")))
                .andExpect(jsonPath("$.components.schemas.SavingsGoalRequest.required",
                        hasItems("targetAmount", "savedAmount", "monthlyContribution")))
                .andExpect(jsonPath("$.components.schemas.UpdateSavingsGoalRequest.required",
                        hasItems("targetAmount", "savedAmount", "monthlyContribution", "version")))
                .andExpect(jsonPath("$.components.schemas.SubscriptionSummary.required",
                        hasItems("amount", "active", "monthlyEquivalent", "version")))
                .andExpect(jsonPath("$.components.schemas.UpdateSubscriptionRequest.required",
                        hasItems("amount", "active", "version")))
                .andExpect(jsonPath("$.components.schemas.SubscriptionPaymentRequest.required",
                        hasItems("occurredOn", "expectedDueOn", "version")))
                .andExpect(jsonPath("$.components.schemas.BalanceCheckInRequest.required",
                        hasItems("bankBalance", "cashBalance", "eWalletBalance", "otherBalance",
                                "checkedOn")))
                .andExpect(jsonPath("$.components.schemas.BalanceCheckInSummary.required",
                        hasItems("breakdown", "totalBalance", "expectedBalance", "discrepancy")));
    }

    @Test
    void recordEditSearchAndDeleteRoundTrip() {
        LocalDate day = LocalDate.of(2031, 1, 14);
        TransactionSummary saved = finance.record(
                item(TransactionType.EXPENSE, 125_000, day, "Pet care probe", "Thu cung", false),
                TransactionSource.CAPTURE);

        assertThat(finance.month(YearMonth.of(2031, 1)))
                .extracting(TransactionSummary::id).contains(saved.id());
        assertThat(finance.categories(TransactionType.EXPENSE))
                .contains("Cafe", "Thu cung");

        TransactionSummary updated = finance.update(saved.id(), 150_000, day.plusDays(1),
                "Pet care probe updated", "Gia dinh", true);

        assertThat(updated.amount()).isEqualTo(150_000);
        assertThat(updated.occurredOn()).isEqualTo(day.plusDays(1));
        assertThat(updated.exceptional()).isTrue();
        assertThat(updated.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(updated.source()).isEqualTo(TransactionSource.CAPTURE);
        assertThat(finance.search("probe updated"))
                .extracting(TransactionSummary::id).containsExactly(saved.id());

        finance.delete(saved.id());
        assertThatThrownBy(() -> finance.find(saved.id()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void monthAndTypicalWeekSummariesUsePersistedRows() {
        finance.record(item(TransactionType.EXPENSE, 400_000, LocalDate.of(2032, 2, 14),
                "Previous month", "Khac", false), TransactionSource.MANUAL);
        finance.record(item(TransactionType.EXPENSE, 35_000, LocalDate.of(2032, 3, 2),
                "Breakfast", "An uong", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.EXPENSE, 500_000, LocalDate.of(2032, 3, 3),
                "Wedding gift", "Hieu hi", true), TransactionSource.ASSISTANT);
        finance.record(item(TransactionType.INCOME, 1_000_000, LocalDate.of(2032, 3, 4),
                "Bonus", "Thuong", false), TransactionSource.ASSISTANT);

        var summary = finance.monthSummary(YearMonth.of(2032, 3));

        assertThat(summary.expenseTotal()).isEqualTo(535_000);
        assertThat(summary.incomeTotal()).isEqualTo(1_000_000);
        assertThat(summary.net()).isEqualTo(465_000);
        assertThat(summary.exceptionalTotal()).isEqualTo(500_000);
        assertThat(summary.exceptionalCount()).isEqualTo(1);
        assertThat(summary.previousMonthExpenseTotal()).isEqualTo(400_000);
        assertThat(summary.categories()).extracting(c -> c.name())
                .containsExactly("Hiếu hỉ", "Ăn uống");

        LocalDate weekStart = LocalDate.of(2033, 6, 6);
        long[] priorWeekTotals = {100_000, 200_000, 300_000, 1_000_000};
        for (int i = 0; i < priorWeekTotals.length; i++) {
            finance.record(item(TransactionType.EXPENSE, priorWeekTotals[i],
                    weekStart.minusWeeks(i + 1), "Typical week " + i, "Khac", false),
                    TransactionSource.MANUAL);
        }
        assertThat(finance.typicalWeekExpense(weekStart)).isEqualTo(250_000);
    }

    @Test
    void caseAndAccentVariantsReuseOneCanonicalLedgerLabel() {
        YearMonth month = YearMonth.of(2039, 7);
        finance.record(item(TransactionType.EXPENSE, 30_000, month.atDay(2),
                "Lunch", "An uong", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.EXPENSE, 45_000, month.atDay(3),
                "Dinner", "ĂN UỐNG", false), TransactionSource.ASSISTANT);

        assertThat(finance.month(month))
                .extracting(TransactionSummary::category)
                .containsOnly("Ăn uống");
        assertThat(finance.monthSummary(month).categories())
                .singleElement()
                .satisfies(category -> {
                    assertThat(category.name()).isEqualTo("Ăn uống");
                    assertThat(category.total()).isEqualTo(75_000);
                });
    }

    @Test
    void balanceCheckInAnchorsTheLedgerAndCreatesImmutableAdjustments() throws Exception {
        LocalDate baseline = LocalDate.of(2040, 1, 1);
        var first = finance.checkInBalance(balance(10_000_000, 0), baseline, baseline);

        assertThat(first.expectedBalance()).isEqualTo(10_000_000);
        assertThat(first.totalBalance()).isEqualTo(10_000_000);
        assertThat(first.discrepancy()).isZero();
        assertThat(first.adjustment()).isNull();

        finance.record(item(TransactionType.INCOME, 500_000, baseline.plusDays(1),
                "Cash received", "Khác", false), TransactionSource.CAPTURE);
        var cashCheckIn = finance.checkInBalance(balance(10_000_000, 500_000),
                baseline.plusDays(1), baseline.plusDays(1));

        assertThat(cashCheckIn.expectedBalance()).isEqualTo(10_500_000);
        assertThat(cashCheckIn.totalBalance()).isEqualTo(10_500_000);
        assertThat(cashCheckIn.breakdown().cashBalance()).isEqualTo(500_000);
        assertThat(cashCheckIn.discrepancy()).isZero();

        finance.record(item(TransactionType.EXPENSE, 1_000_000, baseline.plusDays(2),
                "Rent after baseline", "Nhà cửa", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.INCOME, 200_000, baseline.plusDays(2),
                "Refund after baseline", "Khác", false), TransactionSource.CAPTURE);

        var missingExpense = finance.checkInBalance(balance(9_000_000, 500_000), baseline.plusDays(2),
                baseline.plusDays(2));
        assertThat(missingExpense.expectedBalance()).isEqualTo(9_700_000);
        assertThat(missingExpense.discrepancy()).isEqualTo(-200_000);
        assertThat(missingExpense.adjustment().type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(missingExpense.adjustment().source())
                .isEqualTo(TransactionSource.RECONCILIATION);
        assertThat(missingExpense.adjustment().category()).isEqualTo("Khác");

        var missingIncome = finance.checkInBalance(balance(9_100_000, 500_000), baseline.plusDays(3),
                baseline.plusDays(3));
        assertThat(missingIncome.expectedBalance()).isEqualTo(9_500_000);
        assertThat(missingIncome.discrepancy()).isEqualTo(100_000);
        assertThat(missingIncome.adjustment().type()).isEqualTo(TransactionType.INCOME);

        assertThatThrownBy(() -> finance.update(missingExpense.adjustment().id(), 1,
                baseline.plusDays(2), "tamper", "Khác", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed by their balance check-in");
        assertThatThrownBy(() -> finance.delete(missingExpense.adjustment().id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finance.undoBalanceCheckIn(first.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only the latest");

        mvc.perform(delete("/api/finance/balance-check-ins/{id}", missingIncome.id()))
                .andExpect(status().isNoContent());
        assertThatThrownBy(() -> finance.find(missingIncome.adjustment().id()))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThat(finance.balanceCheckIns()).extracting(item -> item.checkedOn())
                .containsExactly(baseline.plusDays(2), baseline.plusDays(1), baseline);
    }

    @Test
    void insightsAndRecurringSuggestionsAreDerivedFromLedgerHistory() {
        LocalDate through = LocalDate.of(2041, 5, 20);
        finance.record(item(TransactionType.EXPENSE, 120_000, LocalDate.of(2041, 1, 15),
                "YouTube Premium probe", "Giải trí", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.EXPENSE, 125_000, LocalDate.of(2041, 2, 15),
                "YouTube Premium probe", "Giải trí", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.EXPENSE, 120_000, LocalDate.of(2041, 3, 15),
                "YouTube Premium probe", "Giải trí", false), TransactionSource.CAPTURE);
        finance.record(item(TransactionType.EXPENSE, 90_000, through,
                "May insight probe", "Cafe", false), TransactionSource.CAPTURE);

        var insights = finance.insights(through);
        assertThat(insights.months()).hasSize(12);
        assertThat(insights.months().getFirst().month()).isEqualTo("2040-06");
        assertThat(insights.months().getLast().month()).isEqualTo("2041-05");
        assertThat(insights.categories()).extracting(item -> item.name()).contains("Cafe");
        assertThat(insights.days()).hasSize(365)
                .last().satisfies(day -> {
                    assertThat(day.date()).isEqualTo(through);
                    assertThat(day.expenseTotal()).isEqualTo(90_000);
                });

        var suggestion = finance.recurringSuggestions(through).stream()
                .filter(item -> item.name().equals("YouTube Premium probe"))
                .findFirst().orElseThrow();
        assertThat(suggestion.cycle()).isEqualTo(SubscriptionCycle.MONTHLY);
        assertThat(suggestion.amount()).isEqualTo(120_000);
        assertThat(suggestion.occurrences()).isEqualTo(3);
        assertThat(suggestion.nextExpectedOn()).isAfter(through);

        finance.createSubscription(suggestion.name(), suggestion.amount(), suggestion.category(),
                suggestion.cycle(), suggestion.nextExpectedOn(), true, null);
        assertThat(finance.recurringSuggestions(through))
                .noneMatch(item -> item.key().equals(suggestion.key()));
    }

    @Test
    void monthlyBudgetCrudDerivesOverspendAndRejectsDuplicateCategory() {
        YearMonth month = YearMonth.of(2034, 5);
        finance.record(item(TransactionType.EXPENSE, 120_000, month.atDay(4),
                "Budget probe", "Cafe", false), TransactionSource.CAPTURE);

        var first = finance.createBudget(month, "Cafe", 100_000);

        assertThat(first.spentAmount()).isEqualTo(120_000);
        assertThat(first.remainingAmount()).isEqualTo(-20_000);
        assertThat(first.progressPercent()).isEqualTo(120);
        assertThat(first.overBudget()).isTrue();

        assertThatThrownBy(() -> finance.createBudget(month, "cafe", 150_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        var updated = finance.updateBudget(first.id(), month, "Cafe", 150_000);
        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.remainingAmount()).isEqualTo(30_000);
        assertThat(updated.overBudget()).isFalse();
        assertThat(finance.budgets(month)).hasSize(1)
                .allSatisfy(budget -> assertThat(budget.inherited()).isFalse());

        // A month with no rows of its own carries the latest earlier month's
        // limits forward, measured against the NEW month's (empty) spend.
        assertThat(finance.budgets(month.plusMonths(1)))
                .singleElement()
                .satisfies(carried -> {
                    assertThat(carried.inherited()).isTrue();
                    assertThat(carried.month()).isEqualTo(month.plusMonths(1).toString());
                    assertThat(carried.limitAmount()).isEqualTo(150_000);
                    assertThat(carried.spentAmount()).isZero();
                });

        finance.deleteBudget(first.id());
        assertThat(finance.budgets(month)).isEmpty();
    }

    @Test
    void savingsGoalContributionUpdatesProgressWithoutCreatingAnExpense() throws Exception {
        YearMonth emptyMonth = YearMonth.of(2036, 8);
        var created = finance.createSavingsGoal("Emergency reserve", 10_000_000,
                2_000_000, LocalDate.of(2037, 8, 1), 500_000);

        var contributed = finance.contributeSavingsGoal(created.id(), 750_000);

        assertThat(contributed.savedAmount()).isEqualTo(2_750_000);
        assertThat(contributed.remainingAmount()).isEqualTo(7_250_000);
        assertThat(contributed.progressPercent()).isEqualTo(28);
        assertThat(contributed.version()).isGreaterThan(created.version());
        assertThat(finance.month(emptyMonth)).isEmpty();

        mvc.perform(put("/api/finance/savings-goals/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale edit",
                                  "targetAmount": 12000000,
                                  "savedAmount": 2000000,
                                  "targetDate": "2037-12-01",
                                  "monthlyContribution": 600000,
                                  "version": %d
                                }
                                """.formatted(created.version())))
                .andExpect(status().isConflict());

        assertThatThrownBy(() -> finance.updateSavingsGoal(created.id(), "Stale edit",
                12_000_000, created.savedAmount(), LocalDate.of(2037, 12, 1), 600_000,
                created.version()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        var updated = finance.updateSavingsGoal(created.id(), "Emergency fund",
                12_000_000, contributed.savedAmount(), LocalDate.of(2037, 12, 1), 600_000,
                contributed.version());
        assertThat(updated.name()).isEqualTo("Emergency fund");
        assertThat(updated.targetAmount()).isEqualTo(12_000_000);
        assertThat(updated.savedAmount()).isEqualTo(contributed.savedAmount());
        assertThat(updated.version()).isGreaterThan(contributed.version());

        finance.deleteSavingsGoal(created.id());
        assertThat(finance.savingsGoals()).noneMatch(goal -> goal.id().equals(created.id()));
    }

    @Test
    void serviceRejectsTextLongerThanPersistenceColumns() {
        LocalDate day = LocalDate.of(2038, 4, 3);

        assertThatThrownBy(() -> finance.record(item(TransactionType.EXPENSE, 10_000, day,
                "d".repeat(256), "Cafe", false), TransactionSource.ASSISTANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("description must be at most 255 characters");
        assertThatThrownBy(() -> finance.record(item(TransactionType.EXPENSE, 10_000, day,
                "Coffee", "c".repeat(65), false), TransactionSource.ASSISTANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("category must be at most 64 characters");
        assertThatThrownBy(() -> finance.createBudget(YearMonth.of(2038, 4),
                "c".repeat(65), 1_000_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("category must be at most 64 characters");
        assertThatThrownBy(() -> finance.createSavingsGoal("g".repeat(121), 1_000_000,
                0, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must be at most 120 characters");
        assertThatThrownBy(() -> finance.createSubscription("s".repeat(121), 100_000,
                "Hóa đơn", SubscriptionCycle.MONTHLY, day, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must be at most 120 characters");
    }

    @Test
    void subscriptionPaymentRejectsStaleEditAndDuplicateRetry() throws Exception {
        LocalDate due = LocalDate.of(2025, 1, 15);
        LocalDate paidOn = LocalDate.of(2025, 1, 10);
        var subscription = finance.createSubscription("AI subscription", 500_000,
                "Hoc tap", SubscriptionCycle.MONTHLY, due, true, null);

        String paymentBody = """
                {
                  "occurredOn": "%s",
                  "expectedDueOn": "%s",
                  "version": %d
                }
                """.formatted(paidOn, due, subscription.version());
        mvc.perform(post("/api/finance/subscriptions/{id}/payments", subscription.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction.description").value("AI subscription"))
                .andExpect(jsonPath("$.subscription.nextDueOn").value("2025-02-15"));

        SubscriptionSummary paid = subscription(subscription.id());
        assertThat(paid.version()).isGreaterThan(subscription.version());
        assertThat(finance.month(YearMonth.from(paidOn)))
                .filteredOn(row -> row.description().equals("AI subscription"))
                .hasSize(1);

        mvc.perform(post("/api/finance/subscriptions/{id}/payments", subscription.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody))
                .andExpect(status().isConflict());
        assertThat(finance.month(YearMonth.from(paidOn)))
                .filteredOn(row -> row.description().equals("AI subscription"))
                .hasSize(1);

        mvc.perform(put("/api/finance/subscriptions/{id}", subscription.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stale AI subscription",
                                  "amount": 500000,
                                  "category": "Học tập",
                                  "cycle": "MONTHLY",
                                  "nextDueOn": "2025-02-15",
                                  "active": true,
                                  "version": %d
                                }
                                """.formatted(subscription.version())))
                .andExpect(status().isConflict());

        var paused = finance.updateSubscription(subscription.id(), subscription.name(),
                subscription.amount(), subscription.category(), SubscriptionCycle.YEARLY,
                paid.nextDueOn(), false, null, paid.version());
        assertThat(paused.monthlyEquivalent()).isEqualTo(41_667);
        assertThatThrownBy(() -> finance.paySubscription(subscription.id(),
                LocalDate.of(2025, 2, 15), paused.nextDueOn(), paused.version(),
                LocalDate.of(2026, 7, 10), TransactionSource.MANUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void subscriptionBillingAnchorsSurviveShortMonthsAndLeapYears() {
        SubscriptionSummary monthly = finance.createSubscription("Month end probe", 100_000,
                "Hóa đơn", SubscriptionCycle.MONTHLY, LocalDate.of(2024, 1, 31), true, null);
        monthly = pay(monthly, LocalDate.of(2024, 1, 31));
        assertThat(monthly.nextDueOn()).isEqualTo(LocalDate.of(2024, 2, 29));
        monthly = finance.updateSubscription(monthly.id(), "Renamed month end probe",
                monthly.amount(), monthly.category(), monthly.cycle(), monthly.nextDueOn(),
                monthly.active(), null, monthly.version());
        monthly = pay(monthly, LocalDate.of(2024, 2, 29));
        assertThat(monthly.nextDueOn()).isEqualTo(LocalDate.of(2024, 3, 31));
        monthly = pay(monthly, LocalDate.of(2024, 3, 31));
        assertThat(monthly.nextDueOn()).isEqualTo(LocalDate.of(2024, 4, 30));

        SubscriptionSummary yearly = finance.createSubscription("Leap year probe", 1_200_000,
                "Hóa đơn", SubscriptionCycle.YEARLY, LocalDate.of(2024, 2, 29), true, null);
        yearly = pay(yearly, LocalDate.of(2024, 2, 29));
        assertThat(yearly.nextDueOn()).isEqualTo(LocalDate.of(2025, 2, 28));
        yearly = pay(yearly, LocalDate.of(2024, 2, 29));
        yearly = pay(yearly, LocalDate.of(2024, 2, 29));
        yearly = pay(yearly, LocalDate.of(2024, 2, 29));
        assertThat(yearly.nextDueOn()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void subscriptionPaymentRejectsFutureDatesBeforeWritingExpense() throws Exception {
        LocalDate due = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        var subscription = finance.createSubscription("Future payment probe", 75_000,
                "Hóa đơn", SubscriptionCycle.MONTHLY, due, true, null);

        mvc.perform(post("/api/finance/subscriptions/{id}/payments", subscription.id())
                        .header("X-Timezone", "Asia/Bangkok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "occurredOn": "%s",
                                  "expectedDueOn": "%s",
                                  "version": %d
                                }
                                """.formatted(due.plusDays(1), due, subscription.version())))
                .andExpect(status().isBadRequest());

        assertThat(finance.search("Future payment probe")).isEmpty();
        assertThat(subscription(subscription.id()).nextDueOn()).isEqualTo(due);
    }

    private SubscriptionSummary pay(SubscriptionSummary subscription, LocalDate occurredOn) {
        return finance.paySubscription(subscription.id(), occurredOn, subscription.nextDueOn(),
                subscription.version(), LocalDate.of(2026, 7, 10),
                TransactionSource.MANUAL).subscription();
    }

    private SubscriptionSummary subscription(UUID id) {
        return finance.subscriptions().stream()
                .filter(subscription -> subscription.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static NewTransaction item(TransactionType type, long amount, LocalDate occurredOn,
            String description, String category, boolean exceptional) {
        return new NewTransaction(type, amount, occurredOn, description, category, exceptional);
    }

    private static BalanceBreakdown balance(long bank, long cash) {
        return new BalanceBreakdown(bank, cash, 0, 0);
    }
}
