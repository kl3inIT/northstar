# 0030 - Finance Check-ins Require A Balance Breakdown

## Status

Accepted. This refines the aggregate reconciliation decision in
[0010](./0010-finance-reanchors-the-ledger-and-learns-locally.md).

## Context

Finance has one aggregate ledger and no account identity on transactions. A
single `actualBalance` field made it easy to enter only a bank balance even when
the ledger also contained cash income. The arithmetic then correctly treated
the omitted cash as missing spending, but the input contract did not make the
aggregate scope difficult to misunderstand.

Historical check-ins stored only a total, so their bank, cash, e-wallet, and
other portions cannot be reconstructed honestly.

## Decision

- Replace the caller-supplied total with required bank, cash, e-wallet, and
  other balance fields. Persist each component and derive the total on the
  server with checked arithmetic.
- Keep reconciliation aggregate until transactions have a real account model.
  A balance component is an input aid and audit fact, not an account assignment.
- Permit undo only for the latest check-in. Undo removes its generated
  reconciliation transaction in the same database transaction and leaves all
  user-recorded ledger rows untouched.
- Reset historical check-ins and their generated reconciliation rows during the
  schema migration instead of mislabeling an old aggregate total as a bank
  balance.

## Consequences

Cash and bank balances are visible at the moment they matter, so reconciling one
bank account against the whole ledger is no longer the default path. Users can
repair the most recent mistake without paired fake adjustments. Existing
reconciliation history is intentionally discarded once; ordinary income and
expense history is preserved.

This does not enable account-specific balances, transfers, or bank statement
matching. Those require account identity on transaction rows and remain a
separate future model.
