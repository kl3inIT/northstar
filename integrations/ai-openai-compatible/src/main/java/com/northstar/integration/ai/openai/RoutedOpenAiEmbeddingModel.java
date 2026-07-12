package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiTask;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/** Resolves the embedding route and credential at call time. */
final class RoutedOpenAiEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 1536;

    private final AiRouteSettingsService routes;
    private final AiGatewayRegistry gateways;

    RoutedOpenAiEmbeddingModel(AiRouteSettingsService routes, AiGatewayRegistry gateways) {
        this.routes = routes;
        this.gateways = gateways;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate().call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate().embed(document);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private OpenAiEmbeddingModel delegate() {
        AiRoute route = routes.current(AiTask.EMBEDDING);
        AiGatewayDefinition gateway = gateways.definition(route.gatewayId());
        requireCapability(gateway, AiGatewayCapability.EMBEDDING);
        return OpenAiEmbeddingModel.builder()
                .options(OpenAiEmbeddingOptions.builder()
                        .baseUrl(gateway.baseUrl())
                        .apiKey(gateway.apiKey())
                        .model(route.modelId())
                        .dimensions(DIMENSIONS)
                        .timeout(gateway.timeout())
                        .build())
                .build();
    }

    private static void requireCapability(AiGatewayDefinition gateway, AiGatewayCapability capability) {
        if (!gateway.type().supports(capability)) {
            throw new IllegalArgumentException("Gateway " + gateway.id() + " does not support " + capability);
        }
    }
}
