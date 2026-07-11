package com.northstar.core.study;

import java.util.UUID;

public class WritingFeedbackNotFoundException extends RuntimeException {

    public WritingFeedbackNotFoundException(UUID id) {
        super("Writing feedback not found: " + id);
    }
}
