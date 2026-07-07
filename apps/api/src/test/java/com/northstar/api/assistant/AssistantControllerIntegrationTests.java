package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
        properties = {"spring.ai.openai.api-key=test-key",
                // Off so /conversations deterministically shows the first-message
                // fallback; the titling path is driven synchronously below instead.
                "northstar.assistant.title.enabled=false"})
@Testcontainers
class AssistantControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

    @org.springframework.beans.factory.annotation.Autowired
    ConversationTitleService titleService;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

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
        assertThat(turn.body())
                .contains("\"type\":\"start\"")
                .contains("\"type\":\"text-start\"")
                .contains("\"type\":\"text-delta\"")
                .contains("You have nothing due today.")
                .contains("\"type\":\"finish\"")
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
