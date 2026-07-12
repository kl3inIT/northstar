package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiTask;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class RoutedOpenAiModelsTests {

    private HttpServer server;
    private AiRouteSettingsService routes;
    private AiGatewayRegistry gateways;
    private AiGatewayDefinition gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        routes = mock(AiRouteSettingsService.class);
        gateways = mock(AiGatewayRegistry.class);
        gateway = new AiGatewayDefinition("runtime", AiGatewayType.OPENAI, "Runtime",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "runtime-key",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                false, Duration.ofSeconds(5), AiGatewaySource.SETTINGS);
        when(gateways.definition("runtime")).thenReturn(gateway);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void embeddingUsesTheSelectedGatewayModelKeyAndFixedDimensions() {
        when(routes.current(AiTask.EMBEDDING)).thenReturn(new AiRoute("runtime", "embed-model"));
        server.createContext("/v1/embeddings", exchange -> {
            assertRequest(exchange, "embed-model", "runtime-key");
            assertTrue(body(exchange).contains("\"dimensions\":1536"));
            respond(exchange, """
                    {"object":"list","data":[{"object":"embedding","embedding":[0.1,0.2,0.3],"index":0}],
                     "model":"embed-model","usage":{"prompt_tokens":1,"total_tokens":1}}
                    """);
        });
        var model = new RoutedOpenAiEmbeddingModel(routes, gateways);

        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, model.embed("xin chao"), 0.0001f);
        assertEquals(1536, model.dimensions());
    }

    @Test
    void transcriptionUsesTheSelectedGatewayModelAndKey() {
        when(routes.current(AiTask.SPEECH_TO_TEXT)).thenReturn(new AiRoute("runtime", "stt-model"));
        server.createContext("/v1/audio/transcriptions", exchange -> {
            assertRequest(exchange, "stt-model", "runtime-key");
            assertTrue(body(exchange).contains("text"));
            respond(exchange, "xin chao", "text/plain");
        });
        var model = new RoutedOpenAiTranscriptionModel(routes, gateways);
        ByteArrayResource audio = new ByteArrayResource(new byte[] {1, 2, 3}) {
            @Override
            public String getFilename() {
                return "capture.webm";
            }
        };

        assertEquals("xin chao", model.transcribe(audio));
    }

    private static void assertRequest(HttpExchange exchange, String model, String key) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        assertEquals("Bearer " + key, exchange.getRequestHeaders().getFirst("Authorization"));
        assertTrue(body(exchange).contains(model));
    }

    private static String body(HttpExchange exchange) throws IOException {
        Object cached = exchange.getAttribute("request-body");
        if (cached instanceof String value) return value;
        String value = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.setAttribute("request-body", value);
        return value;
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, body, "application/json");
    }

    private static void respond(HttpExchange exchange, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
