package com.northstar.mcp.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Two-layer request-rate limiter for the MCP endpoint: a global bucket (the
 * DDoS backstop) checked first, then a per-client-IP bucket. Global-first so a
 * flood from many IPs still trips the global limit; per-IP so one client cannot
 * starve everyone else. Per-IP buckets are evicted once idle to bound memory.
 */
@Component
public class McpRateLimiter {

    private final RateLimitProperties props;
    private final TokenBucket global;
    private final ConcurrentHashMap<String, TokenBucket> perIp = new ConcurrentHashMap<>();

    McpRateLimiter(RateLimitProperties props) {
        this.props = props;
        this.global = new TokenBucket(props.getGlobalCapacity(), props.getGlobalCapacity(),
                props.getGlobalWindow(), System.nanoTime());
    }

    /** null = allowed; non-null = rejected, with which limit and the retry-after seconds. */
    public @Nullable Rejection check(String ip) {
        long now = System.nanoTime();
        if (!global.tryConsume(now)) {
            return new Rejection("global", global.retryAfterSeconds(now));
        }
        TokenBucket bucket = perIp.computeIfAbsent(ip, k -> new TokenBucket(
                props.getIpCapacity(), props.getIpCapacity(), props.getIpWindow(), now));
        if (perIp.size() > props.getMaxTrackedIps()) {
            evictIdle(now);
        }
        if (!bucket.tryConsume(now)) {
            return new Rejection("ip", bucket.retryAfterSeconds(now));
        }
        return null;
    }

    /** Drop buckets that have refilled to full since their last use — they carry no state worth keeping. */
    private void evictIdle(long nowNanos) {
        for (Iterator<Map.Entry<String, TokenBucket>> it = perIp.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isIdle(nowNanos)) {
                it.remove();
            }
        }
    }

    public record Rejection(String limit, long retryAfterSeconds) {
    }
}
