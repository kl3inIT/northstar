package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.northstar.core.ai.AiGatewayType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiClientRouterTests {

    @Test
    void nineRouterStructuredOutputIsDeterministicAndUsesLegacyTokenField() {
        OpenAiChatOptions options = SpringAiClientRouter.providerStructuredOutputOptions(
                AiGatewayType.NINE_ROUTER, "cx/gpt-5.6-sol", 2048).build();

        assertEquals("cx/gpt-5.6-sol", options.getModel());
        assertEquals(0.0, options.getTemperature());
        assertEquals(2048, options.getMaxTokens());
        assertNull(options.getMaxCompletionTokens());
    }

    @Test
    void nativeOpenAiStructuredOutputUsesCompletionTokenFieldWithoutTemperature() {
        OpenAiChatOptions options = SpringAiClientRouter.providerStructuredOutputOptions(
                AiGatewayType.OPENAI, "gpt-5.6", 512).build();

        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertEquals(512, options.getMaxCompletionTokens());
    }
}
