package com.northstar.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Model Context Protocol server. Exposes the {@code northstar_*} tools (capture,
 * search context, get project context, end session summary) to coding agents.
 * Reuses the same {@code :core} domain and schema as the api; it does not run
 * migrations (the api owns those).
 */
@SpringBootApplication(scanBasePackages = {"com.northstar.mcp", "com.northstar.core"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
public class NorthstarMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthstarMcpApplication.class, args);
    }
}
