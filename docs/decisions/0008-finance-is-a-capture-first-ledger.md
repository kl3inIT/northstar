# 0008 - Finance Uses A Capture-First Ledger And Lightweight Planning

## Status

Accepted.

## Context

Northstar is intended to reduce system-maintenance work. A conventional
personal-finance design with wallets, transfers, automatic recurring posting,
budget hierarchies, and mandatory transaction forms would create another system the
user has to keep in sync. It would also duplicate Capture controls across
domain pages.

The useful first behavior is lower-friction logging and review: record several
amounts from one natural sentence, preserve atypical purchases separately from
routine spending, and provide descriptive month/week comparisons. The user also
requires two planning primitives: category budgets and savings goals. The
maintenance-risk evidence is a reason to keep those primitives narrow and
assistant-accessible, not to remove a stated requirement.

## Decision

- Store one ledger of VND expense/income transactions.
- Keep text, voice, and receipt entry on the single Capture page; `/finance` is
  a monthly read and correction view.
- Let the LLM extract amounts, dates, type, category, and the exceptional flag;
  let deterministic domain code validate and persist the resolved values.
- Constrain categories to a seeded vocabulary unioned with values already in
  the ledger. Use `Khac` when uncertain instead of creating near-duplicates.
- Treat `exceptional` as first-class and user-overridable so reviews can separate
  one-offs from ordinary category totals.
- Infer previous-month and prior-week reference points from the ledger.
- Support one VND limit per expense category and month. Derive spent/remaining
  from transactions and keep historical months independent.
- Support savings goals with current and target amounts, an optional target
  date, and an expected monthly contribution. Goal contributions do not become
  expenses because there is no account/transfer model.
- Reject stale savings-goal full edits by version so a concurrent contribution
  cannot be silently overwritten.
- Expose budget and goal maintenance to the assistant/MCP surface, but require
  explicit user intent for writes.
- Allow narrow recurring-charge definitions through REST and assistant/MCP.
  Only an explicit mark-paid action creates an expense and advances the due
  date; there is no automatic posting or Finance-page subscription tab.
- Do not add accounts, wallets, transfers, reconciliation, envelope
  hierarchies, or multi-currency until a later increment demonstrates the need.
- Use the shadcn Data Table pattern over TanStack Table. Kibo Table adds no
  domain behavior for this ledger and is not introduced.

## Consequences

Logging can stay fast and AI-native, including catch-up batches and receipt
photos, while the Finance page remains focused on inspection and correction.
Month-to-month category data stays comparable and weekly reviews can discuss
ordinary and exceptional spending without presenting a budget as a promise.

Monthly limits and goal progress add deliberate planning without turning
Finance into account reconciliation. They require some maintenance, mitigated
by direct assistant tools and narrow data shapes.

Recurring definitions remove repeated re-entry while retaining an explicit
ledger write. Keeping them outside the current Finance UI prevents the stated
budget/goal workflow from becoming a broad personal-finance dashboard.

V1 has no reconciliation ground truth or duplicate detection. Missing entries
are therefore invisible, savings balances are user/assistant-reported, and
account/balance check-ins remain future work.
