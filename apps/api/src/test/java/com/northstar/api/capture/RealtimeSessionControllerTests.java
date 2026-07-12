package com.northstar.api.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealtimeSessionControllerTests {

    @Test
    void mintsFromTheSelectedGatewayAndReturnsItsWebsocketUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/realtime/client_secrets", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(request).contains("gpt-realtime-whisper").contains("\"language\":\"vi\"");
            byte[] response = "{\"value\":\"ephemeral-secret\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AiRoute route = new AiRoute("openai", "gpt-realtime-whisper", Map.of("language", "vi"));
            AiClientRouter ai = mock(AiClientRouter.class);
            when(ai.route(AiTask.REALTIME_TRANSCRIPTION)).thenReturn(route);
            AiGatewayConnectionResolver gateways = mock(AiGatewayConnectionResolver.class);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            when(gateways.require("openai")).thenReturn(new AiGatewayConnection(
                    "openai", "OpenAI", AiGatewayType.OPENAI, baseUrl, "server-key",
                    Duration.ofSeconds(5)));

            RealtimeSessionResponse result = new RealtimeSessionController(ai, gateways).mint();

            assertThat(result.clientSecret()).isEqualTo("ephemeral-secret");
            assertThat(result.websocketUrl()).isEqualTo(
                    "ws://127.0.0.1:" + server.getAddress().getPort() + "/v1/realtime");
        } finally {
            server.stop(0);
        }
    }
}
