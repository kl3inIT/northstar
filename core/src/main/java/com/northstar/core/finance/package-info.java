/**
 * Finance — a VND ledger with lightweight planning. Transactions are expenses
 * or income; monthly category budgets derive their spend from those ledger rows,
 * while savings goals track user-reported progress without pretending to be
 * accounts or transfers. Entries arrive through AI capture (text, voice, or a
 * receipt photo) or assistant tools. Categories come from a seeded VN-tuned
 * vocabulary unioned with values already in the ledger, so the taxonomy
 * converges instead of drifting. {@code exceptional} flags one-off purchases;
 * the review reports them separately from ordinary category totals.
 */
package com.northstar.core.finance;
