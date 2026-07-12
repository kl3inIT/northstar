package com.northstar.api.speech;

import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiTask;
import com.northstar.core.speech.SpeechAssetContent;
import com.northstar.core.speech.SpeechAssetResult;
import com.northstar.core.speech.SpeechAssetService;
import com.northstar.core.speech.SpeechTarget;
import com.northstar.core.speech.TextToSpeechGateway;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/speech")
class SpeechController {

    private final SpeechAssetService speech;
    private final AiRouteSettingsService routes;
    private final TextToSpeechGateway gateway;

    SpeechController(SpeechAssetService speech, AiRouteSettingsService routes,
            TextToSpeechGateway gateway) {
        this.speech = speech;
        this.routes = routes;
        this.gateway = gateway;
    }

    @PostMapping("/synthesize")
    @Operation(operationId = "synthesizeSpeech")
    SpeechAssetResponse synthesize(@Valid @RequestBody SynthesizeSpeechRequest request) {
        AiRoute configured = routes.current(AiTask.TEXT_TO_SPEECH);
        AiRoute route = request.gatewayId() == null && request.targetId() == null
                ? configured
                : request.routeOverride();
        SpeechAssetResult result = speech.synthesize(route, request.text(), request.locale());
        var asset = result.asset();
        return new SpeechAssetResponse(asset.id(), "/api/speech/assets/" + asset.id() + "/audio",
                asset.gatewayId(), asset.targetId(), asset.locale(), asset.format(), asset.mimeType(),
                asset.textLength(), asset.createdAt(), result.cacheHit());
    }

    @GetMapping("/gateways/{gatewayId}/targets")
    @Operation(operationId = "listSpeechTargets")
    List<SpeechTarget> targets(@PathVariable String gatewayId) {
        return gateway.targets(gatewayId);
    }

    @GetMapping("/assets/{id}/audio")
    @Operation(operationId = "serveSpeechAudio")
    ResponseEntity<byte[]> audio(@PathVariable UUID id) {
        SpeechAssetContent content = speech.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No speech asset " + id));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.asset().mimeType()))
                .contentLength(content.data().length)
                .body(content.data());
    }

    record SynthesizeSpeechRequest(
            @Size(min = 1, max = SpeechAssetService.MAX_TEXT_LENGTH) String text,
            @Size(max = 64) String gatewayId,
            @Size(max = 255) String targetId,
            @Size(max = 35) String locale) {

        AiRoute routeOverride() {
            if (gatewayId == null || gatewayId.isBlank() || targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("gatewayId and targetId must be provided together");
            }
            return new AiRoute(gatewayId, targetId);
        }
    }

    record SpeechAssetResponse(
            UUID id,
            String audioUrl,
            String gatewayId,
            String targetId,
            String locale,
            String format,
            String mediaType,
            int textLength,
            Instant createdAt,
            boolean cacheHit) {
    }
}
