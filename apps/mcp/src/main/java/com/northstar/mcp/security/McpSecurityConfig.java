package com.northstar.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires the MCP abuse guard. The filter is scoped to the MCP endpoint only
 * ({@code /mcp}, Spring AI's streamable-http default), so the actuator health
 * check the container/deploy relies on is never rate-limited. Disable the whole
 * thing with {@code northstar.mcp.rate-limit.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class McpSecurityConfig {

    @Bean
    @ConditionalOnProperty(prefix = "northstar.mcp.rate-limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<McpRateLimitFilter> mcpRateLimitFilter(McpRateLimiter limiter,
            RateLimitProperties props) {
        FilterRegistrationBean<McpRateLimitFilter> registration = new FilterRegistrationBean<>(
                new McpRateLimitFilter(limiter, props));
        registration.addUrlPatterns("/mcp", "/mcp/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("mcpRateLimitFilter");
        return registration;
    }
}
