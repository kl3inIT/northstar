package com.northstar.worker.automation;

import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationHandlerResult;
import com.northstar.core.automation.AutomationRunClaim;
import com.northstar.core.automation.AutomationService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AutomationTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutomationTaskExecutor.class);

    private final AutomationService automations;

    AutomationTaskExecutor(AutomationService automations) {
        this.automations = automations;
    }

    void executeScheduled(AutomationScheduleData data, Instant scheduledFor) {
        AutomationRunClaim claim = automations.beginScheduledRun(data.automationId(), scheduledFor);
        if (!claim.execute()) return;
        AutomationDefinitionSummary definition = claim.definition();
        if (!definition.enabled() || definition.deletedAt() != null) {
            automations.skip(claim.run().id(), "DISABLED", "Automation was disabled before execution");
            return;
        }
        Duration lateness = Duration.between(scheduledFor, Instant.now());
        if (!lateness.isNegative()
                && lateness.toMinutes() > definition.trigger().catchUpWindowMinutes()) {
            automations.skip(claim.run().id(), "MISFIRE_EXPIRED",
                    "Execution was outside its catch-up window");
            return;
        }
        execute(claim);
    }

    void executeManual(ManualAutomationData data) {
        AutomationRunClaim claim = automations.beginManualRun(data.runId());
        if (!claim.execute()) return;
        if (claim.definition().deletedAt() != null) {
            automations.skip(claim.run().id(), "DELETED",
                    "Automation was deleted before manual execution");
            return;
        }
        execute(claim);
    }

    private void execute(AutomationRunClaim claim) {
        try {
            AutomationHandlerResult result = automations.execute(claim);
            automations.succeed(claim.run().id(), result);
            log.info("Automation {} run {} completed", claim.definition().id(), claim.run().id());
        } catch (RuntimeException exception) {
            automations.fail(claim.run().id(), "EXECUTION_FAILED", exception.getMessage());
            log.warn("Automation {} run {} failed", claim.definition().id(), claim.run().id(), exception);
            throw exception;
        }
    }
}
