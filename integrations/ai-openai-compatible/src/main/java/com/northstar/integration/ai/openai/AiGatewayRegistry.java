package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewaySetting;
import com.northstar.core.ai.AiGatewaySettingRepository;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayType;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class AiGatewayRegistry implements AiGatewayConnectionResolver {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{1,63}");
    private static final int MAX_MODELS = 200;

    private final AiProperties properties;
    private final AiGatewaySettingRepository settings;
    private final AiCredentialCipher cipher;

    AiGatewayRegistry(AiProperties properties, AiGatewaySettingRepository settings,
            AiCredentialCipher cipher) {
        this.properties = properties;
        this.settings = settings;
        this.cipher = cipher;
    }

    AiGatewayDefinition definition(String gatewayId) {
        var runtimeSetting = settings.findById(gatewayId);
        if (runtimeSetting.isPresent()) {
            return requireConfigured(runtime(runtimeSetting.get()));
        }
        AiProperties.Gateway deployment = properties.gateways().get(gatewayId);
        if (deployment != null) {
            return requireConfigured(deployment(gatewayId, deployment));
        }
        throw new IllegalArgumentException("Unknown AI gateway: " + gatewayId);
    }

    @Override
    public AiGatewayConnection require(String gatewayId) {
        AiGatewayDefinition gateway = definition(gatewayId);
        return new AiGatewayConnection(gateway.id(), gateway.displayName(), gateway.type(), gateway.baseUrl(),
                gateway.apiKey(), gateway.timeout());
    }

    List<AiGatewayDescriptor> descriptors() {
        Map<String, AiGatewayDescriptor> result = new LinkedHashMap<>();
        properties.gateways().forEach((id, gateway) ->
                result.put(id, deploymentDescriptor(id, gateway)));
        settings.findAll().forEach(setting ->
                result.put(setting.id(), runtimeDescriptor(setting)));
        return new ArrayList<>(result.values()).stream()
                .sorted(Comparator.comparing(AiGatewayDescriptor::displayName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    AiGatewayDefinition draft(AiGatewayInput input) {
        String id = normalizeId(input.id());
        String apiKey = normalize(input.apiKey());
        if (apiKey.isBlank()) {
            apiKey = settings.findById(id)
                    .map(setting -> cipher.decrypt(setting.apiKeyCiphertext(), id))
                    .orElse("");
        }
        if (apiKey.isBlank()) {
            AiProperties.Gateway deployment = properties.gateways().get(id);
            apiKey = deployment == null ? "" : deployment.apiKey();
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        AiGatewayType type = input.type() == null ? AiGatewayType.OPENAI_CHAT_COMPATIBLE : input.type();
        return new AiGatewayDefinition(id, type,
                required(input.displayName(), "displayName"), baseUrl(input.baseUrl()), apiKey,
                models(input.models()), models(input.ttsTargets()),
                models(input.webSearchTargets()), models(input.webFetchTargets()),
                models(input.sttTargets()), models(input.imageTargets()), models(input.embeddingTargets()),
                input.discoverModels(),
                timeout(input.timeoutSeconds()),
                AiGatewaySource.SETTINGS);
    }

    AiGatewayDescriptor save(AiGatewayInput input) {
        AiGatewayDefinition definition = draft(input);
        byte[] encrypted = cipher.encrypt(definition.apiKey(), definition.id());
        AiGatewaySetting setting = settings.findById(definition.id())
                .orElseGet(() -> new AiGatewaySetting(definition.id(), definition.displayName(), definition.type(),
                        definition.baseUrl(), encrypted, encodeModels(definition.models()),
                        encodeModels(definition.ttsTargets()),
                        encodeModels(definition.webSearchTargets()), encodeModels(definition.webFetchTargets()),
                        encodeModels(definition.sttTargets()), encodeModels(definition.imageTargets()),
                        encodeModels(definition.embeddingTargets()),
                        definition.discoverModels(), Math.toIntExact(definition.timeout().toSeconds())));
        setting.apply(definition.displayName(), definition.type(), definition.baseUrl(), encrypted,
                encodeModels(definition.models()), encodeModels(definition.ttsTargets()),
                encodeModels(definition.webSearchTargets()), encodeModels(definition.webFetchTargets()),
                encodeModels(definition.sttTargets()), encodeModels(definition.imageTargets()),
                encodeModels(definition.embeddingTargets()),
                definition.discoverModels(),
                Math.toIntExact(definition.timeout().toSeconds()));
        settings.save(setting);
        boolean deploymentBacked = properties.gateways().containsKey(definition.id());
        return definition.descriptor(AiCredentialSource.SETTINGS, deploymentBacked, deploymentBacked);
    }

    void delete(String gatewayId) {
        if (!settings.existsById(gatewayId)) {
            if (properties.gateways().containsKey(gatewayId)) {
                throw new IllegalArgumentException(
                        "Deployment gateway has no Settings override: " + gatewayId);
            }
            throw new IllegalArgumentException("Unknown runtime AI gateway: " + gatewayId);
        }
        settings.deleteById(gatewayId);
    }

    boolean deploymentBacked(String gatewayId) {
        return properties.gateways().containsKey(gatewayId);
    }

    private AiGatewayDefinition deployment(String id, AiProperties.Gateway gateway) {
        return new AiGatewayDefinition(id, gateway.type(), gateway.displayName(), gateway.baseUrl(),
                gateway.apiKey(), gateway.models(), gateway.ttsTargets(),
                gateway.webSearchTargets(), gateway.webFetchTargets(), gateway.sttTargets(),
                gateway.imageTargets(), gateway.embeddingTargets(),
                gateway.discoverModels(),
                gateway.timeout(),
                AiGatewaySource.DEPLOYMENT);
    }

    private AiGatewayDescriptor deploymentDescriptor(String id, AiProperties.Gateway gateway) {
        AiGatewayDefinition definition = deployment(id, gateway);
        AiCredentialSource credentialSource = gateway.apiKey().isBlank()
                ? AiCredentialSource.NONE : AiCredentialSource.ENVIRONMENT;
        return definition.descriptor(credentialSource, true, false);
    }

    private AiGatewayDefinition runtime(AiGatewaySetting setting) {
        return new AiGatewayDefinition(setting.id(), setting.type(),
                setting.displayName(), setting.baseUrl(),
                cipher.decrypt(setting.apiKeyCiphertext(), setting.id()), decodeModels(setting.models()),
                decodeModels(setting.ttsTargets()),
                decodeModels(setting.webSearchTargets()), decodeModels(setting.webFetchTargets()),
                decodeModels(setting.sttTargets()), decodeModels(setting.imageTargets()),
                decodeModels(setting.embeddingTargets()),
                setting.discoverModels(), Duration.ofSeconds(setting.timeoutSeconds()),
                AiGatewaySource.SETTINGS);
    }

    private AiGatewayDescriptor runtimeDescriptor(AiGatewaySetting setting) {
        boolean deploymentBacked = properties.gateways().containsKey(setting.id());
        return runtime(setting).descriptor(
                AiCredentialSource.SETTINGS, deploymentBacked, deploymentBacked);
    }

    private static AiGatewayDefinition requireConfigured(AiGatewayDefinition definition) {
        if (!definition.configured()) {
            throw new IllegalStateException("AI gateway is not configured: " + definition.id());
        }
        return definition;
    }

    private static String normalizeId(String value) {
        String id = required(value, "id").toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Gateway ID must contain 2-64 lowercase letters, numbers, or hyphens");
        }
        return id;
    }

    private static String baseUrl(String value) {
        String normalized = required(value, "baseUrl").replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Base URL is invalid", exception);
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Base URL must be an HTTP(S) origin/path without credentials, query, or fragment");
        }
        return normalized;
    }

    private static Duration timeout(int seconds) {
        if (seconds < 5 || seconds > 300) {
            throw new IllegalArgumentException("Timeout must be between 5 and 300 seconds");
        }
        return Duration.ofSeconds(seconds);
    }

    private static List<String> models(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(AiGatewayRegistry::normalize)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        unique -> unique.stream().limit(MAX_MODELS).toList()));
    }

    private static String encodeModels(List<String> models) {
        return String.join("\n", models);
    }

    private static List<String> decodeModels(String models) {
        return models(models == null ? List.of() : models.lines().toList());
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
