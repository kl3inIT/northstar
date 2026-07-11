package com.northstar.core.study;

import java.util.UUID;

public class SpeakingFeedbackNotFoundException extends RuntimeException {

    public SpeakingFeedbackNotFoundException(UUID id) {
        super("Speaking feedback not found: " + id);
    }
}
