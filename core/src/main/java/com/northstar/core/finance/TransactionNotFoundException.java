package com.northstar.core.finance;

import java.util.UUID;

/** Raised when a ledger entry id does not exist; the API maps it to 404. */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID id) {
        super("No transaction with id " + id);
    }
}
