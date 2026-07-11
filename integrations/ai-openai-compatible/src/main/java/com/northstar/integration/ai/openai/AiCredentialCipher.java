package com.northstar.integration.ai.openai;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class AiCredentialCipher {

    private static final int VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private final String keyBase64;
    private final SecureRandom random = new SecureRandom();

    AiCredentialCipher(AiProperties properties) {
        keyBase64 = properties.credentials().encryptionKeyBase64();
    }

    byte[] encrypt(String plaintext, String gatewayId) {
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(gatewayId.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(bytes);
            return ByteBuffer.allocate(Integer.BYTES + NONCE_BYTES + ciphertext.length)
                    .putInt(VERSION)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt AI gateway credential", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    String decrypt(byte[] envelope, String gatewayId) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(envelope);
            int version = buffer.getInt();
            if (version != VERSION || buffer.remaining() <= NONCE_BYTES) {
                throw new IllegalStateException("Unsupported AI credential envelope");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(gatewayId.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(ciphertext);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt AI gateway credential", exception);
        }
    }

    private SecretKey key() {
        if (keyBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "Runtime AI gateways require NORTHSTAR_AI_CREDENTIAL_KEY");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "NORTHSTAR_AI_CREDENTIAL_KEY must be base64-encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                    "NORTHSTAR_AI_CREDENTIAL_KEY must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
