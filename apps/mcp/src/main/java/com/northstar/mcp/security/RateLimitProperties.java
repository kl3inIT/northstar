package com.northstar.mcp.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse limits for the PUBLIC, no-auth MCP surface. The MCP tools write to real
 * personal data, so a scanner or an agent stuck in a retry loop must not be able
 * to hammer the endpoint; rate limiting is orthogonal to auth and caps the blast
 * radius until proper auth exists. Defaults mirror jmix-mcp-docs (a production
 * no-auth MCP): per-IP 300/hour, global 800/minute.
 */
@ConfigurationProperties(prefix = "northstar.mcp.rate-limit")
public class RateLimitProperties {

    /** Master switch — off disables the filter entirely (e.g. local dev). */
    private boolean enabled = true;

    /** Global DDoS backstop across all clients. */
    private long globalCapacity = 800;
    private Duration globalWindow = Duration.ofMinutes(1);

    /** Per-client-IP average rate (with burst up to the capacity). */
    private long ipCapacity = 300;
    private Duration ipWindow = Duration.ofHours(1);

    /** Reject a request body larger than this outright (bytes). */
    private long maxBodyBytes = 256 * 1024;

    /** Cap the per-IP bucket map so a spray of IPs can't grow memory unbounded. */
    private int maxTrackedIps = 50_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getGlobalCapacity() {
        return globalCapacity;
    }

    public void setGlobalCapacity(long globalCapacity) {
        this.globalCapacity = globalCapacity;
    }

    public Duration getGlobalWindow() {
        return globalWindow;
    }

    public void setGlobalWindow(Duration globalWindow) {
        this.globalWindow = globalWindow;
    }

    public long getIpCapacity() {
        return ipCapacity;
    }

    public void setIpCapacity(long ipCapacity) {
        this.ipCapacity = ipCapacity;
    }

    public Duration getIpWindow() {
        return ipWindow;
    }

    public void setIpWindow(Duration ipWindow) {
        this.ipWindow = ipWindow;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getMaxTrackedIps() {
        return maxTrackedIps;
    }

    public void setMaxTrackedIps(int maxTrackedIps) {
        this.maxTrackedIps = maxTrackedIps;
    }
}
