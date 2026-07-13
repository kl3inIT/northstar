package com.northstar.core.habit;

import java.util.UUID;

public final class HabitNotFoundException extends RuntimeException {
    public HabitNotFoundException(UUID id) {
        super("No habit with id " + id);
    }
}

