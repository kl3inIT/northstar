package com.northstar.api.finance;

import com.northstar.core.finance.BalanceCheckInSummary;
import com.northstar.core.finance.FinanceInsights;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.BudgetSummary;
import com.northstar.core.finance.MonthSummary;
import com.northstar.core.finance.NewTransaction;
import com.northstar.core.finance.RecurringSuggestion;
import com.northstar.core.finance.SavingsGoalSummary;
import com.northstar.core.finance.SubscriptionPaymentSummary;
import com.northstar.core.finance.SubscriptionSummary;
import com.northstar.core.finance.TransactionSource;
import com.northstar.core.finance.TransactionSummary;
import com.northstar.core.finance.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for the finance ledger. Reads are month-scoped ("which month am
 * I looking at" is the page's only navigation); "current month" depends on the
 * browser zone, so the default comes from the {@code X-Timezone} header like the
 * task endpoints. Writes are the confirmed-capture batch (CAPTURE source) and
 * per-row corrections; free-text parsing never reaches this controller.
 */
@RestController
@RequestMapping("/api/finance")
class FinanceController {

    private final FinanceService finance;

    FinanceController(FinanceService finance) {
        this.finance = finance;
    }

    @GetMapping
    @Operation(operationId = "listTransactions")
    List<TransactionSummary> list(
            @RequestParam(name = "month", required = false) String month,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.month(parseMonth(month, tz));
    }

    @GetMapping("/summary")
    @Operation(operationId = "getMonthSummary")
    MonthSummary summary(
            @RequestParam(name = "month", required = false) String month,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.monthSummary(parseMonth(month, tz));
    }

    /** The constrained category vocabulary (seed ∪ used) — the row editor's select options. */
    @GetMapping("/categories")
    @Operation(operationId = "listCategories")
    List<String> categories(@RequestParam(name = "type") TransactionType type) {
        return finance.categories(type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "recordTransactions")
    List<TransactionSummary> record(@Valid @RequestBody FinanceRequest.RecordTransactionsRequest request) {
        List<NewTransaction> items = request.items().stream()
                .map(i -> new NewTransaction(i.type(), i.amount(), i.occurredOn(),
                        i.description(), i.category(), i.exceptional()))
                .toList();
        return finance.recordAll(items, TransactionSource.CAPTURE);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTransaction")
    TransactionSummary update(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.UpdateTransactionRequest request) {
        return finance.update(id, request.amount(), request.occurredOn(),
                request.description(), request.category(), request.exceptional());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTransaction")
    void delete(@PathVariable("id") UUID id) {
        finance.delete(id);
    }

    @GetMapping("/balance-check-ins")
    @Operation(operationId = "listBalanceCheckIns")
    List<BalanceCheckInSummary> balanceCheckIns() {
        return finance.balanceCheckIns();
    }

    @PostMapping("/balance-check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBalanceCheckIn")
    BalanceCheckInSummary createBalanceCheckIn(
            @Valid @RequestBody FinanceRequest.BalanceCheckInRequest request,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.checkInBalance(request.actualBalance(), request.checkedOn(),
                LocalDate.now(zone(tz)));
    }

    @GetMapping("/insights")
    @Operation(operationId = "getFinanceInsights")
    FinanceInsights insights(
            @RequestParam(name = "through", required = false) String through,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.insights(parseDate(through, tz));
    }

    @GetMapping("/recurring-suggestions")
    @Operation(operationId = "listRecurringSuggestions")
    List<RecurringSuggestion> recurringSuggestions(
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.recurringSuggestions(LocalDate.now(zone(tz)));
    }

    @GetMapping("/budgets")
    @Operation(operationId = "listBudgets")
    List<BudgetSummary> budgets(
            @RequestParam(name = "month", required = false) String month,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.budgets(parseMonth(month, tz));
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBudget")
    BudgetSummary createBudget(@Valid @RequestBody FinanceRequest.BudgetRequest request) {
        return finance.createBudget(parseMonth(request.month(), null), request.category(),
                request.limitAmount());
    }

    @PutMapping("/budgets/{id}")
    @Operation(operationId = "updateBudget")
    BudgetSummary updateBudget(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.BudgetRequest request) {
        return finance.updateBudget(id, parseMonth(request.month(), null),
                request.category(), request.limitAmount());
    }

    @DeleteMapping("/budgets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteBudget")
    void deleteBudget(@PathVariable("id") UUID id) {
        finance.deleteBudget(id);
    }

    @GetMapping("/savings-goals")
    @Operation(operationId = "listSavingsGoals")
    List<SavingsGoalSummary> savingsGoals() {
        return finance.savingsGoals();
    }

    @PostMapping("/savings-goals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createSavingsGoal")
    SavingsGoalSummary createSavingsGoal(
            @Valid @RequestBody FinanceRequest.SavingsGoalRequest request) {
        return finance.createSavingsGoal(request.name(), request.targetAmount(),
                request.savedAmount(), request.targetDate(), request.monthlyContribution());
    }

    @PutMapping("/savings-goals/{id}")
    @Operation(operationId = "updateSavingsGoal")
    SavingsGoalSummary updateSavingsGoal(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.UpdateSavingsGoalRequest request) {
        return finance.updateSavingsGoal(id, request.name(), request.targetAmount(),
                request.savedAmount(), request.targetDate(), request.monthlyContribution(),
                request.version());
    }

    @PostMapping("/savings-goals/{id}/contributions")
    @Operation(operationId = "contributeSavingsGoal")
    SavingsGoalSummary contributeSavingsGoal(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.ContributionRequest request) {
        return finance.contributeSavingsGoal(id, request.amount());
    }

    @DeleteMapping("/savings-goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSavingsGoal")
    void deleteSavingsGoal(@PathVariable("id") UUID id) {
        finance.deleteSavingsGoal(id);
    }

    @GetMapping("/subscriptions")
    @Operation(operationId = "listSubscriptions")
    List<SubscriptionSummary> subscriptions() {
        return finance.subscriptions();
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createSubscription")
    SubscriptionSummary createSubscription(
            @Valid @RequestBody FinanceRequest.SubscriptionRequest request) {
        return finance.createSubscription(request.name(), request.amount(), request.category(),
                request.cycle(), request.nextDueOn(), request.active(),
                request.cancelReminderOn());
    }

    @PutMapping("/subscriptions/{id}")
    @Operation(operationId = "updateSubscription")
    SubscriptionSummary updateSubscription(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.UpdateSubscriptionRequest request) {
        return finance.updateSubscription(id, request.name(), request.amount(), request.category(),
                request.cycle(), request.nextDueOn(), request.active(),
                request.cancelReminderOn(), request.version());
    }

    @PostMapping("/subscriptions/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "paySubscription")
    SubscriptionPaymentSummary paySubscription(@PathVariable("id") UUID id,
            @Valid @RequestBody FinanceRequest.SubscriptionPaymentRequest request,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return finance.paySubscription(id, request.occurredOn(), request.expectedDueOn(),
                request.version(), LocalDate.now(zone(tz)), TransactionSource.MANUAL);
    }

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSubscription")
    void deleteSubscription(@PathVariable("id") UUID id) {
        finance.deleteSubscription(id);
    }

    private static YearMonth parseMonth(String month, String tz) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(zone(tz));
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("month must be yyyy-MM, got '" + month + "'");
        }
    }

    private static LocalDate parseDate(String date, String tz) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(zone(tz));
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("through must be yyyy-MM-dd, got '" + date + "'");
        }
    }

    private static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz.strip());
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }
}
