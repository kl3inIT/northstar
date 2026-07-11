package com.northstar.core.automation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {

    private final AutomationDefinitionRepository definitions;
    private final AutomationRunRepository runs;
    private final AutomationHandlerRegistry handlers;
    private final Clock clock;

    @Autowired
    AutomationService(AutomationDefinitionRepository definitions, AutomationRunRepository runs,
            AutomationHandlerRegistry handlers) {
        this(definitions, runs, handlers, Clock.systemUTC());
    }

    AutomationService(AutomationDefinitionRepository definitions, AutomationRunRepository runs,
            AutomationHandlerRegistry handlers, Clock clock) {
        this.definitions = definitions;
        this.runs = runs;
        this.handlers = handlers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AutomationDefinitionSummary> list() {
        return definitions.findByDeletedAtIsNullOrderByCreatedAtAsc().stream()
                .map(AutomationDefinition::summary).toList();
    }

    @Transactional(readOnly = true)
    public AutomationDefinitionSummary find(UUID id) {
        return active(id).summary();
    }

    public List<AutomationTypeDescriptor> supportedTypes() {
        return handlers.descriptors();
    }

    @Transactional
    public AutomationDefinitionSummary create(String type, String name, boolean enabled,
            AutomationTrigger trigger, Map<String, Object> workflowConfig) {
        String normalizedType = required(type, "type").toLowerCase();
        int configVersion = handlers.validate(normalizedType, workflowConfig);
        AutomationDefinition definition = new AutomationDefinition(UUID.randomUUID(), normalizedType,
                required(name, "name"), enabled, trigger, safeConfig(workflowConfig), configVersion);
        definitions.saveAndFlush(definition);
        return definition.summary();
    }

    @Transactional
    public AutomationDefinitionSummary update(UUID id, String name, boolean enabled,
            AutomationTrigger trigger, Map<String, Object> workflowConfig, long expectedVersion) {
        AutomationDefinition definition = active(id);
        if (definition.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Automation " + id
                    + " was modified concurrently (expected version " + expectedVersion
                    + ", is " + definition.getVersion() + ")");
        }
        int configVersion = handlers.validate(definition.type(), workflowConfig);
        definition.edit(required(name, "name"), enabled, trigger, safeConfig(workflowConfig), configVersion);
        definitions.saveAndFlush(definition);
        return definition.summary();
    }

    @Transactional
    public void delete(UUID id, long expectedVersion) {
        AutomationDefinition definition = active(id);
        if (definition.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Automation " + id + " was modified concurrently");
        }
        definition.delete(Instant.now(clock));
    }

    @Transactional
    public AutomationRunSummary requestRunNow(UUID id) {
        AutomationDefinition definition = active(id);
        AutomationRun run = new AutomationRun(UUID.randomUUID(), definition, Instant.now(clock),
                AutomationRunKind.MANUAL, AutomationRunStatus.QUEUED);
        runs.saveAndFlush(run);
        return run.summary();
    }

    @Transactional(readOnly = true)
    public List<AutomationRunSummary> history(UUID automationId, int limit) {
        active(automationId);
        return runs.findByAutomationIdOrderByScheduledForDesc(automationId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream()
                .map(AutomationRun::summary).toList();
    }

    /** Worker projection input, including soft-deleted rows that still need cancellation. */
    @Transactional(readOnly = true)
    public List<AutomationDefinitionSummary> schedulingDefinitions() {
        return definitions.findAllByOrderByCreatedAtAsc().stream().map(AutomationDefinition::summary).toList();
    }

    @Transactional
    public void markScheduleSynced(UUID id, long scheduleVersion) {
        definitions.findById(id).ifPresent(definition -> definition.markSynced(scheduleVersion));
    }

    @Transactional(readOnly = true)
    public List<AutomationRunSummary> queuedManualRuns() {
        return runs.findByStatusOrderByScheduledForAsc(AutomationRunStatus.QUEUED).stream()
                .map(AutomationRun::summary).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutomationRunClaim beginScheduledRun(UUID automationId, Instant scheduledFor) {
        AutomationDefinition definition = definitions.findById(automationId)
                .orElseThrow(() -> new AutomationDefinitionNotFoundException(automationId));
        AutomationRun run = runs.findByAutomationIdAndScheduledFor(automationId, scheduledFor)
                .orElseGet(() -> runs.save(new AutomationRun(UUID.randomUUID(), definition, scheduledFor,
                        AutomationRunKind.SCHEDULED, AutomationRunStatus.QUEUED)));
        return claim(definition, run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutomationRunClaim beginManualRun(UUID runId) {
        AutomationRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Automation run not found: " + runId));
        return claim(run.automation(), run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID runId, AutomationHandlerResult result) {
        AutomationRun run = requireRun(runId);
        if (run.status() == AutomationRunStatus.RUNNING) run.succeed(Instant.now(clock), result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String code, String message) {
        AutomationRun run = requireRun(runId);
        if (run.status() == AutomationRunStatus.RUNNING) run.fail(Instant.now(clock), code, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skip(UUID runId, String code, String message) {
        AutomationRun run = requireRun(runId);
        if (run.status() == AutomationRunStatus.RUNNING) run.skip(Instant.now(clock), code, message);
    }

    public AutomationHandlerResult execute(AutomationRunClaim claim) {
        AutomationDefinitionSummary definition = claim.definition();
        AutomationRunSummary run = claim.run();
        return handlers.execute(definition.type(), definition.workflowConfig(),
                new AutomationExecutionContext(definition.id(), definition.name(), run.id(),
                        run.scheduledFor(), definition.trigger().zoneId(), run.runKind(), run.attempt()));
    }

    private AutomationRunClaim claim(AutomationDefinition definition, AutomationRun run) {
        if (run.status() == AutomationRunStatus.SUCCEEDED || run.status() == AutomationRunStatus.SKIPPED) {
            return new AutomationRunClaim(false, definition.summary(), run.summary());
        }
        run.start(Instant.now(clock));
        runs.saveAndFlush(run);
        return new AutomationRunClaim(true, definition.summary(), run.summary());
    }

    private AutomationDefinition active(UUID id) {
        AutomationDefinition definition = definitions.findById(id)
                .orElseThrow(() -> new AutomationDefinitionNotFoundException(id));
        if (definition.deleted()) throw new AutomationDefinitionNotFoundException(id);
        return definition;
    }

    private AutomationRun requireRun(UUID id) {
        return runs.findById(id).orElseThrow(() -> new IllegalArgumentException("Automation run not found: " + id));
    }

    private static String required(String value, String field) {
        String clean = value == null ? "" : value.strip();
        if (clean.isBlank()) throw new IllegalArgumentException(field + " is required");
        return clean;
    }

    private static Map<String, Object> safeConfig(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
