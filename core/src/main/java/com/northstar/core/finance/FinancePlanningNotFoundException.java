package com.northstar.core.finance;

import java.util.UUID;

/** Missing budget, savings goal, or subscription. */
public class FinancePlanningNotFoundException extends RuntimeException {

    public FinancePlanningNotFoundException(String kind, UUID id) {
        super("No " + kind + " with id " + id);
    }
}
