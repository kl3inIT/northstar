package com.northstar.core.brief;

/** The external feed was unavailable or changed its response contract. */
public class BriefFeedException extends RuntimeException {

    public BriefFeedException(String message) {
        super(message);
    }

    public BriefFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
