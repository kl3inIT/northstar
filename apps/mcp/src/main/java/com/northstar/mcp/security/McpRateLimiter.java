package com.northstar.mcp.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Two-layer request-rate limiter for the MCP endpoint: a global bucket (the
 * DDoS backstop) checked first, then a per-client-IP bucket. Global-first so a
 * flood from many IPs still trips the global limit; per-IP so one client cannot
 * starve everyone else.
 *
 * <p>The per-IP map is a bounded access-ordered LRU: it can never exceed
 * {@code maxTrackedIps} regardless of workload, so a spray of distinct source
 * IPs cannot grow it without limit. The keys are the real client IP resolved by
 * the trusted proxy (see the filter) — a client cannot mint new keys by
 * spoofing a header.
 */
@Component
public class McpRateLimiter {

    private final RateLimitProperties props;
    private final TokenBucket global;
    private final Map<String, TokenBucket> perIp;

    McpRateLimiter(RateLimitProperties props) {
        this.props = props;
        this.global = new TokenBucket(props.globalCapacity(), props.globalCapacity(),
                props.globalWindow(), System.nanoTime());
        this.perIp = Collections.synchronizedMap(new BoundedLru<>(Math.max(1, props.maxTrackedIps())));
    }

    /** null = allowed; non-null = rejected, with which limit and the retry-after seconds. */
    public @Nullable Rejection check(String ip) {
        long now = System.nanoTime();
        if (!global.tryConsume(now)) {
            return new Rejection("global", global.retryAfterSeconds(now));
        }
        TokenBucket bucket;
        // LinkedHashMap is not thread-safe and access-order reordering mutates on
        // get, so guard get+put together; the eldest entry is evicted on insert.
        synchronized (perIp) {
            bucket = perIp.get(ip);
            if (bucket == null) {
                bucket = new TokenBucket(props.ipCapacity(), props.ipCapacity(), props.ipWindow(), now);
                perIp.put(ip, bucket);
            }
        }
        if (!bucket.tryConsume(now)) {
            return new Rejection("ip", bucket.retryAfterSeconds(now));
        }
        return null;
    }

    public record Rejection(String limit, long retryAfterSeconds) {
    }

    /** Access-ordered map that evicts the least-recently-used entry past a hard cap. */
    private static final class BoundedLru<K, V> extends LinkedHashMap<K, V> {

        private final int maxSize;

        private BoundedLru(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
