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

        assertEquals("nine-router", properties.routeDefaults().get(AiTask.ASSISTANT).gatewayId());
        assertEquals("assistant-combo", properties.routeDefaults().get(AiTask.ASSISTANT).modelId());
        assertEquals("caption-combo", properties.routeDefaults().get(AiTask.IMAGE_CAPTION).modelId());
    }

    @Test
    void gatewayToStringNeverLeaksTheSecret() {
        String rendered = properties().gateways().get("nine-router").toString();

        assertFalse(rendered.contains("secret-key"));
    }

    private static AiProperties properties() {
        var gateway = new AiProperties.Gateway(
                AiGatewayType.OPENAI_COMPATIBLE,
                "9Router",
                "https://router.example/v1",
                "secret-key",
                List.of("assistant-combo", "caption-combo"),
                false,
                Duration.ofSeconds(30));
        var routes = new AiProperties.Routes(
                "assistant-combo",
                "fast-combo",
                "assistant-combo",
                "fast-combo",
                "grader-combo",
                "caption-combo");
        return new AiProperties("nine-router", Map.of("nine-router", gateway), routes,
                new AiProperties.Catalog(Duration.ofMinutes(2)));
    }
}
