package com.northstar.integration.speech.azure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpeechSdkGatewayTests {

    @Test
    void unscriptedConfigurationUsesHundredMarkPhonemesAndProsody() {
        try (var configuration = SpeechSdkGateway.configuration("", "en-US")) {
            assertThat(configuration.getReferenceText()).isEmpty();
            assertThat(configuration.toJson()).containsIgnoringCase("HundredMark")
                    .containsIgnoringCase("Phoneme")
                    .containsIgnoringCase("Prosody");
        }
    }
}
