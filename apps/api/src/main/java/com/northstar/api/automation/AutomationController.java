package com.northstar.api.automation;

import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationRunSummary;
import com.northstar.core.automation.AutomationService;
import com.northstar.core.automation.AutomationTrigger;
import com.northstar.core.automation.AutomationTriggerKind;
import com.northstar.core.automation.AutomationTypeDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automations")
class AutomationController {

    private final AutomationService automations;

    AutomationController(AutomationService automations) {
        this.automations = automations;
    }

    @GetMapping
    @Operation(operationId = "listAutomations")
    List<AutomationDefinitionSummary> list() {
        return automations.list();
    }

    @GetMapping("/types")
    @Operation(operationId = "listAutomationTypes")
    List<AutomationTypeDescriptor> types() {
        return automations.supportedTypes();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAutomation")
    AutomationDefinitionSummary get(@PathVariable UUID id) {
        return automations.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAutomation")
    AutomationDefinitionSummary create(@Valid @RequestBody CreateAutomationRequest request) {
        return automations.create(request.type(), request.name(), request.enabled(),
                request.trigger().value(), request.workflowConfig());
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateAutomation")
    AutomationDefinitionSummary update(@PathVariable UUID id,
            @Valid @RequestBody UpdateAutomationRequest request) {
        return automations.update(id, request.name(), request.enabled(), request.trigger().value(),
                request.workflowConfig(), request.version());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAutomation")
    void delete(@PathVariable UUID id, @RequestParam long version) {
        automations.delete(id, version);
    }

    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "runAutomationNow")
    AutomationRunSummary runNow(@PathVariable UUID id) {
        return automations.requestRunNow(id);
    }

    @GetMapping("/{id}/runs")
    @Operation(operationId = "listAutomationRuns")
    List<AutomationRunSummary> runs(@PathVariable UUID id,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return automations.history(id, limit);
    }

    record CreateAutomationRequest(
            @NotBlank @Size(max = 64) String type,
            @NotBlank @Size(max = 160) String name,
            boolean enabled,
            @NotNull @Valid TriggerRequest trigger,
            @NotNull Map<String, Object> workflowConfig) {
    }

    record UpdateAutomationRequest(
            @NotBlank @Size(max = 160) String name,
            boolean enabled,
            @NotNull @Valid TriggerRequest trigger,
            @NotNull Map<String, Object> workflowConfig,
            @Min(0) long version) {
    }

    record TriggerRequest(
            @NotNull AutomationTriggerKind kind,
            @NotNull LocalTime localTime,
            @NotEmpty Set<DayOfWeek> daysOfWeek,
            @NotBlank @Size(max = 64) String timezone,
            @Min(0) @Max(1440) int catchUpWindowMinutes) {

        AutomationTrigger value() {
            return new AutomationTrigger(kind, localTime, daysOfWeek, timezone, catchUpWindowMinutes);
        }
    }
}
