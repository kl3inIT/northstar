package com.northstar.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Test-only application used to verify the MCP delivery module in isolation. */
@SpringBootApplication(scanBasePackages = {"com.northstar.mcp", "com.northstar.core"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
class TestMcpApplication {
}
