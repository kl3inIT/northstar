package com.northstar.core.finance;

import java.time.LocalDate;

/**
 * Input shape for recording one ledger entry — shared by the capture confirm
 * path, the assistant's log_transactions and the page. The caller supplies the
 * already-resolved values (amount in VND, an absolute date); parsing natural
 * language happens upstream in capture/assistant, never here.
 */
public record NewTransaction(
        TransactionType type,
        long amount,
        LocalDate occurredOn,
        String description,
        String category,
        boolean exceptional) {
}
