package com.northstar.core.discipline;

import java.util.UUID;

/** Thrown when a discipline id does not exist. */
public class DisciplineNotFoundException extends RuntimeException {

    public DisciplineNotFoundException(UUID id) {
        super("Discipline not found: " + id);
    }
}
