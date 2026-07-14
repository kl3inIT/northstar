package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.cache.ExactCache;
import com.northstar.core.cache.ExactCacheNames;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.client.RestClient;

class OpenAiModelCatalogTests {

    @Test
    void cleartextModelProbeDoesNotAttemptH2cUpgrade() throws Exception {
        try (H2cRejectingServer server = H2cRejectingServer.start()) {
            OpenAiModelCatalog catalog = new OpenAiModelCatalog(null, RestClient.builder(),
                    cacheManager());
            AiGatewayDefinition gateway = new AiGatewayDefinition("router", AiGatewayType.NINE_ROUTER,
                    "9Router", server.baseUrl(), "secret", List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), false,
                    Duration.ofSeconds(5),
                    AiGatewaySource.SETTINGS);

            List<AiModelDescriptor> models = catalog.probe(gateway);

            assertEquals(List.of("combo-one"), models.stream().map(AiModelDescriptor::id).toList());
            String request = server.request();
            assertTrue(request.startsWith("GET /v1/models "));
            assertFalse(request.toLowerCase(Locale.ROOT).contains("upgrade: h2c"));
        }
    }

    @Test
    void invalidateForcesTheNextCatalogLoad() {
        CacheManager caches = cacheManager();
        AiGatewayRegistry gateways = mock(AiGatewayRegistry.class);
        AiGatewayDefinition gateway = new AiGatewayDefinition("router", AiGatewayType.NINE_ROUTER,
                "9Router", "https://router.test/v1", "secret", List.of("fresh"), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), false,
                Duration.ofSeconds(5), AiGatewaySource.SETTINGS);
        when(gateways.definition("router")).thenReturn(gateway);
        ExactCache.<String, List<AiModelDescriptor>>from(caches, ExactCacheNames.AI_MODEL_CATALOG)
                .put("router", List.of(new AiModelDescriptor("router", "cached", "cached")));
        OpenAiModelCatalog catalog = new OpenAiModelCatalog(gateways, RestClient.builder(), caches);

        assertEquals(List.of("cached"), catalog.models("router").stream()
                .map(AiModelDescriptor::id).toList());
        catalog.invalidate("router");
        assertEquals(List.of("fresh"), catalog.models("router").stream()
                .map(AiModelDescriptor::id).toList());
    }

    private static CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(ExactCacheNames.AI_MODEL_CATALOG);
    }

    private static final class H2cRejectingServer implements AutoCloseable {

        private final ServerSocket socket;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<String> request = new AtomicReference<>("");
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread thread;

        private H2cRejectingServer(ServerSocket socket) {
            this.socket = socket;
            this.thread = new Thread(this::serve, "h2c-rejecting-server");
            this.thread.start();
        }

        static H2cRejectingServer start() throws IOException {
            ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            return new H2cRejectingServer(socket);
        }

        String baseUrl() {
            return "http://" + socket.getInetAddress().getHostAddress() + ":"
                    + socket.getLocalPort() + "/v1";
        }

        String request() throws InterruptedException {
            if (!done.await(5, TimeUnit.SECONDS)) {
                fail("model probe did not reach the test server");
            }
            Throwable throwable = failure.get();
            if (throwable != null) {
                fail(throwable);
            }
            return request.get();
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join(2_000);
        }

        private void serve() {
            try (Socket client = socket.accept()) {
                client.setSoTimeout(2_000);
                String rawRequest = readHeaders(client);
                request.set(rawRequest);
                if (rawRequest.toLowerCase(Locale.ROOT).contains("upgrade: h2c")) {
                    return;
                }
                byte[] body = "{\"data\":[{\"id\":\"combo-one\"}]}".getBytes(StandardCharsets.UTF_8);
                byte[] headers = """
                        HTTP/1.1 200 OK\r
                        Content-Type: application/json\r
                        Content-Length: %d\r
                        Connection: close\r
                        \r
                        """.formatted(body.length).getBytes(StandardCharsets.US_ASCII);
                client.getOutputStream().write(headers);
                client.getOutputStream().write(body);
                client.getOutputStream().flush();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                done.countDown();
            }
        }

        private static String readHeaders(Socket client) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(),
                    StandardCharsets.US_ASCII));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isBlank()) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }
}
