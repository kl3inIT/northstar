package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.BudgetSummary;
import com.northstar.core.finance.MonthSummary;
import com.northstar.core.finance.NewTransaction;
import com.northstar.core.finance.SavingsGoalSummary;
import com.northstar.core.finance.SubscriptionCycle;
import com.northstar.core.finance.SubscriptionPaymentSummary;
import com.northstar.core.finance.SubscriptionSummary;
import com.northstar.core.finance.TransactionSource;
import com.northstar.core.finance.TransactionSummary;
import com.northstar.core.finance.TransactionType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Money-ledger tools — thin adapters over the finance module's public API. */
@Component
class FinanceTools implements NorthstarTool {

    private static final String LOG_TRANSACTIONS = """
            Record money the user spent or received into the finance ledger ("ăn sáng \
            35k", "nhận lương 18tr5"). Accepts a LIST: when one message names several \
            amounts ("nay tiêu: sáng 30k, cafe 45k, grab 62k"), EVERY amount becomes \
            its own item — never merge two, never drop one. Expand Vietnamese \
            shorthand to VND integers (35k=35000, 500k=500000, 2tr=2000000, \
            2tr5=2500000); never invent an amount the user did not state. Resolve \
            relative dates against today to yyyy-MM-dd; pass "" when no date was \
            mentioned (meaning today). Category comes from the ledger vocabulary — \
            expense: Ăn uống, Cafe, Đi lại, Hóa đơn, Nhà cửa, Mua sắm, Sức khỏe, \
            Giải trí, Học tập, Du lịch, Hiếu hỉ, Gia đình, Khác; income: Lương, \
            Thưởng, Quà tặng, Khác — plus any category already visible in \
            spending_summary output; use "Khác" when unsure, never invent near-\
            duplicates. exceptional=true ONLY for one-off atypical purchases (hiếu \
            hỉ, gadgets, medical, flights/hotels, yearly fees); routine spending \
            (meals, coffee, commuting, bills) is false. After the call, echo each \
            saved item (amount · category · date) back to the user in one line each.""";

    private static final String SPENDING_SUMMARY = """
            One month of the ledger, aggregated: expense/income/net totals, the \
            one-off (exceptional) aggregate the review separates out, expense totals \
            by category largest-first (with a one-off mark), and the previous month's \
            expense total for comparison. Amounts are VND. Use for "tháng này tiêu \
            bao nhiêu", the spending section of a weekly review, and to see which \
            categories the ledger already uses.""";

    private static final String FIND_TRANSACTIONS = """
            Search ledger entries by description, case-insensitive, newest first, max \
            20 ("cái grab lúc nãy", "tiền nhà"). Use before fixing or deleting an \
            entry — results carry the ids delete_transaction needs.""";

    private static final String DELETE_TRANSACTION = """
            Permanently delete one ledger entry by its UUID (from find_transactions \
            or a log_transactions result). No undo — only call on explicit user \
            intent, and name the entry you deleted in your reply.""";

    private static final String LIST_BUDGETS = """
            List category budgets for one month with actual spent, remaining, and
            progress derived from the ledger. Limits carry forward per category:
            a category the user has not re-set this month shows the latest earlier
            month's limit with inherited=true, measured against this month's
            spend — mention when a limit is carried over. To change an inherited
            limit, call set_budget for THIS month; never reuse an inherited
            entry's id, it belongs to the source month. Budgets are reference
            lines, not envelopes: unspent money does NOT roll over anywhere —
            only the limit carries forward, and spent restarts from the new
            month's ledger. If the user asks what to do with leftover money,
            suggest contribute_savings_goal instead of inventing rollover
            mechanics.""";

    private static final String SET_BUDGET = """
            Create or replace one monthly category budget. Month is yyyy-MM, amount
            is a positive VND integer, and category should reuse the ledger vocabulary.
            Only write when the user explicitly asks to set or change a budget.
            When advising, favor a few budgets on variable, controllable categories
            (food, cafe, transport, fun); fixed costs like rent rarely need one and
            one-off spending is already tracked by the exceptional flag.""";

    private static final String DELETE_BUDGET = """
            Delete one monthly budget by UUID after explicit user intent. Ids come
            from list_budgets. NEVER call this with an inherited (carried-over)
            entry's id — that id belongs to the source month and deleting it would
            erase that month's budget; an inherited limit the user wants gone is
            removed by deleting it in its source month or overriding it with
            set_budget for the requested month.""";

    private static final String LIST_GOALS = """
            List savings goals with current/target amounts, remaining, progress,
            target date, and expected monthly contribution. The ids and versions
            returned here are required by save_savings_goal,
            contribute_savings_goal, and delete_savings_goal.""";

    private static final String CONTRIBUTE_GOAL = """
            Add a positive VND contribution to a savings goal on explicit user
            intent ("bỏ 500k vào quỹ MacBook"). Increments the goal's saved amount
            only — it does NOT write an expense to the ledger. This is also the
            right destination for leftover budget money at month end. Goal ids come
            from list_savings_goals.""";

    private static final String SAVE_GOAL = """
            Create or update a savings goal. Pass id="" to create; otherwise use an
            id and version from list_savings_goals. Before updating, always call
            list_savings_goals and preserve its savedAmount plus every field the user
            did not ask to change; a stale version is rejected instead of overwriting
            a contribution. Pass version=0 when creating. Amounts are VND, targetDate
            is yyyy-MM-dd or "", and monthlyContribution may be 0. Only write on
            explicit intent.""";

    private static final String LIST_SUBSCRIPTIONS = """
            List recurring subscription charges, including amount, monthly/yearly
            cycle, next due date, active state, version, normalized monthly cost,
            and the cancel-reminder date when one is set.""";

    private static final String SAVE_SUBSCRIPTION = """
            Create or update a recurring subscription. Pass id="" and version=0 to
            create. Before updating, call list_subscriptions and pass its current id,
            version, and every field the user did not ask to change. A stale version
            is rejected. cycle is MONTHLY or YEARLY; nextDueOn is yyyy-MM-dd. Charges
            post to the ledger AUTOMATICALLY when the due date arrives — never tell
            the user to mark payments themselves. When the user wants to be reminded
            to cancel or reconsider (a trial about to convert — "dùng thử 1 tháng",
            or "cuối năm hủy gói này"), set cancelReminderOn to that date: a reminder
            task is created automatically. For trials, nextDueOn should be the first
            PAID charge date.""";

    private static final String PAY_SUBSCRIPTION = """
            Manually confirm one subscription charge, create its expense, and advance
            the next due date. Rarely needed — due charges post automatically; use
            this only for an off-schedule payment the user explicitly reports.
            Immediately before paying, call list_subscriptions and pass its current
            nextDueOn as expectedDueOn plus its version. A stale call or retry is
            rejected and cannot create a duplicate expense. occurredOn must not be
            in the future.""";

    /**
     * One entry of a log_transactions call. Strings where the model writes text
     * ({@code type} EXPENSE|INCOME, {@code occurredOn} yyyy-MM-dd or "" = today);
     * {@code amount} is the already-expanded VND integer.
     */
    record LedgerItem(String type, long amount, String description, String category,
            String occurredOn, Boolean exceptional) {
    }

    private final FinanceService finance;

    FinanceTools(FinanceService finance) {
        this.finance = finance;
    }

    @Tool(name = "log_transactions", description = LOG_TRANSACTIONS)
    @McpTool(name = "log_transactions", description = LOG_TRANSACTIONS,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    List<TransactionSummary> logTransactions(
            @ToolParam(description = "The entries to record — one per amount in the user's message")
            @McpToolParam(description = "The entries to record — one per amount in the user's message",
                    required = true) List<LedgerItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one entry");
        }
        List<NewTransaction> resolved = items.stream().map(FinanceTools::toNewTransaction).toList();
        return finance.recordAll(resolved, TransactionSource.ASSISTANT);
    }

    @Tool(name = "spending_summary", description = SPENDING_SUMMARY)
    @McpTool(name = "spending_summary", description = SPENDING_SUMMARY,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    MonthSummary spendingSummary(
            @ToolParam(description = "Month as yyyy-MM; omit for the current month", required = false)
            @McpToolParam(description = "Month as yyyy-MM; omit for the current month",
                    required = false) String month) {
        return finance.monthSummary(parseMonth(month));
    }

    @Tool(name = "list_budgets", description = LIST_BUDGETS)
    @McpTool(name = "list_budgets", description = LIST_BUDGETS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<BudgetSummary> listBudgets(
            @ToolParam(description = "Month as yyyy-MM; omit for current month", required = false)
            @McpToolParam(description = "Month as yyyy-MM; omit for current month",
                    required = false) String month) {
        return finance.budgets(parseMonth(month));
    }

    @Tool(name = "set_budget", description = SET_BUDGET)
    @McpTool(name = "set_budget", description = SET_BUDGET,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    BudgetSummary setBudget(
            @ToolParam(description = "Month as yyyy-MM")
            @McpToolParam(description = "Month as yyyy-MM", required = true) String month,
            @ToolParam(description = "Existing expense category")
            @McpToolParam(description = "Existing expense category", required = true) String category,
            @ToolParam(description = "Positive monthly limit in VND")
            @McpToolParam(description = "Positive monthly limit in VND", required = true) long limitAmount) {
        return finance.setBudget(parseMonth(month), category, limitAmount);
    }

    @Tool(name = "delete_budget", description = DELETE_BUDGET)
    @McpTool(name = "delete_budget", description = DELETE_BUDGET,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteBudget(
            @ToolParam(description = "Budget UUID")
            @McpToolParam(description = "Budget UUID", required = true) String budgetId) {
        finance.deleteBudget(UUID.fromString(budgetId));
        return "Deleted budget " + budgetId;
    }

    @Tool(name = "list_savings_goals", description = LIST_GOALS)
    @McpTool(name = "list_savings_goals", description = LIST_GOALS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<SavingsGoalSummary> listSavingsGoals() {
        return finance.savingsGoals();
    }

    @Tool(name = "save_savings_goal", description = SAVE_GOAL)
    @McpTool(name = "save_savings_goal", description = SAVE_GOAL,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    SavingsGoalSummary saveSavingsGoal(
            @ToolParam(description = "Existing goal UUID, or empty string to create", required = false)
            @McpToolParam(description = "Existing goal UUID, or empty string to create",
                    required = false) String id,
            @ToolParam(description = "Current version from list_savings_goals; 0 only when creating")
            @McpToolParam(description = "Current version from list_savings_goals; 0 only when creating",
                    required = true) long version,
            @ToolParam(description = "Goal name")
            @McpToolParam(description = "Goal name", required = true) String name,
            @ToolParam(description = "Positive target in VND")
            @McpToolParam(description = "Positive target in VND", required = true) long targetAmount,
            @ToolParam(description = "Current saved amount in VND, zero or more")
            @McpToolParam(description = "Current saved amount in VND, zero or more",
                    required = true) long savedAmount,
            @ToolParam(description = "Target date yyyy-MM-dd, or empty string", required = false)
            @McpToolParam(description = "Target date yyyy-MM-dd, or empty string",
                    required = false) String targetDate,
            @ToolParam(description = "Expected monthly contribution in VND, zero or more")
            @McpToolParam(description = "Expected monthly contribution in VND, zero or more",
                    required = true) long monthlyContribution) {
        LocalDate date = parseDate("targetDate", targetDate);
        if (id == null || id.isBlank()) {
            return finance.createSavingsGoal(name, targetAmount, savedAmount, date, monthlyContribution);
        }
        return finance.updateSavingsGoal(UUID.fromString(id), name, targetAmount,
                savedAmount, date, monthlyContribution, version);
    }

    @Tool(name = "contribute_savings_goal", description = CONTRIBUTE_GOAL)
    @McpTool(name = "contribute_savings_goal", description = CONTRIBUTE_GOAL,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    SavingsGoalSummary contributeSavingsGoal(
            @ToolParam(description = "Savings goal UUID")
            @McpToolParam(description = "Savings goal UUID", required = true) String goalId,
            @ToolParam(description = "Positive contribution in VND")
            @McpToolParam(description = "Positive contribution in VND", required = true) long amount) {
        return finance.contributeToSavingsGoal(UUID.fromString(goalId), amount);
    }

    @Tool(name = "delete_savings_goal",
            description = "Delete one savings goal by UUID (from list_savings_goals) after explicit user intent. No undo.")
    @McpTool(name = "delete_savings_goal",
            description = "Delete one savings goal by UUID (from list_savings_goals) after explicit user intent. No undo.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteSavingsGoal(
            @ToolParam(description = "Savings goal UUID")
            @McpToolParam(description = "Savings goal UUID", required = true) String goalId) {
        finance.deleteSavingsGoal(UUID.fromString(goalId));
        return "Deleted savings goal " + goalId;
    }

    @Tool(name = "list_subscriptions", description = LIST_SUBSCRIPTIONS)
    @McpTool(name = "list_subscriptions", description = LIST_SUBSCRIPTIONS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<SubscriptionSummary> listSubscriptions() {
        return finance.subscriptions();
    }

    @Tool(name = "save_subscription", description = SAVE_SUBSCRIPTION)
    @McpTool(name = "save_subscription", description = SAVE_SUBSCRIPTION,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    SubscriptionSummary saveSubscription(
            @ToolParam(description = "Existing subscription UUID, or empty string to create", required = false)
            @McpToolParam(description = "Existing subscription UUID, or empty string to create",
                    required = false) String id,
            @ToolParam(description = "Current version from list_subscriptions; 0 only when creating")
            @McpToolParam(description = "Current version from list_subscriptions; 0 only when creating",
                    required = true) long version,
            @ToolParam(description = "Subscription name")
            @McpToolParam(description = "Subscription name", required = true) String name,
            @ToolParam(description = "Positive charge amount in VND")
            @McpToolParam(description = "Positive charge amount in VND", required = true) long amount,
            @ToolParam(description = "Existing expense category")
            @McpToolParam(description = "Existing expense category", required = true) String category,
            @ToolParam(description = "MONTHLY or YEARLY")
            @McpToolParam(description = "MONTHLY or YEARLY", required = true) String cycle,
            @ToolParam(description = "Next due date as yyyy-MM-dd")
            @McpToolParam(description = "Next due date as yyyy-MM-dd", required = true) String nextDueOn,
            @ToolParam(description = "Whether this subscription is active")
            @McpToolParam(description = "Whether this subscription is active",
                    required = true) boolean active,
            @ToolParam(description = "Date to remind the user to cancel/review this subscription, yyyy-MM-dd (trial conversion, planned cancellation); empty for no reminder. A reminder task is created automatically.", required = false)
            @McpToolParam(description = "Date to remind the user to cancel/review this subscription, yyyy-MM-dd (trial conversion, planned cancellation); empty for no reminder. A reminder task is created automatically.",
                    required = false) String cancelReminderOn) {
        SubscriptionCycle parsedCycle;
        try {
            parsedCycle = SubscriptionCycle.valueOf(cycle.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cycle must be MONTHLY or YEARLY, got '" + cycle + "'");
        }
        LocalDate due = parseDate("nextDueOn", nextDueOn);
        if (due == null) {
            throw new IllegalArgumentException("nextDueOn is required");
        }
        LocalDate remindOn = parseDate("cancelReminderOn", cancelReminderOn);
        if (id == null || id.isBlank()) {
            return finance.createSubscription(name, amount, category, parsedCycle, due, active,
                    remindOn);
        }
        return finance.updateSubscription(UUID.fromString(id), name, amount, category,
                parsedCycle, due, active, remindOn, version);
    }

    @Tool(name = "mark_subscription_paid", description = PAY_SUBSCRIPTION)
    @McpTool(name = "mark_subscription_paid", description = PAY_SUBSCRIPTION,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    SubscriptionPaymentSummary markSubscriptionPaid(
            @ToolParam(description = "Subscription UUID")
            @McpToolParam(description = "Subscription UUID", required = true) String subscriptionId,
            @ToolParam(description = "Current next due date from list_subscriptions as yyyy-MM-dd")
            @McpToolParam(description = "Current next due date from list_subscriptions as yyyy-MM-dd",
                    required = true) String expectedDueOn,
            @ToolParam(description = "Current version from list_subscriptions")
            @McpToolParam(description = "Current version from list_subscriptions",
                    required = true) long version,
            @ToolParam(description = "Payment date yyyy-MM-dd, or empty string for today", required = false)
            @McpToolParam(description = "Payment date yyyy-MM-dd, or empty string for today",
                    required = false) String occurredOn) {
        LocalDate date = parseDate("occurredOn", occurredOn);
        LocalDate due = parseDate("expectedDueOn", expectedDueOn);
        if (due == null) {
            throw new IllegalArgumentException("expectedDueOn is required");
        }
        return finance.paySubscription(UUID.fromString(subscriptionId),
                date == null ? LocalDate.now(zone()) : date, due, version,
                LocalDate.now(zone()), TransactionSource.ASSISTANT);
    }

    @Tool(name = "delete_subscription",
            description = "Delete one subscription definition by UUID (from list_subscriptions) after explicit user intent. Already-posted charges stay in the ledger; to stop future charges without losing history, prefer save_subscription with active=false.")
    @McpTool(name = "delete_subscription",
            description = "Delete one subscription definition by UUID (from list_subscriptions) after explicit user intent. Already-posted charges stay in the ledger; to stop future charges without losing history, prefer save_subscription with active=false.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteSubscription(
            @ToolParam(description = "Subscription UUID")
            @McpToolParam(description = "Subscription UUID", required = true) String subscriptionId) {
        finance.deleteSubscription(UUID.fromString(subscriptionId));
        return "Deleted subscription " + subscriptionId;
    }

    @Tool(name = "find_transactions", description = FIND_TRANSACTIONS)
    @McpTool(name = "find_transactions", description = FIND_TRANSACTIONS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<TransactionSummary> findTransactions(
            @ToolParam(description = "Part of the entry's description, e.g. 'grab'")
            @McpToolParam(description = "Part of the entry's description, e.g. 'grab'",
                    required = true) String query) {
        return finance.search(query);
    }

    @Tool(name = "delete_transaction", description = DELETE_TRANSACTION)
    @McpTool(name = "delete_transaction", description = DELETE_TRANSACTION,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteTransaction(
            @ToolParam(description = "The entry's UUID")
            @McpToolParam(description = "The entry's UUID", required = true) String transactionId) {
        UUID id = UUID.fromString(transactionId);
        TransactionSummary victim = finance.find(id);
        finance.delete(id);
        return "Deleted \"" + victim.description() + "\" (" + victim.amount() + " VND, "
                + victim.occurredOn() + ")";
    }

    private static NewTransaction toNewTransaction(LedgerItem item) {
        TransactionType type = item.type() == null || item.type().isBlank()
                ? TransactionType.EXPENSE
                : switch (item.type().strip().toUpperCase(Locale.ROOT)) {
                    case "EXPENSE" -> TransactionType.EXPENSE;
                    case "INCOME" -> TransactionType.INCOME;
                    default -> throw new IllegalArgumentException(
                            "type must be EXPENSE or INCOME, got '" + item.type() + "'");
                };
        LocalDate occurredOn = parseDate("occurredOn", item.occurredOn());
        return new NewTransaction(type, item.amount(),
                occurredOn == null ? LocalDate.now(zone()) : occurredOn,
                item.description(), item.category(),
                Boolean.TRUE.equals(item.exceptional()));
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(zone());
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month must be yyyy-MM, got '" + month + "'");
        }
    }
}
