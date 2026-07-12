package com.northstar.core.speech;

/** A gateway-specific model/voice identifier accepted by text-to-speech. */
public record SpeechTarget(String gatewayId, String id, String displayName) {
}
