package com.northstar.integration.ai.openai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenAiModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(OpenAiModelCatalog.class);

    private final AiProperties properties;
    private final RestClient.Builder restClient;
    private final Map<String, CachedModels> cache = new ConcurrentHashMap<>();

    OpenAiModelCatalog(AiProperties properties, RestClient.Builder restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    List<AiModelDescriptor> models(String gatewayId) {
        AiProperties.Gateway gateway = properties.requireGateway(gatewayId);
        CachedModels current = cache.get(gatewayId);
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current.models();
        }
        List<AiModelDescriptor> models = load(gatewayId, gateway);
        cache.put(gatewayId, new CachedModels(models,
                Instant.now().plus(properties.catalog().cacheTtl())));
        return models;
    }

    private List<AiModelDescriptor> load(String gatewayId, AiProperties.Gateway gateway) {
        Map<String, AiModelDescriptor> result = new LinkedHashMap<>();
        gateway.models().forEach(id -> result.put(id,
                new AiModelDescriptor(gatewayId, id, displayName(id))));
        if (!gateway.discoverModels()) {
            return sorted(result);
        }
        try {
            ModelsResponse response = restClient.clone()
                    .baseUrl(gateway.baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gateway.apiKey())
                    .build()
                    .get()
                    .uri("/models")
                    .retrieve()
                    .body(ModelsResponse.class);
            if (response != null && response.data() != null) {
                response.data().stream()
                        .filter(model -> model.id() != null && !model.id().isBlank())
                        .forEach(model -> result.putIfAbsent(model.id(),
                                new AiModelDescriptor(gatewayId, model.id(), displayName(model.id()))));
            }
        } catch (RuntimeException exception) {
            log.warn("Could not refresh model catalog for gateway {}; using configured models",
                    gatewayId, exception);
        }
        return sorted(result);
    }

    private static List<AiModelDescriptor> sorted(Map<String, AiModelDescriptor> result) {
        return new ArrayList<>(result.values()).stream()
                .sorted(Comparator.comparing(AiModelDescriptor::displayName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String displayName(String id) {
        int slash = id.lastIndexOf('/');
        String name = slash >= 0 ? id.substring(slash + 1) : id;
        return name.replace('-', ' ');
    }

    private record ModelsResponse(List<ModelResponse> data) {
    }

    private record ModelResponse(String id) {
    }

    private record CachedModels(List<AiModelDescriptor> models, Instant expiresAt) {
    }
}
