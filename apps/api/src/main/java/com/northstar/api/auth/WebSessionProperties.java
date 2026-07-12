package com.northstar.api.auth;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@NullMarked
@ConfigurationProperties(prefix = "northstar.auth.web-session")
record WebSessionProperties(
        @DefaultValue("30d") Duration cookieMaxAge,
        @DefaultValue("true") boolean httpOnly,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("true") boolean secure) {

    int cookieMaxAgeSeconds() {
        long seconds = cookieMaxAge.toSeconds();
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "northstar.auth.web-session.cookie-max-age must be between 1 second and "
                            + Integer.MAX_VALUE + " seconds");
        }
        return Math.toIntExact(seconds);
    }
}
