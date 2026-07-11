package com.northstar.core.study;

import java.util.UUID;

/** Raised when a study-session id does not exist; the API maps it to 404. */
public class StudySessionNotFoundException extends RuntimeException {

    public StudySessionNotFoundException(UUID id) {
        super("No study session with id " + id);
    }
}
