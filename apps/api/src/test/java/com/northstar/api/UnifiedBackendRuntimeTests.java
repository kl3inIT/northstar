package com.northstar.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** End-to-end contracts that exist only when API, MCP, and jobs share one runtime. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "northstar.auth.enabled=true",
                "northstar.auth.username=test-user",
                "northstar.auth.password-hash={noop}test-password",
                "northstar.mcp.auth.token=test-mcp-token",
                "spring.ai.mcp.server.name=northstar",
                "spring.ai.mcp.server.version=0.1.0",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.datasource.hikari.minimum-idle=0",
                "spring.datasource.hikari.maximum-pool-size=3",
                "spring.datasource.hikari.connection-timeout=2000"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class UnifiedBackendRuntimeTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    ScheduledAnnotationBeanPostProcessor scheduledTasks;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void unifiedRuntimeOwnsSingletonInfrastructureAndJobs() throws Exception {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(Flyway.class)).hasSize(1);
        assertThat(context.getBeansOfType(Class.forName("com.northstar.worker.search.SearchIndexingWorker")))
                .hasSize(1);
        assertThat(context.getBeansOfType(Class.forName("com.northstar.worker.automation.AutomationSchedulerCoordinator")))
                .hasSize(1);
        assertThat(context.getBeansOfType(Class.forName("com.northstar.worker.finance.SubscriptionWorker")))
                .hasSize(1);
        assertThat(context.getBeanNamesForType(
                Class.forName("com.github.kagkarlsson.scheduler.task.Task")))
                .containsExactlyInAnyOrder(
                        "automationReconcilerTask", "recurringAutomationTask", "manualAutomationTask");
        var workerScheduledTasks = scheduledTasks.getScheduledTasks().stream()
                .map(Object::toString)
                .filter(task -> task.startsWith("com.northstar.worker."))
                .toList();
        assertThat(workerScheduledTasks).containsExactlyInAnyOrder(
                "com.northstar.worker.finance.SubscriptionWorker.sweep",
                "com.northstar.worker.search.SearchIndexingWorker.reindex");
        assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.protocol"))
                .isEqualTo("STREAMABLE");
        assertThat(context.containsBean("webMvcStreamableServerRouterFunction")).isTrue();
    }

    @Test
    void tokenProtectedMcpEndpointCoexistsWithAuthenticatedApi() throws Exception {
        HttpResponse<String> protectedApi = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notes"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},
                  "clientInfo":{"name":"unified-runtime-test","version":"0.0.1"}}}""";
        HttpResponse<String> anonymousMcp = post(null, initialize, false);
        HttpResponse<String> response = post(null, initialize, true);

        assertThat(protectedApi.statusCode()).isEqualTo(401);
        assertThat(anonymousMcp.statusCode()).isEqualTo(401);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"northstar\"");
        String session = response.headers().firstValue("Mcp-Session-Id").orElseThrow();

        HttpResponse<String> initialized = post(session,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        HttpResponse<String> tools = post(session,
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        HttpResponse<String> readOnlyTool = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"today_tasks","arguments":{}}}""");

        assertThat(initialized.statusCode()).isBetween(200, 299);
        assertThat(initialized.body()).doesNotContain("\"error\"");
        assertThat(tools.statusCode()).isEqualTo(200);
        assertThat(tools.body())
                .contains("\"result\"", "search_knowledge", "\"readOnlyHint\":true")
                .doesNotContain("\"error\"");
        assertThat(readOnlyTool.statusCode()).isEqualTo(200);
        assertThat(readOnlyTool.body())
                .contains("\"result\"")
                .doesNotContain("\"error\"", "\"isError\":true");
    }

    private HttpResponse<String> post(String session, String body) throws Exception {
        return post(session, body, true);
    }

    private HttpResponse<String> post(String session, String body, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (authenticated) {
            request.header("X-Northstar-MCP-Token", "test-mcp-token");
        }
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
