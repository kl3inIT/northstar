package com.northstar.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class ApiApplicationProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void localProfileEnablesOnlyDeveloperConveniences() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("northstar.auth.web-session.secure", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.session.timeout")).isEqualTo("30d");
            assertThat(environment.getProperty("northstar.auth.web-session.cookie-max-age")).isEqualTo("30d");
            assertThat(environment.getProperty(
                    "logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor"))
                    .isEqualTo("DEBUG");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .contains("modulith");
        });
    }

    @Test
    void productionProfileHasBoundedPoolAndHardenedObservability() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                    .isEqualTo("northstar-api");
            assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                    .isEqualTo(8);
            assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
            assertThat(environment.getProperty("spring.session.jdbc.initialize-schema"))
                    .isEqualTo("never");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health");
        });
    }
}
