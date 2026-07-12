package com.northstar.integration.ai.openai;

import com.northstar.core.speech.SpeechTarget;
import java.util.List;
import java.util.Map;
import com.northstar.core.ai.AiGatewayCapability;

public record AiGatewayTestResult(
        boolean success,
        long latencyMillis,
        List<AiModelDescriptor> models,
        List<SpeechTarget> ttsTargets,
        Map<AiGatewayCapability, List<AiCapabilityTarget>> capabilityTargets,
        String message) {
}
