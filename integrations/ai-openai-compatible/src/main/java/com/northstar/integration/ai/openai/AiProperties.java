package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import com.northstar.core.ai.AiGatewayType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "northstar.ai")
@NullMarked
public record AiProperties(
        String activeGateway,
        Map<String, Gateway> gateways,
        Routes routes,
        Catalog catalog,
        Credentials credentials) {

    public AiProperties {
        activeGateway = normalize(activeGateway, "openai");
        gateways = gateways == null ? Map.of() : Map.copyOf(gateways);
        routes = routes == null ? Routes.empty() : routes;
        catalog = catalog == null ? new Catalog(Duration.ofMinutes(5)) : catalog;
        credentials = credentials == null ? new Credentials("") : credentials;
    }

    Map<AiTask, AiRoute> routeDefaults() {
        Map<AiTask, String> models = routes.byTask();
        Map<AiTask, AiRoute> result = new EnumMap<>(AiTask.class);
        models.forEach((task, model) -> result.put(task, new AiRoute(activeGateway, model)));
        return result;
    }

    public record Gateway(
            AiGatewayType type,
            String displayName,
            String baseUrl,
            String apiKey,
            List<String> models,
            boolean discoverModels,
            Duration timeout) {

        public Gateway {
            type = type == null ? AiGatewayType.OPENAI_CHAT_COMPATIBLE : type;
            displayName = normalize(displayName, "OpenAI-compatible");
            baseUrl = normalize(baseUrl, "");
            apiKey = apiKey == null ? "" : apiKey.strip();
            models = models == null ? List.of() : models.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
            timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        }

        boolean configured() {
            return !baseUrl.isBlank() && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Gateway[type=%s, displayName=%s, baseUrl=%s, apiKey=%s, models=%s, discoverModels=%s, timeout=%s]"
                    .formatted(type, displayName, baseUrl, apiKey.isBlank() ? "" : "***", models,
                            discoverModels, timeout);
        }
    }

    public record Routes(
            String assistant,
            String capture,
            String alignment,
            String title,
            String studyGrader,
            String imageCaption) {

        static Routes empty() {
            return new Routes("gpt-5.5", "gpt-5.5", "gpt-5.5", "gpt-5.5",
                    "gpt-5.5", "gpt-5.5");
        }

        Map<AiTask, String> byTask() {
            Map<AiTask, String> result = new EnumMap<>(AiTask.class);
            result.put(AiTask.ASSISTANT, required(assistant, "routes.assistant"));
            result.put(AiTask.CAPTURE, required(capture, "routes.capture"));
            result.put(AiTask.ALIGNMENT, required(alignment, "routes.alignment"));
            result.put(AiTask.TITLE, required(title, "routes.title"));
            result.put(AiTask.STUDY_GRADER, required(studyGrader, "routes.study-grader"));
            result.put(AiTask.IMAGE_CAPTION, required(imageCaption, "routes.image-caption"));
            return result;
        }
    }

    public record Catalog(Duration cacheTtl) {
        public Catalog {
            cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
        }
    }

    public record Credentials(String encryptionKeyBase64) {
        public Credentials {
            encryptionKeyBase64 = encryptionKeyBase64 == null ? "" : encryptionKeyBase64.strip();
        }

        @Override
        public String toString() {
            return "Credentials[encryptionKeyBase64=%s]"
                    .formatted(encryptionKeyBase64.isBlank() ? "" : "***");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
