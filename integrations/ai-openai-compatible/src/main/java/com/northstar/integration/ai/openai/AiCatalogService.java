package com.northstar.integration.ai.openai;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiCatalogService {

    private final AiGatewayRegistry gateways;
    private final OpenAiModelCatalog models;

    AiCatalogService(AiGatewayRegistry gateways, OpenAiModelCatalog models) {
        this.gateways = gateways;
        this.models = models;
    }

    public List<AiGatewayDescriptor> gateways() {
        return gateways.descriptors();
    }

    public List<AiModelDescriptor> models(String gatewayId) {
        return models.models(gatewayId);
    }
}
