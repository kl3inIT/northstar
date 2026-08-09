package com.northstar.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.UrlResource;

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
                    .isEqualTo("northstar-server");
            assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                    .isEqualTo(10);
            assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                    .isEqualTo("130s");
            assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
            assertThat(environment.getProperty(
                    "spring.task.scheduling.shutdown.await-termination", Boolean.class)).isTrue();
            assertThat(environment.getProperty(
                    "spring.task.scheduling.shutdown.await-termination-period")).isEqualTo("120s");
            assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
            assertThat(environment.getProperty("spring.session.jdbc.initialize-schema"))
                    .isEqualTo("never");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health");
        });
    }

    @Test
    void canonicalMainConfigOwnsMcpJobsAndSharedCredentialPlaceholders() throws IOException {
        URL mainConfig = Collections.list(getClass().getClassLoader().getResources("application.yml")).stream()
                .filter(url -> url.toExternalForm().contains("/resources/main/")
                        || url.toExternalForm().contains("/src/main/"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("main application.yml is not on the test classpath"));
        var properties = new YamlPropertySourceLoader()
                .load("northstar-main", new UrlResource(mainConfig))
                .getFirst();

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("northstar-server");
        assertThat(properties.getProperty("spring.ai.mcp.server.protocol")).isEqualTo("STREAMABLE");
        assertThat(properties.getProperty("db-scheduler.threads")).isEqualTo(4);
        assertThat(properties.getProperty("db-scheduler.shutdown-max-wait")).isEqualTo("1m");
        assertThat(properties.getProperty("northstar.mcp.auth.token"))
                .isEqualTo("${NORTHSTAR_MCP_TOKEN:}");
        assertThat(properties.getProperty("northstar.brief.firecrawl.api-key"))
                .isEqualTo("${FIRECRAWL_API_KEY:}");
        assertThat(properties.getProperty("northstar.web.firecrawl.api-key"))
                .isEqualTo("${FIRECRAWL_API_KEY:}");
    }
}
