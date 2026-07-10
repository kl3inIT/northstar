# Finance Accuracy, Insights, And Motion

## Problem

The capture-first ledger is useful only while its numbers remain believable.
Northstar currently has no way to re-anchor a self-reported ledger to reality,
does not learn from category corrections, and makes users manually identify
recurring charges. The Finance page also has no time-based view and no export
path. Across the app, route and short-list changes have no shared motion policy.

## Scope

This increment adds:

- aggregate end-of-day balance check-ins and reconciliation transactions;
- recent category-correction examples in the Capture extraction prompt;
- Vietnamese banking SMS examples and extraction rules in the existing Capture
  input, with no second capture surface;
- recurring-expense suggestions that the user may promote to subscriptions;
- monthly CSV export;
- a Finance `Insights` tab with a 12-month trend, current-month category
  breakdown, and trailing-365-day spending heatmap;
- a shared Motion provider and restrained route, tab, short-list, and Finance
  stat transitions.

Savings-goal contributions remain reported progress rather than ledger writes.
Accounts, wallets, and transfers remain outside this increment.

## Domain Design

### Balance Check-In

A check-in records one aggregate actual balance at the end of a calendar day.
The first check-in establishes a baseline and writes no adjustment. A later
check-in must be after the latest check-in date. Its expected balance is:

`previous actual balance + income - expense since the previous check-in`

The signed difference between actual and expected is persisted on the check-in.
A negative difference writes one `EXPENSE` reconciliation transaction; a
positive difference writes one `INCOME` reconciliation transaction. The
adjustment uses category `Khac`, is not exceptional, and cannot be edited or
deleted independently because that would invalidate the audit trail.

### Correction Memory

When a user changes a transaction category, Finance upserts a compact mapping
from normalized description plus transaction type to the chosen category. The
most recently corrected mappings are added to Capture's prompt as user-specific
few-shot examples. Editing other fields without changing category stores no
example.

### Recurring Suggestions

Detection is deterministic and read-only. It groups ordinary expense rows by a
normalized description, requires repeated intervals near a monthly or yearly
cycle and stable amounts, and excludes rows already represented by a
subscription. A suggestion carries enough data to prefill a subscription, but
never creates one without an explicit user action.

### Insights

One read model returns 12 monthly buckets ending in the current user-local
month, the selected month's expense categories, and 365 daily expense buckets
ending today. Exceptional spend remains visible as a separate monthly value.
The frontend uses shadcn chart composition on Recharts v3 and a semantic CSS
grid for the heatmap.

### Motion

The root uses `LazyMotion` with asynchronously loaded `domAnimation`, the `m`
component, strict mode, and `MotionConfig reducedMotion="user"`. Shared motion
is limited to opacity and transform, generally 150-200ms. Existing shadcn CSS
animation remains responsible for dialogs, dropdowns, sheets, and toasts.
Long transaction tables, calendar grids, CodeMirror, and dnd-kit drag elements
do not receive layout animation.

## Delivery Contracts

- `GET /api/finance/balance-check-ins`
- `POST /api/finance/balance-check-ins`
- `GET /api/finance/insights?through=yyyy-MM-dd`
- `GET /api/finance/recurring-suggestions`
- existing transaction update records category corrections when applicable;
- existing Capture endpoint handles banking SMS through improved prompting;
- CSV export is client-side from the already loaded monthly transaction list.

## Verification

- persistence and service integration tests cover baseline and both adjustment
  directions, immutable reconciliation rows, correction learning, insights
  buckets, and recurring detection/exclusion;
- Capture integration tests inspect the system prompt for SMS and correction
  context;
- generated OpenAPI and TypeScript clients compile;
- browser walkthrough covers the new Finance actions, Insights on desktop and
  mobile, reduced-height layout, CSV download, and a clean console.
