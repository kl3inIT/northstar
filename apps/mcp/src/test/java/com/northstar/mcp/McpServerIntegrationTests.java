package com.northstar.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Drives the real streamable-http MCP endpoint the way a coding agent does:
 * initialize handshake, tools/list, then a read and a write tool call. Raw
 * JSON-RPC over JDK HttpClient — no MCP client library, so this also pins the
 * wire format (SSE data: frames, Mcp-Session-Id header) that Claude Code
 * depends on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.datasource.hikari.minimum-idle=0",
                "spring.datasource.hikari.maximum-pool-size=3",
                "spring.datasource.hikari.connection-timeout=2000"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class McpServerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void agentCanHandshakeListAndCallTools() throws Exception {
        HttpResponse<String> init = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},
                  "clientInfo":{"name":"test","version":"0.0.1"}}}""");
        assertThat(init.statusCode()).isEqualTo(200);
        assertThat(init.body()).contains("\"name\":\"northstar\"");
        String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();

        post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        String tools = post(session, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").body();
        assertThat(tools).contains("search_knowledge", "get_note", "create_note",
                "today_tasks", "upcoming_tasks", "create_task", "set_task_done",
                "upcoming_events", "create_event", "find_free_slots",
                "list_budgets", "set_budget", "list_savings_goals",
                "save_savings_goal", "contribute_savings_goal",
                "list_subscriptions", "save_subscription", "mark_subscription_paid",
                "delete_subscription");
        assertThat(tools).contains("\"status\"");
        // MCP behavior hints ride along so clients can gate confirmation UX.
        assertThat(tools).contains("\"readOnlyHint\":true", "\"destructiveHint\":false");

        String created = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"create_task","arguments":{
                    "title":"MCP roundtrip probe","dueDate":"2026-07-03"}}}""").body();
        assertThat(created).contains("MCP roundtrip probe").doesNotContain("\"isError\":true");

        String today = post(session, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
                  "name":"today_tasks","arguments":{}}}""").body();
        assertThat(today).contains("MCP roundtrip probe");

        String note = post(session, """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                  "name":"create_note","arguments":{
                    "title":"MCP approved note probe",
                    "folderPath":"Systems/AI Harness",
                    "contentMarkdown":"Approved note from MCP.",
                    "tags":["mcp","probe"],
                    "status":"RESOURCE"}}}""").body();
        assertThat(note).contains("MCP approved note probe", "\\\"status\\\":\\\"RESOURCE\\\"")
                .doesNotContain("\"isError\":true");

        String budget = post(session, """
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
                  "name":"set_budget","arguments":{
                    "month":"2026-07","category":"Cafe","limitAmount":1000000}}} """).body();
        assertThat(budget).contains("Cafe", "1000000").doesNotContain("\"isError\":true");

        String budgets = post(session, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
                  "name":"list_budgets","arguments":{"month":"2026-07"}}} """).body();
        assertThat(budgets).contains("Cafe", "1000000").doesNotContain("\"isError\":true");

        String goal = post(session, """
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
                  "name":"save_savings_goal","arguments":{
                    "id":"","name":"Emergency fund","targetAmount":20000000,
                    "savedAmount":5000000,"targetDate":"2027-07-01",
                    "monthlyContribution":1000000,"version":0}}} """).body();
        assertThat(goal).contains("Emergency fund", "5000000")
                .doesNotContain("\"isError\":true");

        String goals = post(session, """
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{
                  "name":"list_savings_goals","arguments":{}}} """).body();
        assertThat(goals).contains("Emergency fund", "20000000")
                .doesNotContain("\"isError\":true");

        String subscription = post(session, """
                {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{
                  "name":"save_subscription","arguments":{
                    "id":"","version":0,"name":"MCP cloud subscription",
                    "amount":299000,"category":"Hoa don","cycle":"MONTHLY",
                    "nextDueOn":"2026-07-31","active":true}}} """).body();
        assertThat(subscription).contains("MCP cloud subscription", "299000", "\\\"version\\\":0")
                .doesNotContain("\"isError\":true");

        String subscriptions = post(session, """
                {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{
                  "name":"list_subscriptions","arguments":{}}} """).body();
        assertThat(subscriptions).contains("MCP cloud subscription", "2026-07-31", "\\\"version\\\":0")
                .doesNotContain("\"isError\":true");
    }

    @Test
    void agentCanBookAnEventAndPlanAroundIt() throws Exception {
        HttpResponse<String> init = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},
                  "clientInfo":{"name":"test","version":"0.0.1"}}}""");
        String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        String tomorrow = LocalDate.now().plusDays(1).toString();
        String created = post(session, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"create_event","arguments":{
                    "title":"MCP study block","date":"%s",
                    "startTime":"09:00","endTime":"10:30"}}}""".formatted(tomorrow)).body();
        assertThat(created).contains("MCP study block", tomorrow + " 09:00")
                .doesNotContain("\"isError\":true");

        String upcoming = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"upcoming_events","arguments":{"days":2}}}""").body();
        assertThat(upcoming).contains("MCP study block");

        // Gaps hug the booked block: one ends at 09:00, the next starts at 10:30,
        // and no slot boundary falls inside the event.
        String slots = post(session, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
                  "name":"find_free_slots","arguments":{
                    "date":"%s","durationMinutes":60}}}""".formatted(tomorrow)).body();
        assertThat(slots).contains(tomorrow + " 09:00", tomorrow + " 10:30")
                .doesNotContain(tomorrow + " 09:30");

        String rejected = post(session, """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                  "name":"create_event","arguments":{
                    "title":"Bad","date":"%s",
                    "startTime":"09:00","endTime":"08:00"}}}""".formatted(tomorrow)).body();
        assertThat(rejected).contains("\"isError\":true");
    }

    private HttpResponse<String> post(String session, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
