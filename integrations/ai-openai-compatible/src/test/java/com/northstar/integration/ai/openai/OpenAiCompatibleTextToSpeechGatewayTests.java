package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.speech.SpeechAudio;
import com.northstar.core.speech.SpeechTarget;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleTextToSpeechGatewayTests {

    @Test
    void directOpenAiRequestSeparatesModelAndVoice() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (TestServer server = TestServer.start("/v1/audio/speech", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "audio/mpeg", new byte[] {0x49, 0x44, 0x33});
        })) {
            AiGatewayConnection connection = connection(server, AiGatewayType.OPENAI);
            var gateway = new OpenAiCompatibleTextToSpeechGateway(id -> connection, RestClient.builder());

            SpeechAudio audio = gateway.synthesize(
                    new AiRoute("openai", "openai/gpt-4o-mini-tts/marin"), "Hello", "auto");

            assertEquals("mp3", audio.format());
            assertEquals("Bearer secret", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"gpt-4o-mini-tts\""));
            assertTrue(requestBody.get().contains("\"voice\":\"marin\""));
            assertTrue(requestBody.get().contains("\"input\":\"Hello\""));
        }
    }

    @Test
    void nineRouterDiscoversCapabilitySpecificTargets() throws Exception {
        try (TestServer server = TestServer.start("/v1/models/tts", exchange -> respond(exchange,
                "application/json", """
                        {"object":"list","data":[
                          {"id":"edge-tts/vi-VN-HoaiMyNeural"},
                          {"id":"google-tts/vi"}
                        ]}
                        """.getBytes(StandardCharsets.UTF_8)))) {
            AiGatewayConnection connection = connection(server, AiGatewayType.NINE_ROUTER);
            var gateway = new OpenAiCompatibleTextToSpeechGateway(id -> connection, RestClient.builder());

            List<SpeechTarget> targets = gateway.targets("nine-router");

            assertEquals(List.of("edge-tts/vi-VN-HoaiMyNeural", "google-tts/vi"),
                    targets.stream().map(SpeechTarget::id).toList());
        }
    }

    @Test
    void providerFailureBecomesSpeechSynthesisFailure() throws Exception {
        try (TestServer server = TestServer.start("/v1/audio/speech", exchange -> {
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        })) {
            AiGatewayConnection connection = connection(server, AiGatewayType.OPENAI);
            var gateway = new OpenAiCompatibleTextToSpeechGateway(id -> connection, RestClient.builder());

            assertThrows(com.northstar.core.speech.SpeechSynthesisException.class,
                    () -> gateway.synthesize(
                            new AiRoute("openai", "openai/gpt-4o-mini-tts/alloy"),
                            "Hello", "auto"));
        }
    }

    private static AiGatewayConnection connection(TestServer server, AiGatewayType type) {
        return new AiGatewayConnection(type == AiGatewayType.OPENAI ? "openai" : "nine-router",
                type.name(), type, server.baseUrl(), "secret", Duration.ofSeconds(5));
    }

    private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(HttpServer server) {
            this.server = server;
        }

        static TestServer start(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, handler);
            server.start();
            return new TestServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
