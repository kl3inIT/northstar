package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.GeneratedImage;
import com.northstar.core.ai.ImageGenerationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleImageGenerationGatewayTests {

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3
    };

    @Test
    void directOpenAiUsesImageApiAndDecodesBase64() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        try (TestServer server = TestServer.start("/v1/images/generations", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "application/json", ("{\"data\":[{\"b64_json\":\""
                    + Base64.getEncoder().encodeToString(PNG) + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8));
        })) {
            var gateway = gateway(server, AiGatewayType.OPENAI);

            GeneratedImage image = gateway.generate(new AiRoute("openai", "gpt-image-2"),
                    "A mnemonic illustration");

            assertEquals("image/png", image.mediaType());
            assertTrue(body.get().contains("\"model\":\"gpt-image-2\""));
            assertTrue(body.get().contains("\"quality\":\"low\""));
        }
    }

    @Test
    void nineRouterRequestsBinaryFromSelectedTarget() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> protocol = new AtomicReference<>();
        try (TestServer server = TestServer.start("/v1/images/generations", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            protocol.set(exchange.getProtocol());
            respond(exchange, "image/png", PNG);
        })) {
            var gateway = gateway(server, AiGatewayType.NINE_ROUTER);

            gateway.generate(new AiRoute("nine-router", "auto:image"), "A visual cue");

            assertEquals("response_format=binary", query.get());
            assertTrue(body.get().contains("\"model\":\"auto:image\""));
            assertEquals("HTTP/1.1", protocol.get());
        }
    }

    @Test
    void rejectsNonImageProviderOutput() throws Exception {
        try (TestServer server = TestServer.start("/v1/images/generations", exchange ->
                respond(exchange, "image/png", "not an image".getBytes(StandardCharsets.UTF_8)))) {
            var gateway = gateway(server, AiGatewayType.NINE_ROUTER);

            assertThrows(ImageGenerationException.class,
                    () -> gateway.generate(new AiRoute("nine-router", "auto:image"), "cue"));
        }
    }

    @Test
    void allowsSlowImageInferenceWithoutChangingLongerConfiguredTimeouts() {
        assertEquals(Duration.ofMinutes(5),
                OpenAiCompatibleImageGenerationGateway.imageRequestTimeout(Duration.ofSeconds(60)));
        assertEquals(Duration.ofMinutes(10),
                OpenAiCompatibleImageGenerationGateway.imageRequestTimeout(Duration.ofMinutes(10)));
    }

    private static OpenAiCompatibleImageGenerationGateway gateway(TestServer server,
            AiGatewayType type) {
        String id = type == AiGatewayType.OPENAI ? "openai" : "nine-router";
        AiGatewayConnection connection = new AiGatewayConnection(id, id, type,
                server.baseUrl(), "secret", Duration.ofSeconds(5));
        return new OpenAiCompatibleImageGenerationGateway(ignored -> connection,
                RestClient.builder());
    }

    private static void respond(HttpExchange exchange, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
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
