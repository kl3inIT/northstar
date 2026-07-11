package com.northstar.integration.ai.openai;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiCatalogService {

    private final AiProperties properties;
    private final OpenAiModelCatalog models;

    AiCatalogService(AiProperties properties, OpenAiModelCatalog models) {
        this.properties = properties;
        this.models = models;
    }

    public List<AiGatewayDescriptor> gateways() {
        return properties.gateways().entrySet().stream()
                .map(entry -> new AiGatewayDescriptor(entry.getKey(), entry.getValue().displayName(),
                        entry.getValue().configured()))
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();
    }

    public List<AiModelDescriptor> models(String gatewayId) {
        return models.models(gatewayId);
    }
}
