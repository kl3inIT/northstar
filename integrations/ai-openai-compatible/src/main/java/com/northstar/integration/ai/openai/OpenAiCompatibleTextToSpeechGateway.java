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

    private static final List<String> OPENAI_FULL_VOICES = List.of(
            "alloy", "ash", "ballad", "cedar", "coral", "echo", "fable",
            "marin", "nova", "onyx", "sage", "shimmer", "verse");
    private static final List<String> OPENAI_STANDARD_VOICES = List.of(
            "alloy", "ash", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer");

    private final AiGatewayConnectionResolver gateways;
    private final RestClient.Builder restClient;

    public OpenAiCompatibleTextToSpeechGateway(AiGatewayConnectionResolver gateways,
            RestClient.Builder restClient) {
        this.gateways = gateways;
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
        AiGatewayConnection gateway = connection(gatewayId);
        if (gateway.type() == AiGatewayType.OPENAI) {
            return openAiTargets(gateway.id());
        }
        try {
            ModelsResponse response = client(gateway)
                    .get()
                    .uri("/models/tts")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ModelsResponse.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(model -> model.id() != null && !model.id().isBlank())
                    .map(model -> new SpeechTarget(gateway.id(), model.id().strip(), displayName(model.id())))
                    .distinct()
                    .sorted(java.util.Comparator.comparing(SpeechTarget::displayName,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (RestClientException exception) {
            throw new SpeechSynthesisException("Could not discover TTS targets from gateway " + gatewayId,
                    exception);
        }
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
            targets.add(new SpeechTarget(gatewayId, id, displayName(model) + " / " + title(voice)));
        }
    }

    private static String displayName(String id) {
        return id.replace('-', ' ').replace("/", " / ");
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

    private record OpenAiTarget(String model, String voice) {
    }
}
