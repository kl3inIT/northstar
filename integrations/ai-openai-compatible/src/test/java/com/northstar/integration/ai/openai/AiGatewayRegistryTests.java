package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiGatewaySettingRepository;
import com.northstar.core.ai.AiGatewayType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiGatewayRegistryTests {

    @Test
    void deploymentCredentialIsTheEffectiveFallbackUntilASettingsOverlayExists() {
        AiProperties.Gateway deployment = new AiProperties.Gateway(
                AiGatewayType.OPENAI, "OpenAI", "https://api.openai.com/v1", "environment-key",
                List.of("chat-model"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                false, Duration.ofSeconds(60));
        AiProperties properties = new AiProperties("openai", Map.of("openai", deployment), null, null, null);
        AiGatewaySettingRepository settings = mock(AiGatewaySettingRepository.class);
        when(settings.findAll()).thenReturn(List.of());
        when(settings.findById("openai")).thenReturn(Optional.empty());
        AiGatewayRegistry registry = new AiGatewayRegistry(properties, settings, mock(AiCredentialCipher.class));

        AiGatewayDescriptor descriptor = registry.descriptors().getFirst();

        assertEquals("openai", descriptor.id());
        assertTrue(descriptor.configured());
        assertEquals(AiCredentialSource.ENVIRONMENT, descriptor.credentialSource());
        assertTrue(descriptor.deploymentBacked());
        assertFalse(descriptor.overridden());
        assertEquals("environment-key", registry.require("openai").apiKey());
    }
}
