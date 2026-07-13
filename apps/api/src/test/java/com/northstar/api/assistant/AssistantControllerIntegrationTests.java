package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;

/**
 * Drives the real SSE endpoint the way the web app's useChat does — real
 * Postgres (Flyway V12 chat-memory table), mocked ChatModel. Pins the UI
 * Message Stream protocol frames (start / text-delta / finish / [DONE]) and
 * the JDBC-backed history roundtrip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Off so /conversations deterministically shows the first-message
                // fallback; the titling path is driven synchronously below instead.
                "northstar.assistant.title.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AssistantControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    AiClientRouter ai;

    @org.springframework.beans.factory.annotation.Autowired
    ConversationTitleService titleService;

    @Autowired
    JdbcClient jdbc;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void routeMockModel() {
        AiRoute route = new AiRoute("test", "test-model");
        when(ai.route(any(AiTask.class))).thenReturn(route);
        when(ai.client(any(AiRoute.class))).thenReturn(ChatClient.create(chatModel));
        when(ai.model(any(AiRoute.class))).thenReturn(chatModel);
    }

    @Test
    void turnStreamsProtocolFramesAndPersistsTheConversation() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse reply = new ChatResponse(List.of(new Generation(
                new AssistantMessage("You have nothing due today."))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(reply));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply);

        HttpResponse<String> turn = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"message":"what is due today?","conversationId":"test-convo"}"""))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(turn.statusCode()).isEqualTo(200);
        assertThat(turn.headers().firstValue("x-vercel-ai-ui-message-stream")).hasValue("v1");
        assertThat(turn.headers().firstValue("x-accel-buffering")).hasValue("no");
        assertThat(turn.headers().firstValue("cache-control")).hasValue("no-cache, no-transform");
        assertThat(turn.body())
                .contains("\"type\":\"start\"")
                .contains("\"type\":\"start-step\"")
                .contains("\"type\":\"text-start\"")
                .contains("\"type\":\"text-delta\"")
                .contains("You have nothing due today.")
                .contains("\"type\":\"finish-step\"")
                .contains("\"type\":\"finish\"")
                .contains("\"finishReason\":\"stop\"")
                .contains("[DONE]");

        // The JDBC memory saw both sides of the turn — a reload can rehydrate.
        HttpResponse<String> history = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/history?conversationId=test-convo"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(history.body())
                .contains("what is due today?")
                .contains("You have nothing due today.");

        // The conversation list titles it by the first user message…
        HttpResponse<String> conversations = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/conversations"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(conversations.body()).contains("test-convo").contains("what is due today?");

        // …and deleting it clears the stored transcript.
        HttpResponse<String> deleted = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/conversations/test-convo"))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(deleted.statusCode()).isEqualTo(204);
        HttpResponse<String> after = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/conversations"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(after.body()).doesNotContain("test-convo");
    }

    @Test
    void repeatedIdempotencyKeyDoesNotRunTheTurnTwice() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse reply = new ChatResponse(List.of(new Generation(
                new AssistantMessage("Created once."))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(reply));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply);

        String key = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/chat"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"message":"create this once","conversationId":"idempotent-convo"}"""))
                .build();

        HttpResponse<String> first = http.send(request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> duplicate = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        verify(chatModel, times(1)).stream(any(Prompt.class));
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM spring_ai_chat_memory
                 WHERE conversation_id = 'idempotent-convo' AND type = 'USER'
                """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM northstar_assistant_turn
                 WHERE conversation_id = 'idempotent-convo'
                """).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void historyRehydratesPersistedToolParts() throws Exception {
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO spring_ai_chat_memory (conversation_id, content, type, "timestamp", sequence_id)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params("trace-convo", "find my recent notes", "USER", Timestamp.from(now), 1L)
                .update();
        jdbc.sql("""
                INSERT INTO spring_ai_chat_memory (conversation_id, content, type, "timestamp", sequence_id)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params("trace-convo", "I found these likely recent notes.", "ASSISTANT",
                        Timestamp.from(now.plusMillis(500)), 2L)
                .update();
        jdbc.sql("""
                INSERT INTO northstar_assistant_tool_trace (
                    id, conversation_id, turn_id, sequence_index, tool_call_id, tool_name,
                    state, input_json, output_json, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
                """)
                .params(
                        UUID.randomUUID(),
                        "trace-convo",
                        "turn-1",
                        0,
                        "toolcall-search-1",
                        "search_knowledge",
                        "output-available",
                        "{\"query\":\"recent notes\"}",
                        "{\"results\":[{\"source\":\"note\",\"title\":\"Daily journal\","
                                + "\"slug\":\"daily-journal\",\"url\":\"/notes/daily-journal\"}]}",
                        Timestamp.from(now.plusMillis(100)),
                        Timestamp.from(now.plusMillis(300)))
                .update();

        HttpResponse<String> history = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/history?conversationId=trace-convo"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(history.body())
                .contains("\"parts\"")
                .contains("\"type\":\"tool-search_knowledge\"")
                .contains("\"toolCallId\":\"toolcall-search-1\"")
                .contains("\"state\":\"output-available\"")
                .contains("\"query\":\"recent notes\"")
                .contains("Daily journal")
                .contains("\"type\":\"source-document\"")
                .contains("\"sourceId\":\"/notes/daily-journal\"")
                .contains("\"mediaType\":\"text/markdown\"");
    }

    @Test
    void autoTitleReplacesTheFirstMessageInTheList() throws Exception {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatResponse reply = new ChatResponse(List.of(new Generation(
                new AssistantMessage("IELTS writing plan"))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(reply));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply);

        // A turn seeds the conversation; auto-titling is disabled, so the list still
        // shows the raw first message until we run the titling path explicitly.
        http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/assistant/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"message":"help me plan IELTS writing","conversationId":"title-convo"}"""))
                .build(), HttpResponse.BodyHandlers.ofString());

        // Synchronous so there is no race with the assertions below.
        titleService.ensureTitle("title-convo");

        HttpResponse<String> conversations = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/conversations"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        // COALESCE now prefers the generated title over the first user message.
        assertThat(conversations.body())
                .contains("title-convo")
                .contains("IELTS writing plan")
                .doesNotContain("help me plan IELTS writing");

        // Idempotent: a second run keeps the first title, no duplicate row.
        titleService.ensureTitle("title-convo");
        HttpResponse<String> again = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/assistant/conversations"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(again.body()).contains("IELTS writing plan");
    }
}
