# 0009 - Subscriptions Are Visible And Explicitly Paid

## Status

Accepted for subscription visibility and payment safety. The original
no-automatic-posting assumption is superseded by
[0010](./0010-finance-reanchors-the-ledger-and-learns-locally.md); the UI,
optimistic-locking, cycle-identity, and billing-anchor decisions remain in
force.

## Context

The user confirmed that recurring-charge management is a necessary Finance
workflow, alongside budgets and savings goals. Keeping definitions only behind
REST and assistant tools makes the state difficult to inspect and correct.

At the time of this decision, automatic posting was deferred because the ledger
had no reconciliation anchor. The UI still needed to expose the schedule
without hiding payment state or allowing retries to duplicate a charge.

## Decision

- Expose subscriptions as the third Finance tab. The list is global rather than
  month-scoped, so the Finance month selector is hidden on that tab.
- Show active monthly-equivalent cost, active count, and the earliest next due
  date. A yearly charge contributes `amount / 12` to the monthly-equivalent
  total while retaining its actual yearly payment amount.
- Creating or editing a subscription never creates a transaction immediately.
  The later worker policy in decision 0010 posts due active definitions.
- `Mark paid` is explicit. It creates exactly one expense for the selected
  payment date and advances the subscription by one monthly or yearly cycle in
  the same transaction.
- Bind payment to the version and due cycle the user saw. A stale edit or retry
  conflicts instead of overwriting an advanced due date or adding the same
  cycle twice.
- Reject future payment dates. Preserve the original billing day/month when a
  short month or leap year temporarily clamps the next due date.
- Paused subscriptions remain visible, are excluded from active monthly cost,
  and cannot be marked paid. Deleting a definition does not erase historical
  ledger entries.

## Consequences

Finance can answer what repeats, how much active subscriptions cost per month,
and what is due next. Decision 0010 later allowed due definitions to auto-post
because aggregate reconciliation can expose ledger drift; explicit payment
remains an off-schedule override.

The version/cycle precondition is an application-level idempotency boundary for
one scheduled charge. It does not attempt provider-side billing synchronization
or bank-statement deduplication.
