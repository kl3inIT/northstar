package com.northstar.core.study;

import java.util.UUID;

/** Raised when a vocab-card id does not exist; the API maps it to 404. */
public class VocabCardNotFoundException extends RuntimeException {

    public VocabCardNotFoundException(UUID id) {
        super("No vocab card with id " + id);
    }
}
