package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.speech.SpeechAudio;
import com.northstar.core.speech.SpeechSynthesisException;
import com.northstar.core.speech.SpeechTarget;
import com.northstar.core.speech.TextToSpeechGateway;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** OpenAI speech endpoint plus 9Router's normalized implementation of it. */
@Component
public class OpenAiCompatibleTextToSpeechGateway implements TextToSpeechGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleTextToSpeechGateway.class);

    private static final List<String> OPENAI_FULL_VOICES = List.of(
            "alloy", "ash", "ballad", "cedar", "coral", "echo", "fable",
            "marin", "nova", "onyx", "sage", "shimmer", "verse");
    private static final List<String> OPENAI_STANDARD_VOICES = List.of(
            "alloy", "ash", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer");
    private static final Set<String> VOICE_CATALOG_PROVIDERS = Set.of(
            "edge-tts", "local-device", "elevenlabs", "el", "deepgram", "dg", "inworld");

    private final AiGatewayConnectionResolver gateways;
    private final Function<String, AiGatewayDefinition> definitions;
    private final RestClient.Builder restClient;

    @Autowired
    OpenAiCompatibleTextToSpeechGateway(AiGatewayRegistry gateways,
            RestClient.Builder restClient) {
        this(gateways, gateways::definition, restClient);
    }

    public OpenAiCompatibleTextToSpeechGateway(AiGatewayConnectionResolver gateways,
            RestClient.Builder restClient) {
        this(gateways, id -> definition(gateways.require(id)), restClient);
    }

    private OpenAiCompatibleTextToSpeechGateway(AiGatewayConnectionResolver gateways,
            Function<String, AiGatewayDefinition> definitions, RestClient.Builder restClient) {
        this.gateways = gateways;
        this.definitions = definitions;
        this.restClient = restClient;
    }

    @Override
    public SpeechAudio synthesize(AiRoute route, String text, String locale) {
        AiGatewayConnection gateway = connection(route.gatewayId());
        Map<String, Object> body = gateway.type() == AiGatewayType.OPENAI
                ? openAiBody(route.modelId(), text)
                : nineRouterBody(route.modelId(), text, locale);
        String uri = gateway.type() == AiGatewayType.NINE_ROUTER
                ? "/audio/speech?response_format=mp3"
                : "/audio/speech";
        try {
            ResponseEntity<byte[]> response = client(gateway)
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"), MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new SpeechSynthesisException("TTS provider returned empty audio");
            }
            return new SpeechAudio(bytes, "audio/mpeg", "mp3");
        } catch (SpeechSynthesisException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new SpeechSynthesisException("TTS request failed for gateway " + route.gatewayId(), exception);
        }
    }

    @Override
    public List<SpeechTarget> targets(String gatewayId) {
        return configuredTargets(definitions.apply(gatewayId));
    }

    List<SpeechTarget> configuredTargets(AiGatewayDefinition gateway) {
        return loadTargets(gateway, false);
    }

    List<SpeechTarget> probeTargets(AiGatewayDefinition gateway) {
        return loadTargets(gateway, false);
    }

    private List<SpeechTarget> loadTargets(AiGatewayDefinition definition, boolean failFast) {
        AiGatewayConnection gateway = connection(definition);
        Map<String, SpeechTarget> result = new LinkedHashMap<>();
        definition.ttsTargets().forEach(id -> result.put(id,
                manualTarget(gateway.id(), id)));
        if (gateway.type() == AiGatewayType.OPENAI) {
            openAiTargets(gateway.id()).forEach(target -> result.putIfAbsent(target.id(), target));
            return sorted(result);
        }
        if (!definition.discoverModels() && !failFast) {
            return sorted(result);
        }
        try {
            ModelsResponse response = client(gateway)
                    .get()
                    .uri("/models/tts")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ModelsResponse.class);
            if (response != null && response.data() != null) {
                response.data().stream()
                        .filter(model -> model.id() != null && !model.id().isBlank())
                        .map(model -> manualTarget(gateway.id(), model.id().strip()))
                        .forEach(target -> result.putIfAbsent(target.id(), target));
            }
            discoverVoices(gateway, result);
            return sorted(result);
        } catch (RestClientException exception) {
            if (!failFast) {
                log.warn("Could not refresh TTS catalog for gateway {}; using configured targets",
                        gateway.id(), exception);
                return sorted(result);
            }
            throw new SpeechSynthesisException("Could not discover TTS targets from gateway " + gateway.id(),
                    exception);
        }
    }

    private void discoverVoices(AiGatewayConnection gateway, Map<String, SpeechTarget> result) {
        result.keySet().stream().map(OpenAiCompatibleTextToSpeechGateway::provider)
                .filter(VOICE_CATALOG_PROVIDERS::contains).distinct().toList()
                .forEach(provider -> {
                    try {
                        VoicesResponse response = client(gateway).get()
                                .uri(builder -> builder.path("/audio/voices")
                                        .queryParam("provider", providerAlias(provider)).build())
                                .accept(MediaType.APPLICATION_JSON).retrieve().body(VoicesResponse.class);
                        if (response != null && response.data() != null) {
                            response.data().stream()
                                    .filter(voice -> voice.model() != null && !voice.model().isBlank())
                                    .map(voice -> new SpeechTarget(gateway.id(), voice.model().strip(),
                                            voiceName(voice), normalized(voice.lang()), normalized(voice.gender())))
                                    .forEach(target -> result.put(target.id(), target));
                        }
                    } catch (RestClientException exception) {
                        log.warn("Could not refresh {} voices for gateway {}; keeping manual targets",
                                provider, gateway.id());
                    }
                });
    }

    @Override
    public void validate(AiRoute route) {
        connection(route.gatewayId());
        if (route.modelId().length() > 255) {
            throw new IllegalArgumentException("TTS target must be at most 255 characters");
        }
    }

    private AiGatewayConnection connection(String gatewayId) {
        AiGatewayConnection gateway = gateways.require(gatewayId);
        if (!gateway.supports(AiGatewayCapability.TEXT_TO_SPEECH)) {
            throw new IllegalArgumentException("Gateway " + gatewayId + " does not support text-to-speech");
        }
        return gateway;
    }

    private static AiGatewayConnection connection(AiGatewayDefinition definition) {
        AiGatewayConnection gateway = new AiGatewayConnection(definition.id(), definition.displayName(),
                definition.type(), definition.baseUrl(), definition.apiKey(), definition.timeout());
        if (!gateway.supports(AiGatewayCapability.TEXT_TO_SPEECH)) {
            throw new IllegalArgumentException(
                    "Gateway " + gateway.id() + " does not support text-to-speech");
        }
        return gateway;
    }

    private RestClient client(AiGatewayConnection gateway) {
        RestClient.Builder builder = restClient.clone()
                .baseUrl(gateway.baseUrl())
                .requestFactory(requestFactory(gateway));
        if (!gateway.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gateway.apiKey());
        }
        return builder.build();
    }

    private static Map<String, Object> openAiBody(String targetId, String text) {
        OpenAiTarget target = parseOpenAiTarget(targetId);
        return Map.of(
                "model", target.model(),
                "voice", target.voice(),
                "input", text,
                "response_format", "mp3");
    }

    private static Map<String, Object> nineRouterBody(String targetId, String text, String locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", targetId);
        body.put("input", text);
        if (!"auto".equals(locale)) {
            body.put("language", locale);
        }
        return body;
    }

    private static OpenAiTarget parseOpenAiTarget(String targetId) {
        String target = targetId.startsWith("openai/") ? targetId.substring("openai/".length()) : targetId;
        int slash = target.lastIndexOf('/');
        if (slash < 1 || slash == target.length() - 1) {
            return new OpenAiTarget(target, "alloy");
        }
        return new OpenAiTarget(target.substring(0, slash), target.substring(slash + 1));
    }

    private static List<SpeechTarget> openAiTargets(String gatewayId) {
        List<SpeechTarget> targets = new ArrayList<>();
        addOpenAiTargets(targets, gatewayId, "gpt-4o-mini-tts", OPENAI_FULL_VOICES);
        addOpenAiTargets(targets, gatewayId, "gpt-4o-mini-tts-2025-12-15", OPENAI_FULL_VOICES);
        addOpenAiTargets(targets, gatewayId, "tts-1", OPENAI_STANDARD_VOICES);
        addOpenAiTargets(targets, gatewayId, "tts-1-hd", OPENAI_STANDARD_VOICES);
        return List.copyOf(targets);
    }

    private static void addOpenAiTargets(List<SpeechTarget> targets, String gatewayId,
            String model, List<String> voices) {
        for (String voice : voices) {
            String id = "openai/" + model + "/" + voice;
            targets.add(new SpeechTarget(gatewayId, id, displayName(model) + " / " + title(voice),
                    "multi", ""));
        }
    }

    private static SpeechTarget manualTarget(String gatewayId, String id) {
        return new SpeechTarget(gatewayId, id, displayName(id), language(id), "");
    }

    private static String language(String id) {
        String[] parts = id.split("/");
        if (parts.length > 1 && parts[0].equals("edge-tts")) {
            String voice = parts[1];
            int secondDash = voice.indexOf('-', 3);
            return secondDash > 0 ? voice.substring(0, secondDash) : "";
        }
        return "";
    }

    private static String provider(String id) {
        int slash = id.indexOf('/');
        return slash > 0 ? id.substring(0, slash) : id;
    }

    private static String providerAlias(String provider) {
        return switch (provider) {
            case "el" -> "elevenlabs";
            case "dg" -> "deepgram";
            default -> provider;
        };
    }

    private static String voiceName(VoiceResponse voice) {
        String name = normalized(voice.name());
        return name.isBlank() ? displayName(voice.model()) : name;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static String displayName(String id) {
        return id.replace('-', ' ').replace("/", " / ");
    }

    private static List<SpeechTarget> sorted(Map<String, SpeechTarget> targets) {
        return targets.values().stream()
                .sorted(java.util.Comparator.comparing(SpeechTarget::displayName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static AiGatewayDefinition definition(AiGatewayConnection gateway) {
        return new AiGatewayDefinition(gateway.id(), gateway.type(), gateway.displayName(),
                gateway.baseUrl(), gateway.apiKey(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), true,
                gateway.timeout(), AiGatewaySource.DEPLOYMENT);
    }

    private static String title(String value) {
        if (value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static JdkClientHttpRequestFactory requestFactory(AiGatewayConnection gateway) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(gateway.timeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (gateway.baseUrl().regionMatches(true, 0, "http://", 0, 7)) {
            http.version(HttpClient.Version.HTTP_1_1);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(gateway.timeout());
        return factory;
    }

    private record ModelsResponse(List<ModelResponse> data) {
    }

    private record ModelResponse(String id) {
    }

    private record VoicesResponse(List<VoiceResponse> data) {
    }

    private record VoiceResponse(String id, String name, String lang, String gender, String model) {
    }

    private record OpenAiTarget(String model, String voice) {
    }
}
