package com.northstar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class McpAuthPropertiesTests {

    @Test
    void rejectsMissingTokenWhenAuthenticationIsEnabled() {
        assertThatThrownBy(() -> new McpAuthProperties("").requireConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("northstar.mcp.auth.token");
    }

    @Test
    void matchesOnlyTheConfiguredToken() {
        McpAuthProperties properties = new McpAuthProperties("expected-token");

        assertThat(properties.matches("expected-token")).isTrue();
        assertThat(properties.matches("wrong-token")).isFalse();
        assertThat(properties.matches("")).isFalse();
    }
}
