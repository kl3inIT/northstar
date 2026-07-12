package com.northstar.api.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiRoute;
import com.northstar.core.speech.SpeechAudio;
import com.northstar.integration.ai.openai.OpenAiCompatibleTextToSpeechGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class SpeechControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    OpenAiCompatibleTextToSpeechGateway speechGateway;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void explicitSynthesisPersistsAndReusesTheAudio() throws Exception {
        byte[] mp3 = {0x49, 0x44, 0x33, 1, 2, 3};
        when(speechGateway.synthesize(any(AiRoute.class), eq("Read this once"), eq("auto")))
                .thenReturn(new SpeechAudio(mp3, "audio/mpeg", "mp3"));

        HttpResponse<String> first = synthesize("Read this once");
        HttpResponse<String> second = synthesize("Read this once");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        String firstId = jsonString(first.body(), "id");
        String secondId = jsonString(second.body(), "id");
        assertThat(first.body()).contains("\"cacheHit\":false");
        assertThat(second.body()).contains("\"cacheHit\":true");
        assertThat(secondId).isEqualTo(firstId);
        verify(speechGateway, times(1)).synthesize(any(AiRoute.class), eq("Read this once"), eq("auto"));

        HttpResponse<byte[]> audio = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + jsonString(first.body(), "audioUrl")))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(audio.statusCode()).isEqualTo(200);
        assertThat(audio.headers().firstValue("content-type")).hasValue("audio/mpeg");
        assertThat(audio.body()).isEqualTo(mp3);
    }

    @Test
    void oversizedTextIsRejectedWithoutSpendingProviderQuota() throws Exception {
        HttpResponse<String> response = synthesize("x".repeat(4097));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void configuredLanguageIsUsedWhenSynthesisDoesNotOverrideIt() throws Exception {
        byte[] mp3 = {0x49, 0x44, 0x33, 4, 5, 6};
        when(speechGateway.synthesize(any(AiRoute.class), eq("Xin chao"), eq("vi-vn")))
                .thenReturn(new SpeechAudio(mp3, "audio/mpeg", "mp3"));
        jdbc.sql("""
                INSERT INTO ai_route_setting(task, gateway_id, model_id, options)
                VALUES ('TEXT_TO_SPEECH', 'openai', 'openai/gpt-4o-mini-tts/alloy',
                        CAST(:options AS jsonb))
                ON CONFLICT (task) DO UPDATE SET options = EXCLUDED.options
                """).param("options", "{\"language\":\"vi-VN\"}").update();
        HttpResponse<String> response;
        try {
            response = synthesize("Xin chao");
        } finally {
            jdbc.sql("DELETE FROM ai_route_setting WHERE task = 'TEXT_TO_SPEECH'").update();
        }

        assertThat(response.statusCode()).isEqualTo(200);
        verify(speechGateway).synthesize(any(AiRoute.class), eq("Xin chao"), eq("vi-vn"));
    }

    private HttpResponse<String> synthesize(String text) throws Exception {
        String body = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/speech/synthesize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonString(String body, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"")
                .matcher(body);
        assertThat(matcher.find()).as("JSON field %s in %s", field, body).isTrue();
        return matcher.group(1);
    }
}
