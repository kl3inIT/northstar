# Finance Spec

## Current Behavior

Finance combines a capture-first VND ledger with lightweight planning and an
aggregate balance-reconciliation anchor. It does not model individual wallets,
bank accounts, transfers, provider synchronization, or multiple currencies.

Each transaction stores:

- `EXPENSE` or `INCOME`;
- a positive whole-VND amount;
- the date the money moved;
- a short description and category;
- an `exceptional` flag for atypical one-off spending;
- its entry source: `CAPTURE`, `ASSISTANT`, `MANUAL`, `SUBSCRIPTION`, or
  `RECONCILIATION`.

Expense categories start with a VN-tuned vocabulary: An uong, Cafe, Di lai,
Hoa don, Nha cua, Mua sam, Suc khoe, Giai tri, Hoc tap, Du lich, Hieu hi, Gia
dinh, and Khac (stored with their Vietnamese diacritics). Income starts with
Luong, Thuong, Qua tang, and Khac. Extraction prompts receive the seed values
unioned with categories already used in the ledger so they reuse labels instead
of creating spelling variants.

## Entry Paths

- The Capture page is the only human-facing capture entry point. Finance does
  not duplicate text, voice, or receipt controls.
- Text and transcribed voice use the same classifier. Money already spent or
  received becomes an `EXPENSE` draft; an intention to buy remains a task.
- One sentence can produce several transactions. Vietnamese shorthands such as
  `35k`, `2tr`, and `2tr5` are expanded by the model, then the server validates
  the resolved positive VND integers and absolute dates.
- Banking SMS text uses the same Capture input. Prompt rules distinguish the
  transaction amount (`GD`) from the reported balance (`SD`), preserve the
  message timestamp, infer common merchant codes, and accept several pasted
  messages without adding a second capture surface.
- Receipt images are sent to the multimodal model and are not stored. The model
  returns expense items through the same draft shape.
- Confirmed capture items are written as one batch and the web echo names every
  amount and category. The toast exposes an undo action that deletes the batch.
- Assistant and MCP callers can use `log_transactions`, `spending_summary`,
  `find_transactions`, and `delete_transaction`.

## Read And Correction Paths

- `/finance` is a read-mostly monthly view with spent, income, net, one-off,
  previous-month, and expense-category summaries.
- The transaction table can sort by date and amount. A row can be corrected
  without changing its type or source, or deleted.
- Category correction selects from the constrained vocabulary. The one-off flag
  remains user-overridable. A real category change stores a compact mapping from
  normalized description and type to the chosen category; recent mappings are
  fed back to Capture as user-specific few-shot examples.
- A balance check-in records an explicit end-of-day breakdown for bank
  accounts, cash, e-wallets, and other balances. The server derives the aggregate
  total; callers cannot submit a detached total that accidentally reconciles one
  account against the whole ledger. The first check-in establishes a baseline.
  Each later check-in compares the new total with the prior total plus
  intervening income minus expense, then writes the signed discrepancy as one
  immutable reconciliation entry in category `Khac`.
- Only the latest balance check-in can be undone. Undo removes both its anchor
  and generated reconciliation row while preserving user-recorded transactions.
  Earlier anchors stay immutable so a later check-in can never be left based on
  a deleted predecessor. This remains aggregate reconciliation: transaction rows
  still do not claim an account or explain which balance component moved.
- The transaction toolbar exports the loaded month as CSV for statement review
  or spreadsheet analysis.
- The weekly alignment review reports ordinary category totals separately from
  exceptional purchases and compares the week with the median of the previous
  four full weeks. This is a descriptive reference, not a budget.
- The same review section lists active subscriptions charging through the end
  of the following week (auto-posted awareness, not a chore) and any
  cancel-reminder dates falling in that window.

## Budgets And Savings Goals

- A monthly budget is one positive VND limit for one expense category and one
  calendar month. Spent, remaining, and progress are always derived from ledger
  rows; they are not copied into the budget row.
- A category can have at most one budget in a month. The same category can use a
  different limit next month without rewriting history.
- Limits carry forward per category: a category the user has not re-set in the
  requested month shows the latest earlier budgeted month's limit with
  `inherited=true`, measured against the requested month's spend — a new month
  never opens empty. Editing an inherited limit materializes a real row for
  that month only; an inherited entry's id belongs to the source month, so
  writers create for the requested month instead of updating or deleting by
  that id. Months before the first budget ever set stay blank.
- A savings goal stores a name, positive target, current saved amount, optional
  target date, and optional expected monthly contribution.
- Adding a goal contribution increments goal progress only. It is not recorded
  as an expense because V1 has no account or transfer model.
- Savings-goal edits carry the version that was read. A contribution or other
  write that lands first makes a stale edit fail with a conflict instead of
  silently replacing the new balance.
- Budgets and goals can be managed in Finance or through explicit assistant/MCP
  tool calls. This keeps maintenance optional and lets the assistant apply a
  user-approved plan without inventing one.
- The Finance page adapts the compact stat strip, transaction toolbar/table,
  SVG budget-ring, and savings-progress patterns from the MIT-licensed
  [shadcn-fintech](https://github.com/abderrahimghazali/shadcn-fintech)
  reference to Northstar's real data and design system.

## Recurring Charge Definitions

- A subscription definition stores a name, positive VND amount, expense
  category, monthly/yearly cycle, next due date, active state, and an optional
  cancel-reminder date.
- Due charges post to the ledger AUTOMATICALLY: a worker sweep (every few
  hours) writes the expense with source `SUBSCRIPTION` on the due date and
  advances the cycle, looping to catch up cycles missed while the machine was
  off. The sweep is idempotent — posted charges move the due date into the
  future. Paused subscriptions never auto-charge.
- Mark-paid remains only as a manual override for off-schedule payments; it
  writes one expense and advances the due date atomically.
- The cancel-reminder date ("remind me to cancel/review by X" — a trial about
  to convert, a planned cancellation) becomes ONE task, created by the worker
  shortly before the date and due on it. The created task id is stored on the
  subscription so the reminder never duplicates; moving or clearing the date
  re-arms it. The weekly review also lists cancel-by dates in its window.
- The Finance `Subscriptions` tab shows active monthly-equivalent cost, active
  count, next charge, due/overdue state, and active/paused filtering. It is not
  month-scoped, so the page month selector is hidden while this tab is active.
- Deterministic recurring detection groups ordinary expenses with stable
  amounts and monthly/yearly intervals, excludes reconciliation/subscription
  rows and already tracked definitions, and offers an explicit prefilled
  `Track` action. Detection never creates a subscription by itself.
- Payment is bound to the version and due cycle the caller read. Retrying a
  completed cycle or submitting a stale edit fails with a conflict instead of
  adding another expense or restoring an old due date.
- Future payment dates are rejected. Month-end and leap-year schedules retain
  their original billing anchor when advancing.
- Subscription definitions and explicit payment are also available to REST and
  assistant/MCP callers.

## Insights And Motion

- The `Insights` tab shows 12 monthly spending buckets with exceptional spend
  separated, the current month's category breakdown, and 365 user-local daily
  buckets in a 53-by-7 heatmap. It stays in Finance so month/category context
  and transaction drill-down remain in one domain.
- Recharts is lazy-loaded with the Insights panel. Opening Transactions,
  Budgets, or Subscriptions does not pay the chart bundle cost.
- Shared app motion uses async `LazyMotion`, `m`, strict mode, and the user's
  reduced-motion preference. Route, short-list, tab, and stat transitions are
  limited to opacity/transform and roughly 150-200 ms. Long tables, calendar,
  CodeMirror, and dnd-kit drag nodes remain unanimated.

## Delivery Contracts

- `GET /api/finance?month=yyyy-MM`
- `GET /api/finance/summary?month=yyyy-MM`
- `GET /api/finance/categories?type=EXPENSE|INCOME`
- `POST /api/finance` for a confirmed batch
- `PUT /api/finance/{id}` for a full correction
- `DELETE /api/finance/{id}`
- `POST /api/capture/receipt` for multimodal receipt drafting
- Monthly budget list/create/update/delete under `/api/finance/budgets`
- Savings-goal list/create/update/contribute/delete under
  `/api/finance/savings-goals`
- Subscription list/create/update/delete and explicit payment under
  `/api/finance/subscriptions`
- Balance check-in list/create and latest-only undo under
  `/api/finance/balance-check-ins`
- `GET /api/finance/insights?through=yyyy-MM-dd`
- `GET /api/finance/recurring-suggestions`

## Source Modules

- `core.finance`
- `core.capture`
- `core.assistant`
- `core.alignment`
- `apps/api.finance`
- `apps/api.capture`
- `apps/worker` (`SubscriptionWorker` — auto-posting and cancel reminders)
- `web/src/pages/finance.tsx`
- `web/src/pages/capture.tsx`

## Related Decisions

- [0008 - Finance Uses A Capture-First Ledger And Lightweight Planning](../../decisions/0008-finance-is-a-capture-first-ledger.md)
- [0009 - Subscriptions Are Visible And Explicitly Paid](../../decisions/0009-subscriptions-are-visible-and-explicitly-paid.md)
- [0010 - Finance Reanchors The Ledger And Learns Locally](../../decisions/0010-finance-reanchors-the-ledger-and-learns-locally.md)
- [0030 - Finance Check-ins Require A Balance Breakdown](../../decisions/0030-finance-check-ins-require-a-balance-breakdown.md)
