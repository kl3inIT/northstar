package com.northstar.worker.automation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationRunClaim;
import com.northstar.core.automation.AutomationRunKind;
import com.northstar.core.automation.AutomationRunStatus;
import com.northstar.core.automation.AutomationRunSummary;
import com.northstar.core.automation.AutomationService;
import com.northstar.core.automation.AutomationTrigger;
import com.northstar.core.automation.AutomationTriggerKind;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationTaskExecutorTests {

    @Test
    void skipsQueuedManualRunWhenDefinitionWasDeleted() {
        AutomationService automations = mock(AutomationService.class);
        UUID automationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-11T05:00:00Z");
        AutomationDefinitionSummary definition = new AutomationDefinitionSummary(
                automationId, "morning-brief.v1", "Brief", false,
                new AutomationTrigger(AutomationTriggerKind.DAILY, LocalTime.of(7, 0),
                        Set.of(DayOfWeek.MONDAY), "Asia/Bangkok", 120),
                Map.of(), 1, 2, 1, false, now, now, now, 1);
        AutomationRunSummary run = new AutomationRunSummary(
                runId, automationId, now, AutomationRunKind.MANUAL,
                AutomationRunStatus.RUNNING, 1, now, null, null, null,
                null, null, Map.of(), now, now);
        when(automations.beginManualRun(runId)).thenReturn(new AutomationRunClaim(true, definition, run));

        new AutomationTaskExecutor(automations).executeManual(new ManualAutomationData(automationId, runId));

        verify(automations).beginManualRun(runId);
        verify(automations).skip(runId, "DELETED", "Automation was deleted before manual execution");
        verifyNoMoreInteractions(automations);
    }
}
