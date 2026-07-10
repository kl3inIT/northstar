package com.northstar.core.finance;

/** Which entry channel produced a transaction — kept for later adherence analysis. */
public enum TransactionSource {
    /** AI capture flow (text, voice or receipt photo) confirmed by the user. */
    CAPTURE,
    /** Assistant chat / MCP tool call. */
    ASSISTANT,
    /** Direct edit or manual entry on the Finance page. */
    MANUAL,
    /** Auto-posted by the worker when a subscription's due date arrives. */
    SUBSCRIPTION,
    /** Ledger adjustment created by an aggregate balance check-in. */
    RECONCILIATION
}
