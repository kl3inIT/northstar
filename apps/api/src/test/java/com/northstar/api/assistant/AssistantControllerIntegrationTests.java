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
        properties = "spring.ai.openai.api-key=test-key")
@Testcontainers
class AssistantControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

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
    }
}
