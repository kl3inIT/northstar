package com.northstar.api.auth;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@NullMarked
@ConfigurationProperties(prefix = "northstar.security.cors")
public record CorsProperties(@DefaultValue("") String allowedOrigins) {

    List<String> origins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(CorsProperties::requireValidOrigin)
                .distinct()
                .toList();
    }

    private static String requireValidOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        }
        catch (IllegalArgumentException exception) {
            throw invalidOrigin(origin, exception);
        }

        boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        boolean originOnly = StringUtils.hasText(uri.getHost())
                && uri.getUserInfo() == null
                && !StringUtils.hasText(uri.getPath())
                && uri.getQuery() == null
                && uri.getFragment() == null;
        if (!http || !originOnly) {
            throw invalidOrigin(origin);
        }
        return origin;
    }

    private static IllegalStateException invalidOrigin(String origin) {
        return new IllegalStateException(invalidOriginMessage(origin));
    }

    private static IllegalStateException invalidOrigin(String origin, Exception cause) {
        return new IllegalStateException(invalidOriginMessage(origin), cause);
    }

    private static String invalidOriginMessage(String origin) {
        return "northstar.security.cors.allowed-origins must contain exact HTTP(S) origins "
                + "without paths, queries, fragments, credentials, or wildcards; invalid value: " + origin;
    }
}
