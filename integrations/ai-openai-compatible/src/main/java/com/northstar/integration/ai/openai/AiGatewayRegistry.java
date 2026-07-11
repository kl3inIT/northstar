package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiGatewaySetting;
import com.northstar.core.ai.AiGatewaySettingRepository;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class AiGatewayRegistry {

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

    AiGatewayDefinition require(String gatewayId) {
        AiProperties.Gateway deployment = properties.gateways().get(gatewayId);
        if (deployment != null) {
            AiGatewayDefinition definition = deployment(gatewayId, deployment);
            if (!definition.configured()) {
                throw new IllegalStateException("AI gateway is not configured: " + gatewayId);
            }
            return definition;
        }
        return settings.findById(gatewayId)
                .map(this::runtime)
                .orElseThrow(() -> new IllegalArgumentException("Unknown AI gateway: " + gatewayId));
    }

    List<AiGatewayDescriptor> descriptors() {
        List<AiGatewayDescriptor> result = new ArrayList<>();
        properties.gateways().forEach((id, gateway) -> result.add(deployment(id, gateway).descriptor()));
        settings.findAll().stream().map(this::runtimeDescriptor)
                .forEach(result::add);
        return result.stream()
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
            throw new IllegalArgumentException("API key is required");
        }
        return new AiGatewayDefinition(id, AiGatewayType.OPENAI_COMPATIBLE,
                required(input.displayName(), "displayName"), baseUrl(input.baseUrl()), apiKey,
                models(input.models()), input.discoverModels(), timeout(input.timeoutSeconds()),
                AiGatewaySource.SETTINGS);
    }

    AiGatewayDescriptor save(AiGatewayInput input) {
        AiGatewayDefinition definition = draft(input);
        if (properties.gateways().containsKey(definition.id())) {
            throw new IllegalArgumentException(
                    "Deployment gateway IDs cannot be changed in Settings: " + definition.id());
        }
        byte[] encrypted = cipher.encrypt(definition.apiKey(), definition.id());
        AiGatewaySetting setting = settings.findById(definition.id())
                .orElseGet(() -> new AiGatewaySetting(definition.id(), definition.displayName(),
                        definition.baseUrl(), encrypted, encodeModels(definition.models()),
                        definition.discoverModels(), Math.toIntExact(definition.timeout().toSeconds())));
        setting.apply(definition.displayName(), definition.baseUrl(), encrypted,
                encodeModels(definition.models()), definition.discoverModels(),
                Math.toIntExact(definition.timeout().toSeconds()));
        settings.save(setting);
        return definition.descriptor();
    }

    void delete(String gatewayId) {
        if (properties.gateways().containsKey(gatewayId)) {
            throw new IllegalArgumentException("Deployment gateways cannot be deleted");
        }
        if (!settings.existsById(gatewayId)) {
            throw new IllegalArgumentException("Unknown runtime AI gateway: " + gatewayId);
        }
        settings.deleteById(gatewayId);
    }

    private AiGatewayDefinition deployment(String id, AiProperties.Gateway gateway) {
        return new AiGatewayDefinition(id, gateway.type(), gateway.displayName(), gateway.baseUrl(),
                gateway.apiKey(), gateway.models(), gateway.discoverModels(), gateway.timeout(),
                AiGatewaySource.DEPLOYMENT);
    }

    private AiGatewayDefinition runtime(AiGatewaySetting setting) {
        return new AiGatewayDefinition(setting.id(), AiGatewayType.OPENAI_COMPATIBLE,
                setting.displayName(), setting.baseUrl(),
                cipher.decrypt(setting.apiKeyCiphertext(), setting.id()), decodeModels(setting.models()),
                setting.discoverModels(), Duration.ofSeconds(setting.timeoutSeconds()),
                AiGatewaySource.SETTINGS);
    }

    private AiGatewayDescriptor runtimeDescriptor(AiGatewaySetting setting) {
        return new AiGatewayDescriptor(setting.id(), setting.displayName(), true,
                AiGatewaySource.SETTINGS, true, setting.baseUrl(),
                decodeModels(setting.models()), setting.discoverModels(), setting.timeoutSeconds());
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
