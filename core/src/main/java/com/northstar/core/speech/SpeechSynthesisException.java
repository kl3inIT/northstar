package com.northstar.core.speech;

public class SpeechSynthesisException extends RuntimeException {

    public SpeechSynthesisException(String message) {
        super(message);
    }

    public SpeechSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}
