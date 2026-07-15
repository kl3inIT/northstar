package com.northstar.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Windows are set to hours/minutes so no refill happens within the test — the
 * bucket math is exercised purely against its initial capacity.
 */
class McpRateLimiterTest {

    private static RateLimitProperties props(long globalCapacity, long ipCapacity) {
        return new RateLimitProperties(true, globalCapacity, Duration.ofMinutes(1),
                ipCapacity, Duration.ofHours(1), 256 * 1024, 50_000);
    }

    @Test
    void allowsUpToPerIpCapacityThenRejects() {
        McpRateLimiter limiter = new McpRateLimiter(props(1000, 3));

        assertThat(limiter.check("1.2.3.4")).isNull();
        assertThat(limiter.check("1.2.3.4")).isNull();
        assertThat(limiter.check("1.2.3.4")).isNull();

        McpRateLimiter.Rejection rejection = limiter.check("1.2.3.4");
        assertThat(rejection).isNotNull();
        assertThat(rejection.limit()).isEqualTo("ip");
        assertThat(rejection.retryAfterSeconds()).isPositive();
    }

    @Test
    void perIpBucketsAreIndependent() {
        McpRateLimiter limiter = new McpRateLimiter(props(1000, 1));

        assertThat(limiter.check("ip-a")).isNull();
        assertThat(limiter.check("ip-a")).isNotNull(); // a exhausted
        assertThat(limiter.check("ip-b")).isNull(); // b unaffected
    }

    @Test
    void rejectedIpRequestsDoNotDrainGlobalBudget() {
        // One IP capped at 1/window against a global budget of 2. Once the
        // attacker's per-IP bucket is empty, its further (rejected) requests must
        // not consume global tokens, so a second IP can still be served.
        McpRateLimiter limiter = new McpRateLimiter(props(2, 1));

        assertThat(limiter.check("attacker")).isNull(); // 1 ip + 1 global consumed

        McpRateLimiter.Rejection first = limiter.check("attacker");
        assertThat(first).isNotNull();
        assertThat(first.limit()).isEqualTo("ip");
        McpRateLimiter.Rejection second = limiter.check("attacker");
        assertThat(second).isNotNull();
        assertThat(second.limit()).isEqualTo("ip"); // still "ip", never falls through to "global"

        assertThat(limiter.check("victim")).isNull(); // global still has a token left
    }

    @Test
    void globalLimitTripsAcrossManyIps() {
        McpRateLimiter limiter = new McpRateLimiter(props(2, 1000));

        assertThat(limiter.check("ip-1")).isNull();
        assertThat(limiter.check("ip-2")).isNull();

        McpRateLimiter.Rejection rejection = limiter.check("ip-3");
        assertThat(rejection).isNotNull();
        assertThat(rejection.limit()).isEqualTo("global");
    }
}
