package com.northstar.mcp.security;

import java.time.Duration;

/**
 * A classic token bucket: starts full, each request takes one token, tokens
 * refill continuously at {@code refillTokens / refillPeriod}. Allows short
 * bursts up to {@code capacity} while enforcing the average rate. Thread-safe
 * via method synchronization (contention is trivial — one short critical
 * section per request); timing uses {@link System#nanoTime()} (monotonic).
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(long capacity, long refillTokens, Duration refillPeriod, long nowNanos) {
        this.capacity = capacity;
        this.refillPerNano = (double) refillTokens / refillPeriod.toNanos();
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds until at least one token is available; 0 if one is available now. */
    synchronized long retryAfterSeconds(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            return 0;
        }
        double nanosNeeded = (1.0 - tokens) / refillPerNano;
        return Math.max(1, (long) Math.ceil(nanosNeeded / 1_000_000_000.0));
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefillNanos = nowNanos;
        }
    }
}
