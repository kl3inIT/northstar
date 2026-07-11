package com.northstar.api.auth;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@NullMarked
@ConfigurationProperties(prefix = "northstar.auth")
public record AuthProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("") String username,
        @DefaultValue("") String passwordHash) {

    void requireConfigured() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(passwordHash)) {
            throw new IllegalStateException(
                    "northstar.auth.username and northstar.auth.password-hash must be set when auth is enabled");
        }
    }
}
