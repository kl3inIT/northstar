package com.northstar.integration.ai.openai;

import com.northstar.core.cache.ExactCache;
import com.northstar.core.cache.ExactCacheNames;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenAiModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(OpenAiModelCatalog.class);

    private final AiGatewayRegistry gateways;
    private final RestClient.Builder restClient;
    private final ExactCache<String, List<AiModelDescriptor>> cache;

    OpenAiModelCatalog(AiGatewayRegistry gateways, RestClient.Builder restClient,
            CacheManager cacheManager) {
        this.gateways = gateways;
        this.restClient = restClient;
        this.cache = ExactCache.from(cacheManager, ExactCacheNames.AI_MODEL_CATALOG);
    }

    List<AiModelDescriptor> models(String gatewayId) {
        AiGatewayDefinition gateway = gateways.definition(gatewayId);
        List<AiModelDescriptor> current = cache.find(gatewayId).orElse(null);
        if (current != null) return current;
        List<AiModelDescriptor> models = load(gatewayId, gateway);
        cache.put(gatewayId, models);
        return models;
    }

    List<AiModelDescriptor> probe(AiGatewayDefinition gateway) {
        return load(gateway.id(), gateway, true, true);
    }

    void invalidate(String gatewayId) {
        cache.evict(gatewayId);
    }

    private List<AiModelDescriptor> load(String gatewayId, AiGatewayDefinition gateway) {
        return load(gatewayId, gateway, false, false);
    }

    private List<AiModelDescriptor> load(String gatewayId, AiGatewayDefinition gateway,
            boolean failFast, boolean forceDiscovery) {
        Map<String, AiModelDescriptor> result = new LinkedHashMap<>();
        gateway.models().forEach(id -> result.put(id,
                new AiModelDescriptor(gatewayId, id, displayName(id))));
        if (!gateway.discoverModels() && !forceDiscovery) {
            return sorted(result);
        }
        try {
            JdkClientHttpRequestFactory requestFactory =
                    requestFactory(gateway.baseUrl(), gateway.timeout());
            ModelsResponse response = restClient.clone()
                    .baseUrl(gateway.baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gateway.apiKey())
                    .requestFactory(requestFactory)
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
            if (failFast) {
                throw exception;
            }
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

    private static JdkClientHttpRequestFactory requestFactory(String baseUrl, Duration timeout) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER);
        if (cleartext(baseUrl)) {
            http.version(HttpClient.Version.HTTP_1_1);
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http.build());
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private static boolean cleartext(String baseUrl) {
        return baseUrl.regionMatches(true, 0, "http://", 0, 7);
    }

    private record ModelsResponse(List<ModelResponse> data) {
    }

    private record ModelResponse(String id) {
    }

}
