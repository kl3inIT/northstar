package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.northstar.core.ai.AiTask;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiPropertiesTests {

    @Test
    void routesUseTheActiveUserDefinedGateway() {
        var properties = properties();

        assertEquals("custom-router", properties.routeDefaults().get(AiTask.ASSISTANT).gatewayId());
        assertEquals("assistant-combo", properties.routeDefaults().get(AiTask.ASSISTANT).modelId());
        assertEquals("caption-combo", properties.routeDefaults().get(AiTask.IMAGE_CAPTION).modelId());
        assertEquals("openai/gpt-4o-mini-tts/alloy",
                properties.routeDefaults().get(AiTask.TEXT_TO_SPEECH).modelId());
    }

    @Test
    void gatewayToStringNeverLeaksTheSecret() {
        String rendered = properties().gateways().get("custom-router").toString();

        assertFalse(rendered.contains("secret-key"));
    }

    private static AiProperties properties() {
        var gateway = new AiProperties.Gateway(
                com.northstar.core.ai.AiGatewayType.OPENAI_CHAT_COMPATIBLE,
                "Custom Router",
                "https://router.example/v1",
                "secret-key",
                List.of("assistant-combo", "caption-combo"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                false,
                Duration.ofSeconds(30));
        var routes = new AiProperties.Routes(
                "assistant-combo",
                "fast-combo",
                "assistant-combo",
                "fast-combo",
                "grader-combo",
                "caption-combo",
                "openai/gpt-4o-mini-tts/alloy",
                "whisper-1",
                "gpt-image-2",
                "text-embedding-3-large");
        return new AiProperties("custom-router", Map.of("custom-router", gateway), routes,
                new AiProperties.Catalog(Duration.ofMinutes(2)),
                new AiProperties.Credentials(""));
    }
}
