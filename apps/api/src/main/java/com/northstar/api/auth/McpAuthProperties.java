package com.northstar.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/** Shared-secret authentication for the HTTP MCP transport. */
@NullMarked
@ConfigurationProperties(prefix = "northstar.mcp.auth")
public record McpAuthProperties(@DefaultValue("") String token) {

    void requireConfigured() {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException(
                    "northstar.mcp.auth.token must be set when web authentication is enabled");
        }
    }

    boolean matches(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
