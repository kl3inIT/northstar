package com.northstar.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
        properties = "spring.flyway.enabled=true")
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
                "upcoming_events", "create_event", "find_free_slots");
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
