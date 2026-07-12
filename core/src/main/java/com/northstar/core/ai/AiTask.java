package com.northstar.core.ai;

public enum AiTask {
    ASSISTANT,
    CAPTURE,
    ALIGNMENT,
    TITLE,
    STUDY_GRADER,
    IMAGE_CAPTION,
    TEXT_TO_SPEECH,
    SPEECH_TO_TEXT,
    REALTIME_TRANSCRIPTION,
    IMAGE_GENERATION,
    EMBEDDING;

    public AiGatewayCapability requiredCapability() {
        return switch (this) {
            case TEXT_TO_SPEECH -> AiGatewayCapability.TEXT_TO_SPEECH;
            case SPEECH_TO_TEXT -> AiGatewayCapability.SPEECH_TO_TEXT;
            case REALTIME_TRANSCRIPTION -> AiGatewayCapability.REALTIME;
            case IMAGE_GENERATION -> AiGatewayCapability.IMAGE_GENERATION;
            case EMBEDDING -> AiGatewayCapability.EMBEDDING;
            default -> AiGatewayCapability.CHAT;
        };
    }
}
