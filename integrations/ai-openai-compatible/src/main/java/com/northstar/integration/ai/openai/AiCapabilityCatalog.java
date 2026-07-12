package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayType;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Manual-first capability catalogs with optional 9Router discovery. */
@Service
public class AiCapabilityCatalog {

    private static final Logger log = LoggerFactory.getLogger(AiCapabilityCatalog.class);
    private static final Map<AiGatewayCapability, List<String>> OPENAI_DEFAULTS = Map.of(
            AiGatewayCapability.SPEECH_TO_TEXT, List.of("whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"),
            AiGatewayCapability.IMAGE_GENERATION, List.of("gpt-image-2"),
            AiGatewayCapability.EMBEDDING, List.of("text-embedding-3-small", "text-embedding-3-large"));

    private final Function<String, AiGatewayDefinition> definitions;
    private final RestClient.Builder restClient;

    @Autowired
    AiCapabilityCatalog(AiGatewayRegistry gateways, RestClient.Builder restClient) {
        this(gateways::definition, restClient);
    }

    AiCapabilityCatalog(Function<String, AiGatewayDefinition> definitions,
            RestClient.Builder restClient) {
        this.definitions = definitions;
        this.restClient = restClient;
    }

    public List<AiCapabilityTarget> targets(String gatewayId, AiGatewayCapability capability) {
        return targets(definitions.apply(gatewayId), capability, false);
    }

    List<AiCapabilityTarget> probe(AiGatewayDefinition gateway, AiGatewayCapability capability) {
        return targets(gateway, capability, true);
    }

    private List<AiCapabilityTarget> targets(AiGatewayDefinition gateway,
            AiGatewayCapability capability, boolean forceDiscovery) {
        if (!gateway.type().supports(capability)) {
            return List.of();
        }
        Map<String, AiCapabilityTarget> result = new LinkedHashMap<>();
        manualTargets(gateway, capability).forEach(id -> put(result, gateway.id(), id, capability));
        if (gateway.type() == AiGatewayType.OPENAI) {
            OPENAI_DEFAULTS.getOrDefault(capability, List.of())
                    .forEach(id -> put(result, gateway.id(), id, capability));
            return sorted(result);
        }
        if (gateway.type() != AiGatewayType.NINE_ROUTER
                || (!gateway.discoverModels() && !forceDiscovery)) {
            return sorted(result);
        }
        Endpoint endpoint = Endpoint.forCapability(capability);
        if (endpoint == null) {
            return sorted(result);
        }
        try {
            ModelsResponse response = client(gateway).get().uri(endpoint.path())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(ModelsResponse.class);
            if (response != null && response.data() != null) {
                response.data().stream()
                        .filter(model -> model.id() != null && !model.id().isBlank())
                        .filter(model -> endpoint.kind() == null || endpoint.kind().equals(model.kind()))
                        .forEach(model -> put(result, gateway.id(), model.id(), capability));
            }
        } catch (RestClientException exception) {
            log.warn("Could not refresh {} catalog for gateway {}; using configured targets",
                    capability, gateway.id(), exception);
        }
        return sorted(result);
    }

    private RestClient client(AiGatewayDefinition gateway) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(gateway.timeout()).followRedirects(HttpClient.Redirect.NEVER);
        if (gateway.baseUrl().regionMatches(true, 0, "http://", 0, 7)) {
            http.version(HttpClient.Version.HTTP_1_1);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(gateway.timeout());
        RestClient.Builder builder = restClient.clone().baseUrl(gateway.baseUrl()).requestFactory(factory);
        if (!gateway.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gateway.apiKey());
        }
        return builder.build();
    }

    private static List<String> manualTargets(AiGatewayDefinition gateway, AiGatewayCapability capability) {
        return switch (capability) {
            case WEB_SEARCH -> gateway.webSearchTargets();
            case WEB_FETCH -> gateway.webFetchTargets();
            case SPEECH_TO_TEXT -> gateway.sttTargets();
            case IMAGE_GENERATION -> gateway.imageTargets();
            case EMBEDDING -> gateway.embeddingTargets();
            default -> List.of();
        };
    }

    private static void put(Map<String, AiCapabilityTarget> result, String gatewayId,
            String rawId, AiGatewayCapability capability) {
        String id = rawId.strip();
        result.putIfAbsent(id, new AiCapabilityTarget(gatewayId, id, displayName(id), capability));
    }

    private static List<AiCapabilityTarget> sorted(Map<String, AiCapabilityTarget> result) {
        return new ArrayList<>(result.values()).stream()
                .sorted(Comparator.comparing(AiCapabilityTarget::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String displayName(String id) {
        return id.replace('-', ' ').replace("/", " / ");
    }

    private record ModelsResponse(List<ModelResponse> data) {
    }

    private record ModelResponse(String id, String kind) {
    }

    private record Endpoint(String path, String kind) {
        static Endpoint forCapability(AiGatewayCapability capability) {
            return switch (capability) {
                case WEB_SEARCH -> new Endpoint("/models/web", "webSearch");
                case WEB_FETCH -> new Endpoint("/models/web", "webFetch");
                case SPEECH_TO_TEXT -> new Endpoint("/models/stt", null);
                case IMAGE_GENERATION -> new Endpoint("/models/image", null);
                case EMBEDDING -> new Endpoint("/models/embedding", null);
                default -> null;
            };
        }
    }
}
