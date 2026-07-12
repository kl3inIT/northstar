package com.northstar.api.ai;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiRouteSettingsService;
import com.northstar.core.ai.AiRouteSettingsService.AiRouteSelection;
import com.northstar.core.ai.AiTask;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.integration.ai.openai.AiCatalogService;
import com.northstar.integration.ai.openai.AiGatewayDescriptor;
import com.northstar.integration.ai.openai.AiGatewayInput;
import com.northstar.integration.ai.openai.AiGatewayManagementService;
import com.northstar.integration.ai.openai.AiGatewayTestResult;
import com.northstar.integration.ai.openai.AiModelDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final AiGatewayManagementService gateways;

    AiSettingsController(AiRouteSettingsService settings, AiClientRouter router,
            AiCatalogService catalog, AiGatewayManagementService gateways) {
        this.settings = settings;
        this.router = router;
        this.catalog = catalog;
        this.gateways = gateways;
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

    @PostMapping("/gateways")
    @Operation(operationId = "createAiGateway")
    AiGatewayDescriptor createGateway(@Valid @RequestBody AiGatewayRequest request) {
        return gateways.save(request.input(request.id()));
    }

    @PutMapping("/gateways/{gatewayId}")
    @Operation(operationId = "updateAiGateway")
    AiGatewayDescriptor updateGateway(@PathVariable String gatewayId,
            @Valid @RequestBody AiGatewayRequest request) {
        return gateways.save(request.input(gatewayId));
    }

    @PostMapping("/gateways/test")
    @Operation(operationId = "testAiGatewayDraft")
    AiGatewayTestResult testGatewayDraft(@Valid @RequestBody AiGatewayRequest request) {
        return gateways.test(request.input(request.id()));
    }

    @PostMapping("/gateways/{gatewayId}/test")
    @Operation(operationId = "testAiGateway")
    AiGatewayTestResult testGateway(@PathVariable String gatewayId) {
        return gateways.test(gatewayId);
    }

    @DeleteMapping("/gateways/{gatewayId}")
    @Operation(operationId = "deleteAiGateway")
    void deleteGateway(@PathVariable String gatewayId) {
        gateways.delete(gatewayId);
    }

    record AiRouteRequest(@NotBlank String gatewayId, @NotBlank String modelId) {
    }

    record AiGatewayRequest(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 100) String displayName,
            AiGatewayType type,
            @NotBlank @Size(max = 500) String baseUrl,
            @Size(max = 1000) String apiKey,
            @Size(max = 200) List<@NotBlank @Size(max = 255) String> models,
            boolean discoverModels,
            @Min(5) @Max(300) int timeoutSeconds) {

        AiGatewayInput input(String resolvedId) {
            return new AiGatewayInput(resolvedId, displayName, type, baseUrl, apiKey, models,
                    discoverModels, timeoutSeconds);
        }
    }

    record AiSettingsResponse(Map<AiTask, AiRouteSelection> routes,
            List<AiGatewayDescriptor> gateways) {
    }
}
