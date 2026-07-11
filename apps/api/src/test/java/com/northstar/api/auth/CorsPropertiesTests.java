package com.northstar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorsPropertiesTests {

    @Test
    void parsesTrimsAndDeduplicatesExactOrigins() {
        CorsProperties properties = new CorsProperties(
                " http://127.0.0.1:7357,https://mobile-preview.example.com,http://127.0.0.1:7357 ");

        assertThat(properties.origins())
                .containsExactly("http://127.0.0.1:7357", "https://mobile-preview.example.com");
    }

    @Test
    void rejectsValuesThatAreNotExactHttpOrigins() {
        assertThatThrownBy(() -> new CorsProperties("*").origins())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid value: *");
        assertThatThrownBy(() -> new CorsProperties("https://mobile-preview.example.com/path").origins())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without paths");
        assertThatThrownBy(() -> new CorsProperties("file://mobile-preview").origins())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact HTTP(S) origins");
    }
}
