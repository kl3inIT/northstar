package com.northstar.worker.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationService;
import com.northstar.core.automation.AutomationTrigger;
import com.northstar.core.automation.AutomationTriggerKind;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AutomationSchedulerCoordinatorTests {

    @Test
    void buildsTimezoneAwareCronWithStableWeekdayOrder() {
        AutomationTrigger trigger = new AutomationTrigger(
                AutomationTriggerKind.DAILY,
                LocalTime.of(7, 15),
                Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                "Asia/Bangkok",
                120);

        CronSchedule schedule = AutomationSchedulerCoordinator.schedule(trigger);

        assertThat(schedule.getPattern()).isEqualTo("0 15 7 * * MON,WED,FRI");
        assertThat(schedule.getZoneId()).isEqualTo(ZoneId.of("Asia/Bangkok"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconcilesDisabledDefinitionAndMarksItsScheduleVersionSynced() {
        AutomationService automations = mock(AutomationService.class);
        SchedulerClient scheduler = mock(SchedulerClient.class);
        ObjectProvider<SchedulerClient> provider = mock(ObjectProvider.class);
        UUID id = UUID.randomUUID();
        AutomationTrigger trigger = new AutomationTrigger(
                AutomationTriggerKind.DAILY, LocalTime.of(7, 0), Set.of(DayOfWeek.MONDAY),
                "Asia/Bangkok", 120);
        AutomationDefinitionSummary definition = new AutomationDefinitionSummary(
                id, "morning-brief.v1", "Brief", false, trigger, Map.of(), 1,
                4, 3, false, null, Instant.now(), Instant.now(), 0);
        when(provider.getObject()).thenReturn(scheduler);
        when(automations.schedulingDefinitions()).thenReturn(List.of(definition));
        when(automations.queuedManualRuns()).thenReturn(List.of());
        when(scheduler.getScheduledExecution(AutomationSchedulerConfiguration.RECURRING.instanceId(id.toString())))
                .thenReturn(Optional.empty());

        new AutomationSchedulerCoordinator(automations, provider).reconcile();

        verify(automations).markScheduleSynced(id, 4);
    }
}
