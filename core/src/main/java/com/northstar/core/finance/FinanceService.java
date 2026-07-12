package com.northstar.core.finance;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The finance module's public API. Writes take already-resolved values (VND
 * longs, absolute dates) — natural-language parsing lives in capture and the
 * assistant. Reads are month-scoped. {@link #categories} is the constrained
 * vocabulary fed into every extraction prompt: the seeded VN consensus taxonomy
 * unioned with whatever the ledger already uses, so the LLM reuses labels
 * instead of inventing near-duplicates ("cafe" vs "coffee") that would break
 * month-over-month comparison.
 */
@Service
public class FinanceService {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /**
     * Consensus expense taxonomy across Money Lover/MISA/1Money/Mint/Monarch,
     * VN-tuned: Cafe and Hiếu hỉ are deliberately top-level. "Khác" is the
     * mandated sink when nothing fits — every shipped app hard-codes one.
     */
    static final List<String> EXPENSE_SEED = List.of(
            "Ăn uống", "Cafe", "Đi lại", "Hóa đơn", "Nhà cửa", "Mua sắm", "Sức khỏe",
            "Giải trí", "Học tập", "Du lịch", "Hiếu hỉ", "Gia đình", "Khác");

    static final List<String> INCOME_SEED = List.of("Lương", "Thưởng", "Quà tặng", "Khác");

    private final TransactionRepository transactions;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository goalRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BalanceCheckInRepository balanceCheckInRepository;
    private final CategoryCorrectionRepository categoryCorrectionRepository;

    FinanceService(TransactionRepository transactions, BudgetRepository budgetRepository,
            SavingsGoalRepository goalRepository, SubscriptionRepository subscriptionRepository,
            BalanceCheckInRepository balanceCheckInRepository,
            CategoryCorrectionRepository categoryCorrectionRepository) {
        this.transactions = transactions;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.balanceCheckInRepository = balanceCheckInRepository;
        this.categoryCorrectionRepository = categoryCorrectionRepository;
    }

    /** Record a batch in one transaction — one multi-item capture is one confirm. */
    @Transactional
    public List<TransactionSummary> recordAll(List<NewTransaction> items, TransactionSource source) {
        List<TransactionSummary> saved = new ArrayList<>();
        for (NewTransaction item : items) {
            saved.add(record(item, source));
        }
        return saved;
    }

    @Transactional
    public TransactionSummary record(NewTransaction item, TransactionSource source) {
        TransactionType type = requireType(item.type());
        Transaction transaction = new Transaction(UUID.randomUUID(), type,
                requirePositive(item.amount()), requireDate(item.occurredOn()),
                requireDescription(item.description()), canonicalCategory(item.category(), type),
                item.exceptional(), Objects.requireNonNull(source, "source is required"));
        transactions.save(transaction);
        return summary(transaction);
    }

    /** Full edit of the user-facing fields; type and source never change. */
    @Transactional
    public TransactionSummary update(UUID id, long amount, LocalDate occurredOn,
            String description, String category, boolean exceptional) {
        Transaction transaction = transactions.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        rejectReconciliationMutation(transaction);
        String validDescription = requireDescription(description);
        String validCategory = canonicalCategory(category, transaction.getType());
        if (!categoryKey(transaction.getCategory()).equals(categoryKey(validCategory))) {
            recordCategoryCorrection(transaction.getType(), validDescription, validCategory);
        }
        transaction.edit(requirePositive(amount), requireDate(occurredOn), validDescription,
                validCategory, exceptional);
        return summary(transaction);
    }

    @Transactional
    public void delete(UUID id) {
        Transaction transaction = transactions.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        rejectReconciliationMutation(transaction);
        transactions.delete(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionSummary find(UUID id) {
        return summary(transactions.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id)));
    }

    /** One month's ledger, newest first. */
    @Transactional(readOnly = true)
    public List<TransactionSummary> month(YearMonth month) {
        return transactions
                .findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                        month.atDay(1), month.atEndOfMonth())
                .stream().map(this::summary).toList();
    }

    /** Ledger entries in an arbitrary date window, newest first — the weekly review's week. */
    @Transactional(readOnly = true)
    public List<TransactionSummary> range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        return transactions.findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(from, to)
                .stream().map(this::summary).toList();
    }

    /**
     * Median expense of the 4 full weeks before {@code weekStart} — the review's
     * comparison standard. A descriptive norm inferred from the ledger, not a
     * user-maintained budget: feedback without any reference point demonstrably
     * changes nothing, budgets demonstrably don't get maintained.
     */
    @Transactional(readOnly = true)
    public long typicalWeekExpense(LocalDate weekStart) {
        long[] sums = new long[4];
        for (int i = 1; i <= 4; i++) {
            LocalDate start = weekStart.minusWeeks(i);
            sums[i - 1] = transactions.sumAmount(TransactionType.EXPENSE, start, start.plusDays(6));
        }
        Arrays.sort(sums);
        return (sums[1] + sums[2]) / 2;
    }

    /** Header aggregates for one month, including the previous-month expense reference. */
    @Transactional(readOnly = true)
    public MonthSummary monthSummary(YearMonth month) {
        YearMonth previous = month.minusMonths(1);
        long previousExpense = transactions.sumAmount(TransactionType.EXPENSE,
                previous.atDay(1), previous.atEndOfMonth());
        return MonthSummary.of(month, month(month), previousExpense);
    }

    /** Recent-first description search — the assistant's find/fix channel. */
    @Transactional(readOnly = true)
    public List<TransactionSummary> search(String query) {
        return transactions
                .findTop20ByDescriptionContainingIgnoreCaseOrderByOccurredOnDesc(query.strip())
                .stream().map(this::summary).toList();
    }

    /** The constrained category vocabulary for one type: seed ∪ already-used. */
    @Transactional(readOnly = true)
    public List<String> categories(TransactionType type) {
        List<String> seed = type == TransactionType.INCOME ? INCOME_SEED : EXPENSE_SEED;
        return mergeVocabulary(seed, transactions.distinctCategories(type));
    }

    /** Recent user-confirmed mappings, newest first, for Capture prompt few-shots. */
    @Transactional(readOnly = true)
    public List<CategoryCorrectionSummary> categoryCorrections() {
        return categoryCorrectionRepository.findTop12ByOrderByUpdatedAtDescCreatedAtDesc()
                .stream()
                .map(correction -> new CategoryCorrectionSummary(correction.getType(),
                        correction.getDescription(), correction.getCategory()))
                .toList();
    }

    /** Most recent aggregate balance anchors, newest first. */
    @Transactional(readOnly = true)
    public List<BalanceCheckInSummary> balanceCheckIns() {
        return balanceCheckInRepository.findTop12ByOrderByCheckedOnDescCreatedAtDesc()
                .stream().map(this::balanceCheckInSummary).toList();
    }

    /**
     * Re-anchor the ledger to an aggregate end-of-day balance. The first value
     * establishes the baseline; later differences become immutable ledger rows.
     */
    @Transactional
    public BalanceCheckInSummary checkInBalance(BalanceBreakdown breakdown, LocalDate checkedOn,
            LocalDate today) {
        BalanceBreakdown validBreakdown = requireBalanceBreakdown(breakdown);
        long actual = validBreakdown.totalBalance();
        LocalDate date = requireDate(checkedOn, "checkedOn");
        if (date.isAfter(requireDate(today, "today"))) {
            throw new IllegalArgumentException("checkedOn must not be in the future");
        }

        BalanceCheckIn previous = balanceCheckInRepository
                .findTopByOrderByCheckedOnDescCreatedAtDesc().orElse(null);
        long expected = actual;
        long discrepancy = 0;
        TransactionSummary adjustment = null;
        if (previous != null) {
            if (!date.isAfter(previous.getCheckedOn())) {
                throw new IllegalArgumentException(
                        "checkedOn must be after the latest balance check-in");
            }
            List<TransactionSummary> flow = range(previous.getCheckedOn().plusDays(1), date);
            long income = flow.stream().filter(row -> row.type() == TransactionType.INCOME)
                    .mapToLong(TransactionSummary::amount).sum();
            long expense = flow.stream().filter(row -> row.type() == TransactionType.EXPENSE)
                    .mapToLong(TransactionSummary::amount).sum();
            try {
                expected = Math.subtractExact(Math.addExact(previous.getTotalBalance(), income),
                        expense);
                discrepancy = Math.subtractExact(actual, expected);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("balance check-in exceeds the supported VND range", e);
            }
            if (discrepancy != 0) {
                TransactionType adjustmentType = discrepancy < 0
                        ? TransactionType.EXPENSE : TransactionType.INCOME;
                String description = discrepancy < 0
                        ? "Chi chưa ghi lại (đối soát)" : "Thu chưa ghi lại (đối soát)";
                adjustment = record(new NewTransaction(adjustmentType, Math.abs(discrepancy), date,
                        description, "Khác", false), TransactionSource.RECONCILIATION);
            }
        }

        BalanceCheckIn checkIn = new BalanceCheckIn(UUID.randomUUID(), date, validBreakdown,
                expected, discrepancy, adjustment == null ? null : adjustment.id());
        balanceCheckInRepository.saveAndFlush(checkIn);
        return balanceCheckInSummary(checkIn, adjustment);
    }

    /** Remove only the latest anchor and its generated adjustment, then expose its predecessor. */
    @Transactional
    public void undoBalanceCheckIn(UUID id) {
        BalanceCheckIn latest = balanceCheckInRepository
                .findTopByOrderByCheckedOnDescCreatedAtDesc()
                .orElseThrow(() -> new IllegalArgumentException("balance check-in not found"));
        if (!latest.getId().equals(Objects.requireNonNull(id, "id is required"))) {
            throw new IllegalArgumentException("only the latest balance check-in can be undone");
        }
        UUID adjustmentId = latest.getAdjustmentTransactionId();
        balanceCheckInRepository.delete(latest);
        balanceCheckInRepository.flush();
        if (adjustmentId != null) {
            transactions.deleteById(adjustmentId);
        }
    }

    /** Twelve monthly points plus current-month categories and 365 daily expense buckets. */
    @Transactional(readOnly = true)
    public FinanceInsights insights(LocalDate through) {
        LocalDate end = requireDate(through, "through");
        YearMonth endMonth = YearMonth.from(end);
        YearMonth startMonth = endMonth.minusMonths(11);
        LocalDate dailyStart = end.minusDays(364);
        LocalDate queryStart = startMonth.atDay(1).isBefore(dailyStart)
                ? startMonth.atDay(1) : dailyStart;
        List<TransactionSummary> rows = range(queryStart, end);

        Map<YearMonth, List<TransactionSummary>> byMonth = rows.stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.occurredOn())));
        List<FinanceInsights.MonthlyPoint> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth month = startMonth.plusMonths(i);
            MonthSummary summary = MonthSummary.of(month,
                    byMonth.getOrDefault(month, List.of()), 0);
            months.add(new FinanceInsights.MonthlyPoint(month.toString(), summary.expenseTotal(),
                    summary.incomeTotal(), summary.net(), summary.exceptionalTotal()));
        }

        Map<LocalDate, Long> daily = rows.stream()
                .filter(row -> row.type() == TransactionType.EXPENSE
                        && !row.occurredOn().isBefore(dailyStart))
                .collect(Collectors.groupingBy(TransactionSummary::occurredOn,
                        Collectors.summingLong(TransactionSummary::amount)));
        List<FinanceInsights.DailyPoint> days = new ArrayList<>();
        for (int i = 0; i < 365; i++) {
            LocalDate date = dailyStart.plusDays(i);
            days.add(new FinanceInsights.DailyPoint(date, daily.getOrDefault(date, 0L)));
        }
        List<MonthSummary.CategoryTotal> categories = MonthSummary.of(endMonth,
                byMonth.getOrDefault(endMonth, List.of()), 0).categories();
        return new FinanceInsights(end, List.copyOf(months), categories, List.copyOf(days));
    }

    /** Detect stable monthly/yearly expense patterns not already tracked as subscriptions. */
    @Transactional(readOnly = true)
    public List<RecurringSuggestion> recurringSuggestions(LocalDate today) {
        LocalDate date = requireDate(today, "today");
        Set<String> subscriptions = subscriptionRepository.findAll().stream()
                .map(item -> descriptionKey(item.getName()))
                .collect(Collectors.toSet());
        Map<String, List<TransactionSummary>> groups = range(date.minusYears(3), date).stream()
                .filter(row -> row.type() == TransactionType.EXPENSE && !row.exceptional())
                .filter(row -> row.source() != TransactionSource.SUBSCRIPTION
                        && row.source() != TransactionSource.RECONCILIATION)
                .collect(Collectors.groupingBy(row -> descriptionKey(row.description())));

        List<RecurringSuggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, List<TransactionSummary>> entry : groups.entrySet()) {
            String key = entry.getKey();
            if (key.isBlank() || subscriptions.stream().anyMatch(subscription ->
                    subscription.length() > 3
                            && (key.contains(subscription) || subscription.contains(key)))) {
                continue;
            }
            List<TransactionSummary> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(TransactionSummary::occurredOn)).toList();
            SubscriptionCycle cycle = detectCycle(ordered);
            if (cycle == null) {
                continue;
            }
            int sampleSize = cycle == SubscriptionCycle.MONTHLY ? Math.min(6, ordered.size())
                    : Math.min(4, ordered.size());
            List<TransactionSummary> sample = ordered.subList(ordered.size() - sampleSize,
                    ordered.size());
            long amount = medianAmount(sample);
            long tolerance = Math.max(10_000, amount / 10);
            if (sample.stream().anyMatch(row -> Math.abs(row.amount() - amount) > tolerance)) {
                continue;
            }
            TransactionSummary latest = sample.get(sample.size() - 1);
            LocalDate next = advance(latest.occurredOn(), cycle);
            while (!next.isAfter(date)) {
                next = advance(next, cycle);
            }
            suggestions.add(new RecurringSuggestion(key, latest.description(), amount,
                    latest.category(), cycle, next, ordered.size()));
        }
        suggestions.sort(Comparator.comparing(RecurringSuggestion::nextExpectedOn)
                .thenComparing(RecurringSuggestion::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(suggestions);
    }

    /**
     * Monthly category limits with actual spend derived from the ledger. Limits
     * carry forward per category: any category the latest earlier budgeted month
     * set that this month has not overridden appears with {@code inherited=true},
     * measured against THIS month's spend — a new month never opens empty just
     * because nobody re-typed the same numbers (budgets people must re-set
     * monthly are budgets people abandon). Setting one category for the month
     * materializes only that row; the others keep carrying. Only history before
     * the first budget ever set stays blank.
     */
    @Transactional(readOnly = true)
    public List<BudgetSummary> budgets(YearMonth month) {
        Map<String, Long> spent = new HashMap<>();
        for (TransactionSummary row : month(month)) {
            if (row.type() == TransactionType.EXPENSE) {
                spent.merge(row.category().toLowerCase(Locale.ROOT), row.amount(), Long::sum);
            }
        }
        List<Budget> own = budgetRepository.findByMonthStartOrderByCreatedAtAsc(month.atDay(1));
        Set<String> covered = own.stream()
                .map(budget -> budget.getCategory().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Budget> carried = budgetRepository
                .findTopByMonthStartLessThanOrderByMonthStartDesc(month.atDay(1))
                .map(latest -> budgetRepository
                        .findByMonthStartOrderByCreatedAtAsc(latest.getMonthStart()))
                .orElse(List.of())
                .stream()
                .filter(budget -> !covered.contains(budget.getCategory().toLowerCase(Locale.ROOT)))
                .toList();
        List<BudgetSummary> result = new ArrayList<>();
        for (Budget budget : own) {
            result.add(budgetSummary(budget, month,
                    spent.getOrDefault(budget.getCategory().toLowerCase(Locale.ROOT), 0L), false));
        }
        for (Budget budget : carried) {
            result.add(budgetSummary(budget, month,
                    spent.getOrDefault(budget.getCategory().toLowerCase(Locale.ROOT), 0L), true));
        }
        return List.copyOf(result);
    }

    /** Create the one budget for a month/category pair. */
    @Transactional
    public BudgetSummary createBudget(YearMonth month, String category, long limitAmount) {
        String normalized = canonicalCategory(category, TransactionType.EXPENSE);
        long validLimit = requirePositive(limitAmount);
        rejectDuplicateBudget(null, month, normalized);
        Budget budget = new Budget(UUID.randomUUID(), month.atDay(1), normalized, validLimit);
        budgetRepository.saveAndFlush(budget);
        return budgetSummary(budget, spentForCategory(month, normalized));
    }

    /** Assistant-facing upsert retained for the existing explicit {@code set_budget} tool. */
    @Transactional
    public BudgetSummary setBudget(YearMonth month, String category, long limitAmount) {
        String normalized = canonicalCategory(category, TransactionType.EXPENSE);
        long validLimit = requirePositive(limitAmount);
        Budget budget = budgetRepository
                .findByMonthStartAndCategoryIgnoreCase(month.atDay(1), normalized)
                .orElseGet(() -> new Budget(UUID.randomUUID(), month.atDay(1), normalized, validLimit));
        budget.edit(month.atDay(1), normalized, validLimit);
        budgetRepository.saveAndFlush(budget);
        return budgetSummary(budget, spentForCategory(month, normalized));
    }

    @Transactional
    public BudgetSummary updateBudget(UUID id, YearMonth month, String category, long limitAmount) {
        Budget budget = getBudget(id);
        String normalized = canonicalCategory(category, TransactionType.EXPENSE);
        rejectDuplicateBudget(id, month, normalized);
        budget.edit(month.atDay(1), normalized, requirePositive(limitAmount));
        return budgetSummary(budget, spentForCategory(month, normalized));
    }

    @Transactional
    public void deleteBudget(UUID id) {
        budgetRepository.delete(getBudget(id));
    }

    @Transactional(readOnly = true)
    public List<SavingsGoalSummary> savingsGoals() {
        return goalRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(FinanceService::goalSummary).toList();
    }

    @Transactional
    public SavingsGoalSummary createSavingsGoal(String name, long targetAmount, long savedAmount,
            LocalDate targetDate, long monthlyContribution) {
        SavingsGoal goal = new SavingsGoal(UUID.randomUUID(), requireName(name, "name"),
                requirePositive(targetAmount), requireNonNegative(savedAmount, "savedAmount"),
                targetDate, requireNonNegative(monthlyContribution, "monthlyContribution"));
        goalRepository.saveAndFlush(goal);
        return goalSummary(goal);
    }

    @Transactional
    public SavingsGoalSummary updateSavingsGoal(UUID id, String name, long targetAmount,
            long savedAmount, LocalDate targetDate, long monthlyContribution, long expectedVersion) {
        SavingsGoal goal = getGoal(id);
        long version = requireNonNegative(expectedVersion, "version");
        if (goal.getVersion() != version) {
            throw new OptimisticLockingFailureException(
                    "Savings goal " + id + " was modified concurrently (expected version "
                            + version + ", is " + goal.getVersion() + ")");
        }
        goal.edit(requireName(name, "name"), requirePositive(targetAmount),
                requireNonNegative(savedAmount, "savedAmount"), targetDate,
                requireNonNegative(monthlyContribution, "monthlyContribution"));
        goalRepository.saveAndFlush(goal);
        return goalSummary(goal);
    }

    @Transactional
    public SavingsGoalSummary contributeSavingsGoal(UUID id, long amount) {
        SavingsGoal goal = getGoal(id);
        goal.contribute(requirePositive(amount));
        goalRepository.saveAndFlush(goal);
        return goalSummary(goal);
    }

    /** Compatibility alias for existing assistant and generated-client integrations. */
    @Transactional
    public SavingsGoalSummary contributeToSavingsGoal(UUID id, long amount) {
        return contributeSavingsGoal(id, amount);
    }

    @Transactional
    public void deleteSavingsGoal(UUID id) {
        goalRepository.delete(getGoal(id));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionSummary> subscriptions() {
        return subscriptionRepository.findAllByOrderByActiveDescNextDueOnAsc().stream()
                .map(FinanceService::subscriptionSummary).toList();
    }

    /** Active subscriptions due on or before {@code limit} (overdue included), soonest first. */
    @Transactional(readOnly = true)
    public List<SubscriptionSummary> subscriptionsDueBy(LocalDate limit) {
        return subscriptionRepository
                .findByActiveTrueAndNextDueOnLessThanEqualOrderByNextDueOnAsc(
                        requireDate(limit, "limit"))
                .stream().map(FinanceService::subscriptionSummary).toList();
    }

    @Transactional
    public SubscriptionSummary createSubscription(String name, long amount, String category,
            SubscriptionCycle cycle, LocalDate nextDueOn, boolean active,
            LocalDate cancelReminderOn) {
        Subscription subscription = new Subscription(UUID.randomUUID(), requireName(name, "name"),
                requirePositive(amount), canonicalCategory(category, TransactionType.EXPENSE),
                requireCycle(cycle),
                requireDate(nextDueOn, "nextDueOn"), active, cancelReminderOn);
        subscriptionRepository.saveAndFlush(subscription);
        return subscriptionSummary(subscription);
    }

    @Transactional
    public SubscriptionSummary updateSubscription(UUID id, String name, long amount,
            String category, SubscriptionCycle cycle, LocalDate nextDueOn, boolean active,
            LocalDate cancelReminderOn, long expectedVersion) {
        Subscription subscription = getSubscription(id);
        rejectStaleSubscriptionVersion(subscription, expectedVersion);
        subscription.edit(requireName(name, "name"), requirePositive(amount),
                canonicalCategory(category, TransactionType.EXPENSE), requireCycle(cycle),
                requireDate(nextDueOn, "nextDueOn"), active, cancelReminderOn);
        subscriptionRepository.saveAndFlush(subscription);
        return subscriptionSummary(subscription);
    }

    /**
     * The worker's daily sweep: post every active charge whose due date has
     * arrived (looping catches cycles missed while the machine was off) and
     * advance the schedule. Idempotent across runs — once posted, the due date
     * sits in the future and the next sweep is a no-op. Mark-paid stays as the
     * manual override for off-schedule payments.
     */
    @Transactional
    public List<TransactionSummary> postDueSubscriptions(LocalDate today) {
        requireDate(today, "today");
        List<TransactionSummary> posted = new ArrayList<>();
        for (Subscription subscription : subscriptionRepository
                .findByActiveTrueAndNextDueOnLessThanEqualOrderByNextDueOnAsc(today)) {
            while (!subscription.getNextDueOn().isAfter(today)) {
                posted.add(record(new NewTransaction(TransactionType.EXPENSE,
                        subscription.getAmount(), subscription.getNextDueOn(),
                        subscription.getName(), subscription.getCategory(), false),
                        TransactionSource.SUBSCRIPTION));
                subscription.advanceCycle();
            }
            subscriptionRepository.saveAndFlush(subscription);
        }
        return posted;
    }

    /**
     * Active subscriptions whose cancel-reminder date is due for a task —
     * anything on or before {@code limit}, past dates included (a machine that
     * was off still reminds, late beats never).
     */
    @Transactional(readOnly = true)
    public List<SubscriptionSummary> subscriptionsNeedingCancelReminder(LocalDate limit) {
        return subscriptionRepository
                .findByActiveTrueAndCancelReminderTaskIdIsNullAndCancelReminderOnLessThanEqual(
                        requireDate(limit, "limit"))
                .stream().map(FinanceService::subscriptionSummary).toList();
    }

    /** Record the cancel-reminder task the worker created, so it is never duplicated. */
    @Transactional
    public void markCancelReminderCreated(UUID id, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
        Subscription subscription = getSubscription(id);
        subscription.cancelReminderCreated(taskId);
        subscriptionRepository.saveAndFlush(subscription);
    }

    /** Confirm one cycle identity: write its expense and advance atomically. */
    @Transactional
    public SubscriptionPaymentSummary paySubscription(UUID id, LocalDate occurredOn,
            LocalDate expectedDueOn, long expectedVersion, LocalDate today,
            TransactionSource source) {
        Subscription subscription = getSubscription(id);
        rejectStaleSubscriptionCycle(subscription, expectedDueOn, expectedVersion);
        if (!subscription.isActive()) {
            throw new IllegalArgumentException("Cannot pay an inactive subscription");
        }
        LocalDate paymentDate = requireDate(occurredOn);
        if (paymentDate.isAfter(requireDate(today, "today"))) {
            throw new IllegalArgumentException("occurredOn must not be in the future");
        }
        TransactionSummary transaction = record(new NewTransaction(TransactionType.EXPENSE,
                subscription.getAmount(), paymentDate, subscription.getName(),
                subscription.getCategory(), false), source);
        subscription.advanceCycle();
        subscriptionRepository.saveAndFlush(subscription);
        return new SubscriptionPaymentSummary(transaction, subscriptionSummary(subscription));
    }

    @Transactional
    public void deleteSubscription(UUID id) {
        subscriptionRepository.delete(getSubscription(id));
    }

    /**
     * Seed order first, then ledger values the seed does not cover (sorted, compared
     * case-insensitively so "cafe" never rides alongside "Cafe").
     */
    static List<String> mergeVocabulary(List<String> seed, List<String> used) {
        Set<String> known = new LinkedHashSet<>();
        List<String> merged = new ArrayList<>();
        for (String category : seed) {
            if (known.add(category.toLowerCase(Locale.ROOT))) {
                merged.add(category);
            }
        }
        List<String> extras = new ArrayList<>();
        for (String category : used) {
            if (known.add(category.toLowerCase(Locale.ROOT))) {
                extras.add(category);
            }
        }
        extras.sort(String.CASE_INSENSITIVE_ORDER);
        merged.addAll(extras);
        return List.copyOf(merged);
    }

    private void recordCategoryCorrection(TransactionType type, String description,
            String category) {
        String key = descriptionKey(description);
        if (key.isBlank()) {
            return;
        }
        CategoryCorrection correction = categoryCorrectionRepository
                .findByTypeAndDescriptionKey(type, key)
                .orElseGet(() -> new CategoryCorrection(UUID.randomUUID(), type,
                        description, key, category));
        correction.edit(description, category);
        categoryCorrectionRepository.saveAndFlush(correction);
    }

    private BalanceCheckInSummary balanceCheckInSummary(BalanceCheckIn checkIn) {
        TransactionSummary adjustment = checkIn.getAdjustmentTransactionId() == null ? null
                : transactions.findById(checkIn.getAdjustmentTransactionId())
                        .map(this::summary).orElse(null);
        return balanceCheckInSummary(checkIn, adjustment);
    }

    private static BalanceCheckInSummary balanceCheckInSummary(BalanceCheckIn checkIn,
            TransactionSummary adjustment) {
        return new BalanceCheckInSummary(checkIn.getId(), checkIn.getCheckedOn(),
                checkIn.getBreakdown(), checkIn.getTotalBalance(), checkIn.getExpectedBalance(),
                checkIn.getDiscrepancy(), adjustment, checkIn.getCreatedAt());
    }

    private static BalanceBreakdown requireBalanceBreakdown(BalanceBreakdown breakdown) {
        BalanceBreakdown value = Objects.requireNonNull(breakdown, "breakdown is required");
        requireNonNegative(value.bankBalance(), "bankBalance");
        requireNonNegative(value.cashBalance(), "cashBalance");
        requireNonNegative(value.eWalletBalance(), "eWalletBalance");
        requireNonNegative(value.otherBalance(), "otherBalance");
        value.totalBalance();
        return value;
    }

    private static void rejectReconciliationMutation(Transaction transaction) {
        if (transaction.getSource() == TransactionSource.RECONCILIATION) {
            throw new IllegalArgumentException(
                    "Reconciliation adjustments are managed by their balance check-in");
        }
    }

    private static SubscriptionCycle detectCycle(List<TransactionSummary> ordered) {
        if (ordered.size() >= 3 && intervalsMatch(tail(ordered, 6), 25, 35)) {
            return SubscriptionCycle.MONTHLY;
        }
        if (ordered.size() >= 2 && intervalsMatch(tail(ordered, 4), 350, 380)) {
            return SubscriptionCycle.YEARLY;
        }
        return null;
    }

    private static List<TransactionSummary> tail(List<TransactionSummary> rows, int limit) {
        return rows.subList(Math.max(0, rows.size() - limit), rows.size());
    }

    private static boolean intervalsMatch(List<TransactionSummary> rows, int minimumDays,
            int maximumDays) {
        for (int i = 1; i < rows.size(); i++) {
            long days = ChronoUnit.DAYS.between(rows.get(i - 1).occurredOn(),
                    rows.get(i).occurredOn());
            if (days < minimumDays || days > maximumDays) {
                return false;
            }
        }
        return true;
    }

    private static long medianAmount(List<TransactionSummary> rows) {
        List<Long> amounts = rows.stream().map(TransactionSummary::amount).sorted().toList();
        int middle = amounts.size() / 2;
        if (amounts.size() % 2 == 1) {
            return amounts.get(middle);
        }
        return amounts.get(middle - 1) / 2 + amounts.get(middle) / 2
                + (amounts.get(middle - 1) % 2 + amounts.get(middle) % 2) / 2;
    }

    private static LocalDate advance(LocalDate date, SubscriptionCycle cycle) {
        return cycle == SubscriptionCycle.YEARLY ? date.plusYears(1) : date.plusMonths(1);
    }

    private static TransactionType requireType(TransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        return type;
    }

    private static long requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive (VND)");
        }
        return amount;
    }

    private static long requireNonNegative(long amount, String field) {
        if (amount < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return amount;
    }

    private static LocalDate requireDate(LocalDate occurredOn) {
        return requireDate(occurredOn, "occurredOn");
    }

    private static LocalDate requireDate(LocalDate value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        return requireMaxLength(description.strip(), 255, "description");
    }

    private static String requireName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return requireMaxLength(value.strip(), 120, field);
    }

    private static SubscriptionCycle requireCycle(SubscriptionCycle cycle) {
        if (cycle == null) {
            throw new IllegalArgumentException("cycle is required");
        }
        return cycle;
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null || category.isBlank() ? "Khác" : category.strip();
        return requireMaxLength(normalized, 64, "category");
    }

    private String canonicalCategory(String category, TransactionType type) {
        String normalized = normalizeCategory(category);
        String key = categoryKey(normalized);
        return categories(type).stream()
                .filter(known -> categoryKey(known).equals(key))
                .findFirst()
                .orElse(normalized);
    }

    private static String categoryKey(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String descriptionKey(String value) {
        return categoryKey(value).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static String requireMaxLength(String value, int maxLength, String field) {
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(field + " must be at most "
                    + maxLength + " characters");
        }
        return value;
    }

    private TransactionSummary summary(Transaction t) {
        return new TransactionSummary(t.getId(), t.getType(), t.getAmount(), t.getOccurredOn(),
                t.getDescription(), t.getCategory(), t.isExceptional(), t.getSource(),
                t.getCreatedAt());
    }

    private long spentForCategory(YearMonth month, String category) {
        return month(month).stream()
                .filter(row -> row.type() == TransactionType.EXPENSE
                        && row.category().equalsIgnoreCase(category))
                .mapToLong(TransactionSummary::amount).sum();
    }

    private void rejectDuplicateBudget(UUID currentId, YearMonth month, String category) {
        budgetRepository.findByMonthStartAndCategoryIgnoreCase(month.atDay(1), category)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A budget already exists for "
                            + category + " in " + month);
                });
    }

    private Budget getBudget(UUID id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new FinancePlanningNotFoundException("budget", id));
    }

    private SavingsGoal getGoal(UUID id) {
        return goalRepository.findById(id)
                .orElseThrow(() -> new FinancePlanningNotFoundException("savings goal", id));
    }

    private Subscription getSubscription(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new FinancePlanningNotFoundException("subscription", id));
    }

    private static void rejectStaleSubscriptionVersion(Subscription subscription,
            long expectedVersion) {
        long version = requireNonNegative(expectedVersion, "version");
        if (subscription.getVersion() != version) {
            throw new OptimisticLockingFailureException(
                    "Subscription " + subscription.getId()
                            + " was modified concurrently (expected version " + version
                            + ", is " + subscription.getVersion() + ")");
        }
    }

    private static void rejectStaleSubscriptionCycle(Subscription subscription,
            LocalDate expectedDueOn, long expectedVersion) {
        LocalDate dueOn = requireDate(expectedDueOn, "expectedDueOn");
        rejectStaleSubscriptionVersion(subscription, expectedVersion);
        if (!subscription.getNextDueOn().equals(dueOn)) {
            throw new OptimisticLockingFailureException(
                    "Subscription " + subscription.getId()
                            + " cycle changed (expected due " + dueOn
                            + ", is " + subscription.getNextDueOn() + ")");
        }
    }

    private static BudgetSummary budgetSummary(Budget budget, long spent) {
        return budgetSummary(budget, YearMonth.from(budget.getMonthStart()), spent, false);
    }

    /** {@code month} is the REQUESTED month — for a carry-forward it differs from the row's own. */
    private static BudgetSummary budgetSummary(Budget budget, YearMonth month, long spent,
            boolean inherited) {
        long remaining = budget.getLimitAmount() - spent;
        int progress = (int) Math.round(spent * 100.0 / budget.getLimitAmount());
        return new BudgetSummary(budget.getId(), month.toString(),
                budget.getCategory(), budget.getLimitAmount(), spent, remaining, progress,
                spent > budget.getLimitAmount(), inherited, budget.getCreatedAt());
    }

    private static SavingsGoalSummary goalSummary(SavingsGoal goal) {
        long remaining = Math.max(0, goal.getTargetAmount() - goal.getSavedAmount());
        int progress = (int) Math.round(goal.getSavedAmount() * 100.0 / goal.getTargetAmount());
        return new SavingsGoalSummary(goal.getId(), goal.getName(), goal.getTargetAmount(),
                goal.getSavedAmount(), remaining, goal.getMonthlyContribution(), goal.getTargetDate(),
                progress, goal.getSavedAmount() >= goal.getTargetAmount(), goal.getVersion(),
                goal.getCreatedAt());
    }

    private static SubscriptionSummary subscriptionSummary(Subscription subscription) {
        long monthly = subscription.getCycle() == SubscriptionCycle.MONTHLY
                ? subscription.getAmount() : Math.round(subscription.getAmount() / 12.0);
        return new SubscriptionSummary(subscription.getId(), subscription.getName(),
                subscription.getAmount(), subscription.getCategory(), subscription.getCycle(),
                subscription.getNextDueOn(), subscription.isActive(), monthly,
                subscription.getCancelReminderOn(), subscription.getVersion(),
                subscription.getCreatedAt());
    }
}
