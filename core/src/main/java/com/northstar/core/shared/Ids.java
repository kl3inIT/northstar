package com.northstar.core.shared;

import java.util.UUID;

/** Identifier helpers shared across modules. */
public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }
}
