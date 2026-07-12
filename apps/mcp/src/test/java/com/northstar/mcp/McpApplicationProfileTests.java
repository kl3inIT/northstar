package com.northstar.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class McpApplicationProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void productionProfileHasItsOwnPoolAndProxyBoundary() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                    .isEqualTo("northstar-mcp");
            assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                    .isEqualTo(4);
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
            assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health");
        });
    }

    @Test
    void localProfileDoesNotEnableProductionProxyHandling() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("server.forward-headers-strategy")).isNull();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health,info");
        });
    }
}
