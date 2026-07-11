package com.northstar.worker.automation;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.SchedulerClient.ScheduleOptions;
import com.github.kagkarlsson.scheduler.exceptions.TaskInstanceCurrentlyExecutingException;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationRunSummary;
import com.northstar.core.automation.AutomationService;
import com.northstar.core.automation.AutomationTrigger;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class AutomationSchedulerCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AutomationSchedulerCoordinator.class);

    private final AutomationService automations;
    private final ObjectProvider<SchedulerClient> schedulerProvider;

    AutomationSchedulerCoordinator(AutomationService automations, ObjectProvider<SchedulerClient> schedulerProvider) {
        this.automations = automations;
        this.schedulerProvider = schedulerProvider;
    }

    void reconcile() {
        SchedulerClient scheduler = schedulerProvider.getObject();
        for (AutomationDefinitionSummary definition : automations.schedulingDefinitions()) {
            if (definition.scheduleSynced()) continue;
            try {
                project(scheduler, definition);
                automations.markScheduleSynced(definition.id(), definition.scheduleVersion());
            } catch (TaskInstanceCurrentlyExecutingException exception) {
                log.debug("Automation {} is running; its new schedule will sync next pass", definition.id());
            } catch (RuntimeException exception) {
                log.warn("Could not reconcile automation {}", definition.id(), exception);
            }
        }
        for (AutomationRunSummary run : automations.queuedManualRuns()) {
            try {
                scheduler.scheduleIfNotExists(AutomationSchedulerConfiguration.MANUAL
                        .instance(run.id().toString())
                        .data(new ManualAutomationData(run.automationId(), run.id()))
                        .scheduledTo(run.scheduledFor()));
            } catch (RuntimeException exception) {
                log.warn("Could not enqueue manual automation run {}", run.id(), exception);
            }
        }
    }

    private void project(SchedulerClient scheduler, AutomationDefinitionSummary definition) {
        var instanceId = AutomationSchedulerConfiguration.RECURRING.instanceId(definition.id().toString());
        if (!definition.enabled() || definition.deletedAt() != null) {
            if (scheduler.getScheduledExecution(instanceId).isPresent()) scheduler.cancel(instanceId);
            return;
        }
        CronSchedule schedule = schedule(definition.trigger());
        AutomationScheduleData data = new AutomationScheduleData(
                schedule, definition.id(), definition.scheduleVersion());
        scheduler.schedule(
                AutomationSchedulerConfiguration.RECURRING.instance(definition.id().toString())
                        .data(data).scheduledAccordingToData(),
                ScheduleOptions.defaultOptions().whenExistsReschedule());
    }

    static CronSchedule schedule(AutomationTrigger trigger) {
        String days = trigger.daysOfWeek().stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(day -> day.name().substring(0, 3).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(","));
        String expression = "0 %d %d * * %s".formatted(
                trigger.localTime().getMinute(), trigger.localTime().getHour(), days);
        return new CronSchedule(expression, trigger.zoneId());
    }
}
