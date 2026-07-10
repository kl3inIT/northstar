# 0010 - Finance Reanchors The Ledger And Learns Locally

## Status

Accepted. This supersedes the no-reconciliation and no-automatic-posting
assumptions in [0008](./0008-finance-is-a-capture-first-ledger.md) and
[0009](./0009-subscriptions-are-visible-and-explicitly-paid.md). Their
capture-first, visible-management, optimistic-locking, and payment-safety
decisions remain in force.

## Context

A manually captured ledger drifts when small transactions are forgotten. Once
the displayed balance is not believable, every budget and insight built on it
loses value. Repeated category corrections and recurring transactions also
contain useful personal patterns that should reduce future maintenance without
silently letting the model invent financial state.

Northstar still has no wallet/account/transfer model or bank connection. A
reconciliation design therefore has to improve aggregate accuracy while being
explicit about what it cannot identify.

## Decision

- Record occasional aggregate end-of-day balance check-ins. The first is a
  baseline; each later check-in writes the signed difference from expected
  ledger balance as an immutable income or expense reconciliation row.
- Treat reconciliation as an audit anchor, not an inferred bank transaction.
  It uses category `Khac` and does not guess an account or merchant.
- Save only explicit transaction-category changes as recent normalized prompt
  examples. Do not fine-tune a model or learn from unchanged edits.
- Parse banking SMS in the existing Capture surface with rules for transaction
  amount versus reported balance, embedded timestamp, merchant code, and
  multiple pasted messages.
- Detect stable monthly/yearly expense patterns deterministically and present a
  prefilled subscription action. Never create a definition from detection
  alone.
- Automatically post charges from user-confirmed active subscription
  definitions. Keep the sweep idempotent, preserve billing anchors, and retain
  explicit payment as an off-schedule override.
- Keep savings-goal contributions outside the ledger until accounts and
  transfers exist.

## Consequences

Finance can regularly return to a believable aggregate balance and gets more
personal with ordinary corrections. The adjustment amount also exposes how
much activity was missed, but it cannot explain where that money moved. Users
remain responsible for promoting suggestions and defining subscriptions.

When Northstar introduces accounts, reconciliation rows and goal contributions
will need an explicit migration into account-scoped adjustments and transfers.
