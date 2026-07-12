package com.northstar.integration.ai.openai;

import com.openai.models.audio.AudioResponseFormat;
import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiTask;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

/** Resolves the speech-to-text route and credential at call time. */
final class RoutedOpenAiTranscriptionModel implements TranscriptionModel {

    private final AiRouteSettingsService routes;
    private final AiGatewayRegistry gateways;

    RoutedOpenAiTranscriptionModel(AiRouteSettingsService routes, AiGatewayRegistry gateways) {
        this.routes = routes;
        this.gateways = gateways;
    }

    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        AiRoute route = routes.current(AiTask.SPEECH_TO_TEXT);
        AiGatewayDefinition gateway = gateways.definition(route.gatewayId());
        if (!gateway.type().supports(AiGatewayCapability.SPEECH_TO_TEXT)) {
            throw new IllegalArgumentException("Gateway " + gateway.id()
                    + " does not support " + AiGatewayCapability.SPEECH_TO_TEXT);
        }
        var delegate = OpenAiAudioTranscriptionModel.builder()
                .options(OpenAiAudioTranscriptionOptions.builder()
                        .baseUrl(gateway.baseUrl())
                        .apiKey(gateway.apiKey())
                        .model(route.modelId())
                        .responseFormat(AudioResponseFormat.TEXT)
                        .timeout(gateway.timeout())
                        .build())
                .build();
        return delegate.call(prompt);
    }
}
