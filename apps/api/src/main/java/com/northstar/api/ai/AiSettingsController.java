package com.northstar.api.ai;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiRouteSettingsService.AiRouteSelection;
import com.northstar.core.ai.AiTask;
import com.northstar.integration.ai.openai.AiCatalogService;
import com.northstar.integration.ai.openai.AiGatewayDescriptor;
import com.northstar.integration.ai.openai.AiModelDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/ai")
class AiSettingsController {

    private final AiRouteSettingsService settings;
    private final AiClientRouter router;
    private final AiCatalogService catalog;

    AiSettingsController(AiRouteSettingsService settings, AiClientRouter router,
            AiCatalogService catalog) {
        this.settings = settings;
        this.router = router;
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(operationId = "getAiSettings")
    AiSettingsResponse get() {
        return new AiSettingsResponse(settings.all(), catalog.gateways());
    }

    @PutMapping("/routes/{task}")
    @Operation(operationId = "updateAiRoute")
    AiRouteSelection update(@PathVariable AiTask task, @Valid @RequestBody AiRouteRequest request) {
        return settings.update(task, new AiRoute(request.gatewayId(), request.modelId()), router);
    }

    @DeleteMapping("/routes/{task}/override")
    @Operation(operationId = "resetAiRoute")
    AiRouteSelection reset(@PathVariable AiTask task) {
        return settings.reset(task);
    }

    @GetMapping("/gateways/{gatewayId}/models")
    @Operation(operationId = "listAiModels")
    List<AiModelDescriptor> models(@PathVariable String gatewayId) {
        return catalog.models(gatewayId);
    }

    record AiRouteRequest(@NotBlank String gatewayId, @NotBlank String modelId) {
    }

    record AiSettingsResponse(Map<AiTask, AiRouteSelection> routes,
            List<AiGatewayDescriptor> gateways) {
    }
}
