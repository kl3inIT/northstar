package com.northstar.worker;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Test-only application used to verify the jobs module in isolation. */
@SpringBootApplication(scanBasePackages = {"com.northstar.worker", "com.northstar.core", "com.northstar.integration"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
class TestWorkerApplication {
}
