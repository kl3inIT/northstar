package com.northstar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Single backend entry point. REST delivery, MCP transport, migrations, and
 * background jobs remain in separate packages but share one Spring context,
 * datasource, and lifecycle.
 */
@SpringBootApplication(scanBasePackages = {
        "com.northstar.api",
        "com.northstar.mcp",
        "com.northstar.worker",
        "com.northstar.core",
        "com.northstar.integration"
})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
public class NorthstarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthstarServerApplication.class, args);
    }
}
