package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiTask;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import com.northstar.core.speech.TextToSpeechGateway;

@Component
public class SpringAiClientRouter implements AiClientRouter {

    private final AiProperties properties;
    private final AiGatewayRegistry gateways;
    private final AiRouteSettingsService routes;
    private final OpenAiModelCatalog catalog;
    private final AiCapabilityCatalog capabilityCatalog;
    private final TextToSpeechGateway speechCatalog;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    SpringAiClientRouter(AiProperties properties, AiGatewayRegistry gateways,
            AiRouteSettingsService routes, OpenAiModelCatalog catalog,
            AiCapabilityCatalog capabilityCatalog,
            TextToSpeechGateway speechCatalog) {
        this.properties = properties;
        this.gateways = gateways;
        this.routes = routes;
        this.catalog = catalog;
        this.capabilityCatalog = capabilityCatalog;
        this.speechCatalog = speechCatalog;
    }

    @Override
    public AiRoute route(AiTask task) {
        return routes.current(task);
    }

    @Override
    public ChatClient client(AiTask task) {
        return client(route(task));
    }

    @Override
    public ChatClient client(AiRoute route) {
        validateGateway(route.gatewayId());
        return clients.computeIfAbsent(route.gatewayId(), id -> ChatClient.builder(model(id))
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .build());
    }

    public ChatModel model(String gatewayId) {
        return models.computeIfAbsent(gatewayId, this::createModel);
    }

    @Override
    public ChatModel model(AiRoute route) {
        validateGateway(route.gatewayId());
        return model(route.gatewayId());
    }

    @Override
    public void validate(AiTask task, AiRoute route) {
        AiGatewayDefinition gateway = gateways.definition(route.gatewayId());
        if (!gateway.type().supports(task.requiredCapability())) {
            throw new IllegalArgumentException("Gateway " + route.gatewayId()
                    + " does not support " + task.requiredCapability());
        }
        if (task.requiredCapability() == com.northstar.core.ai.AiGatewayCapability.TEXT_TO_SPEECH) {
            var available = speechCatalog.targets(route.gatewayId());
            validateTarget(route, available.stream().map(com.northstar.core.speech.SpeechTarget::id).toList());
            return;
        }
        if (task.requiredCapability() != com.northstar.core.ai.AiGatewayCapability.CHAT) {
            var available = capabilityCatalog.targets(route.gatewayId(), task.requiredCapability());
            validateTarget(route, available.stream().map(AiCapabilityTarget::id).toList());
            return;
        }
        List<AiModelDescriptor> available = catalog.models(route.gatewayId());
        validateTarget(route, available.stream().map(AiModelDescriptor::id).toList());
    }

    private static void validateTarget(AiRoute route, List<String> available) {
        if (!available.isEmpty() && !available.contains(route.modelId())) {
            throw new IllegalArgumentException("Target is not available from gateway "
                    + route.gatewayId() + ": " + route.modelId());
        }
    }

    private void validateGateway(String gatewayId) {
        gateways.definition(gatewayId);
    }

    private ChatModel createModel(String gatewayId) {
        AiGatewayDefinition gateway = gateways.definition(gatewayId);
        String defaultModel = gateway.models().isEmpty()
                ? properties.routes().assistant()
                : gateway.models().getFirst();
        return switch (gateway.type()) {
            case OPENAI, NINE_ROUTER, OPENAI_CHAT_COMPATIBLE -> OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(gateway.baseUrl())
                            .apiKey(gateway.apiKey())
                            .model(defaultModel)
                            .timeout(gateway.timeout())
                            .build())
                    .build();
        };
    }

    void invalidate(String gatewayId) {
        clients.remove(gatewayId);
        models.remove(gatewayId);
    }
}
