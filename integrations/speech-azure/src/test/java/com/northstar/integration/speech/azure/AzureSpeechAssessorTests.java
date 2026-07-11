package com.northstar.integration.speech.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AzureSpeechAssessorTests {

    @Test
    void exposesStableProviderIdentityAndPassesHeaderFreePcmToGateway() throws IOException {
        CapturingGateway gateway = new CapturingGateway(captured());
        AzureSpeechAssessor assessor = new AzureSpeechAssessor(
                gateway, new AzureSpeechResponseParser(new ObjectMapper()));

        var result = assessor.assessReading(wav(32_000),
                "Good morning.", "en-US");

        assertThat(assessor.providerId()).isEqualTo("azure");
        assertThat(assessor.providerRevision()).isEqualTo("speech-sdk-1.50.0");
        assertThat(gateway.pcm).hasSize(32_000);
        assertThat(gateway.referenceText).isEqualTo("Good morning.");
        assertThat(gateway.locale).isEqualTo("en-US");
        assertThat(gateway.continuous).isFalse();
        assertThat(result.words()).hasSize(2);
    }

    private static String captured() throws IOException {
        try (var input = AzureSpeechAssessorTests.class.getResourceAsStream("/azure-reading-response.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] wav(int pcmBytes) {
        ByteBuffer result = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
        result.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcmBytes);
        result.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        result.putShort((short) 1).putShort((short) 1).putInt(16_000).putInt(32_000);
        result.putShort((short) 2).putShort((short) 16);
        result.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcmBytes).put(new byte[pcmBytes]);
        return result.array();
    }

    private static final class CapturingGateway implements AzureSpeechGateway {
        private final String response;
        private byte[] pcm;
        private String referenceText;
        private String locale;
        private boolean continuous;

        private CapturingGateway(String response) {
            this.response = response;
        }

        @Override
        public List<String> assess(byte[] pcm, String referenceText, String locale, boolean continuous) {
            this.pcm = pcm;
            this.referenceText = referenceText;
            this.locale = locale;
            this.continuous = continuous;
            return List.of(response);
        }
    }
}
