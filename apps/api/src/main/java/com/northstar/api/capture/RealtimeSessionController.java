package com.northstar.api.capture;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import io.swagger.v3.oas.annotations.Operation;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mints an ephemeral OpenAI Realtime client secret for live dictation ("nói
 * đến đâu chữ lên đến đó"): the browser opens a WebSocket DIRECTLY to OpenAI
 * with the short-lived secret and streams mic PCM — audio never touches this
 * server and the real API key never reaches the browser. Plain REST here
 * because Spring AI 2.0 has no Realtime API abstraction yet.
 */
@RestController
@RequestMapping("/api/capture")
class RealtimeSessionController {

    private final AiClientRouter ai;
    private final AiGatewayConnectionResolver gateways;

    RealtimeSessionController(AiClientRouter ai, AiGatewayConnectionResolver gateways) {
        this.ai = ai;
        this.gateways = gateways;
    }

    @PostMapping("/realtime-session")
    @Operation(operationId = "createRealtimeCaptureSession")
    RealtimeSessionResponse mint() {
        AiRoute route = ai.route(AiTask.REALTIME_TRANSCRIPTION);
        ai.validate(AiTask.REALTIME_TRANSCRIPTION, route);
        AiGatewayConnection gateway = gateways.require(route.gatewayId());
        HttpClient http = HttpClient.newBuilder().connectTimeout(gateway.timeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(gateway.timeout());
        RestClient client = RestClient.builder()
                .baseUrl(gateway.baseUrl())
                .defaultHeader("Authorization", "Bearer " + gateway.apiKey())
                .requestFactory(requests)
                .build();
        String language = route.options().getOrDefault("language", "vi");
        // gpt-realtime-whisper streams deltas continuously and rejects
        // turn_detection, so the session config stays minimal.
        Map<?, ?> body = client.post()
                .uri("/realtime/client_secrets")
                .body(Map.of("session", Map.of(
                        "type", "transcription",
                        "audio", Map.of("input", Map.of(
                                "transcription", Map.of("model", route.modelId(), "language", language))))))
                .retrieve()
                .body(Map.class);
        if (body == null || !(body.get("value") instanceof String secret)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned no client secret");
        }
        return new RealtimeSessionResponse(secret, websocketUrl(gateway.baseUrl()));
    }

    private static String websocketUrl(String baseUrl) {
        String websocketBase = baseUrl.regionMatches(true, 0, "https://", 0, 8)
                ? "wss://" + baseUrl.substring(8)
                : baseUrl.regionMatches(true, 0, "http://", 0, 7)
                        ? "ws://" + baseUrl.substring(7)
                        : baseUrl;
        return websocketBase.replaceAll("/+$", "") + "/realtime";
    }
}
