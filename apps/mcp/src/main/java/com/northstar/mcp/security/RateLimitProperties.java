package com.northstar.mcp.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Immutable abuse limits for the public, no-auth MCP surface. */
@ConfigurationProperties(prefix = "northstar.mcp.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("800") long globalCapacity,
        @DefaultValue("1m") Duration globalWindow,
        @DefaultValue("300") long ipCapacity,
        @DefaultValue("1h") Duration ipWindow,
        @DefaultValue("262144") long maxBodyBytes,
        @DefaultValue("50000") int maxTrackedIps) {
}
