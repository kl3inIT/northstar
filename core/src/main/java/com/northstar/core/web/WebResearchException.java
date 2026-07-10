package com.northstar.core.web;

public class WebResearchException extends RuntimeException {

    private final WebResearchFailureCode code;

    public WebResearchException(WebResearchFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public WebResearchException(WebResearchFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public WebResearchFailureCode code() {
        return code;
    }

    public boolean isRetryable() {
        return code == WebResearchFailureCode.RATE_LIMITED
                || code == WebResearchFailureCode.UNAVAILABLE;
    }
}
