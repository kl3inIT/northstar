package com.northstar.api.auth;

import java.time.Duration;
import java.util.Base64;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@NullMarked
@ConfigurationProperties(prefix = "northstar.auth.mobile")
public record MobileAuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("northstar-api") String issuer,
        @DefaultValue("northstar-mobile") String audience,
        @DefaultValue("") String jwtSecret,
        @DefaultValue("15m") Duration accessTtl,
        @DefaultValue("30d") Duration refreshTtl) {

    byte[] decodedSecret() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException(
                    "northstar.auth.mobile.jwt-secret must be set when mobile auth is enabled");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(jwtSecret);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("northstar.auth.mobile.jwt-secret must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "northstar.auth.mobile.jwt-secret must decode to at least 32 bytes for HS256");
        }
        return secret;
    }
}
