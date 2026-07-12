package com.northstar.core.ai;

public enum AiTask {
    ASSISTANT,
    CAPTURE,
    ALIGNMENT,
    TITLE,
    STUDY_GRADER,
    IMAGE_CAPTION,
    TEXT_TO_SPEECH;

    public AiGatewayCapability requiredCapability() {
        return this == TEXT_TO_SPEECH
                ? AiGatewayCapability.TEXT_TO_SPEECH
                : AiGatewayCapability.CHAT;
    }
}
