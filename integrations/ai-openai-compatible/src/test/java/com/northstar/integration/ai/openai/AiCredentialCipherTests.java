package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCredentialCipherTests {

    @Test
    void encryptsWithRandomNonceAndBindsCiphertextToGatewayId() {
        AiCredentialCipher cipher = new AiCredentialCipher(properties(key()));

        byte[] first = cipher.encrypt("secret-key", "router-a");
        byte[] second = cipher.encrypt("secret-key", "router-a");

        assertFalse(java.util.Arrays.equals(first, second));
        assertEquals("secret-key", cipher.decrypt(first, "router-a"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(first, "router-b"));
    }

    @Test
    void requiresAnExplicitAes256KeyOnlyWhenCredentialsAreUsed() {
        AiCredentialCipher cipher = new AiCredentialCipher(properties(""));

        assertThrows(IllegalArgumentException.class,
                () -> cipher.encrypt("secret-key", "router-a"));
    }

    private static String key() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    private static AiProperties properties(String key) {
        return new AiProperties("openai", Map.of(), AiProperties.Routes.empty(),
                new AiProperties.Catalog(Duration.ofMinutes(5), 64),
                new AiProperties.Credentials(key));
    }
}
