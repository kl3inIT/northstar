package com.northstar.core.study;

public class SpeechAssessmentException extends RuntimeException {

    private final Failure failure;

    public SpeechAssessmentException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public SpeechAssessmentException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public enum Failure {
        BAD_AUDIO,
        AUTHENTICATION,
        QUOTA,
        TIMEOUT,
        PROVIDER
    }
}
