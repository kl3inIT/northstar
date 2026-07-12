package com.northstar.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class WorkerApplicationProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void productionProfileBudgetsConnectionsAndShutdownTime() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                    .isEqualTo("northstar-worker");
            assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                    .isEqualTo(6);
            assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                    .isEqualTo("130s");
            assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
            assertThat(environment.getProperty(
                    "logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor"))
                    .isEqualTo("OFF");
        });
    }

    @Test
    void localProfileEnablesAiDiagnostics() {
        runner.withPropertyValues("spring.profiles.active=local").run(context ->
                assertThat(context.getEnvironment().getProperty(
                        "logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor"))
                        .isEqualTo("DEBUG"));
    }
}
