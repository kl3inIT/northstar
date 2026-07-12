package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiCapabilityCatalogTests {

    @Test
    void nineRouterMergesManualAndDiscoveredImageTargets() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models/image", exchange -> {
            byte[] body = """
                    {"object":"list","data":[{"id":"cx/gpt-image"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiGatewayDefinition definition = definition(server, List.of("manual/image"));
            AiCapabilityCatalog catalog = new AiCapabilityCatalog(id -> definition, RestClient.builder());

            List<AiCapabilityTarget> targets = catalog.targets(
                    "nine-router", AiGatewayCapability.IMAGE_GENERATION);

            assertEquals(List.of("cx/gpt-image", "manual/image"),
                    targets.stream().map(AiCapabilityTarget::id).toList());
        } finally {
            server.stop(0);
        }
    }

    private static AiGatewayDefinition definition(HttpServer server, List<String> images) {
        return new AiGatewayDefinition("nine-router", AiGatewayType.NINE_ROUTER, "9Router",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "secret",
                List.of(), List.of(), List.of(), List.of(), List.of(), images, List.of(),
                true, Duration.ofSeconds(5), AiGatewaySource.SETTINGS);
    }
}
