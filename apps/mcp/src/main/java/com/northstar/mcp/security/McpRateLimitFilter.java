package com.northstar.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the public no-auth MCP endpoint: rejects oversized bodies (413) and
 * rate-limited callers (429 + Retry-After), and logs every call (method, path,
 * client IP, status, duration) for audit — the "who hit my no-auth surface"
 * trail jmix-mcp-docs keeps. Registered only on the MCP paths, so the container
 * health check and other routes are untouched.
 */
@NullMarked
public class McpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpRateLimitFilter.class);

    private final McpRateLimiter limiter;
    private final RateLimitProperties props;

    public McpRateLimitFilter(McpRateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String ip = clientIp(request);

        long length = request.getContentLengthLong();
        if (length > props.getMaxBodyBytes()) {
            log.warn("MCP {} {} ip={} rejected: body {}B over cap {}B",
                    request.getMethod(), request.getRequestURI(), ip, length, props.getMaxBodyBytes());
            writeError(response, HttpStatus.CONTENT_TOO_LARGE, "request_too_large", 0);
            return;
        }

        McpRateLimiter.Rejection rejection = limiter.check(ip);
        if (rejection != null) {
            log.warn("MCP {} {} ip={} rate-limited ({} limit), retry after {}s",
                    request.getMethod(), request.getRequestURI(), ip, rejection.limit(),
                    rejection.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(rejection.retryAfterSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limited", rejection.retryAfterSeconds());
            return;
        }

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("MCP {} {} ip={} status={} {}ms",
                    request.getMethod(), request.getRequestURI(), ip, response.getStatus(), ms);
        }
    }

    /** Real client IP behind the NPM reverse proxy — remoteAddr would be NPM's container IP. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String error,
            long retryAfterSeconds) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = retryAfterSeconds > 0
                ? "{\"error\":\"%s\",\"retryAfterSeconds\":%d}".formatted(error, retryAfterSeconds)
                : "{\"error\":\"%s\"}".formatted(error);
        response.getWriter().write(body);
    }
}
