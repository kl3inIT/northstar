package com.northstar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * REST + web entry point. The domain and Spring Modulith modules live in
 * {@code :core} ({@code com.northstar.core}); this app scans them explicitly and
 * owns running Flyway migrations at startup.
 */
@SpringBootApplication(scanBasePackages = {"com.northstar.api", "com.northstar.core"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
public class NorthstarApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthstarApiApplication.class, args);
    }
}
