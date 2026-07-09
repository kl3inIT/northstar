package com.northstar.api.capture;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    private final RestClient openai;
    private final String model;

    RealtimeSessionController(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${northstar.capture.realtime-stt-model:gpt-realtime-whisper}") String model) {
        this.openai = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @PostMapping("/realtime-session")
    @Operation(operationId = "createRealtimeCaptureSession")
    RealtimeSessionResponse mint() {
        // gpt-realtime-whisper streams deltas continuously and rejects
        // turn_detection, so the session config stays minimal.
        Map<?, ?> body = openai.post()
                .uri("/v1/realtime/client_secrets")
                .body(Map.of("session", Map.of(
                        "type", "transcription",
                        "audio", Map.of("input", Map.of(
                                "transcription", Map.of("model", model, "language", "vi"))))))
                .retrieve()
                .body(Map.class);
        if (body == null || !(body.get("value") instanceof String secret)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned no client secret");
        }
        return new RealtimeSessionResponse(secret);
    }
}
