package com.northstar.worker.automation;

import com.github.kagkarlsson.scheduler.boot.autoconfigure.Jackson3Serializer;
import com.github.kagkarlsson.scheduler.boot.config.DbSchedulerCustomizer;
import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AutomationSchedulerConfiguration {

    static final TaskDescriptor<AutomationScheduleData> RECURRING =
            TaskDescriptor.of("northstar-automation-v1", AutomationScheduleData.class);
    static final TaskDescriptor<ManualAutomationData> MANUAL =
            TaskDescriptor.of("northstar-automation-manual-v1", ManualAutomationData.class);

    @Bean
    DbSchedulerCustomizer dbSchedulerCustomizer() {
        return new DbSchedulerCustomizer() {
            @Override
            public Optional<com.github.kagkarlsson.scheduler.serializer.Serializer> serializer() {
                return Optional.of(new Jackson3Serializer());
            }
        };
    }

    @Bean
    Task<Void> automationReconcilerTask(AutomationSchedulerCoordinator coordinator) {
        return Tasks.recurring("northstar-automation-reconcile-v1", Schedules.fixedDelay(Duration.ofSeconds(10)))
                .execute((instance, context) -> coordinator.reconcile());
    }

    @Bean
    Task<AutomationScheduleData> recurringAutomationTask(AutomationTaskExecutor executor) {
        return Tasks.recurringWithPersistentSchedule(RECURRING)
                .onFailure(FailureHandler.<AutomationScheduleData>maxRetries(3)
                        .withBackoff(Duration.ofMinutes(1), 2)
                        .then(new FailureHandler.OnFailureRescheduleUsingTaskDataSchedule<>()))
                .execute((instance, context) -> executor.executeScheduled(
                        instance.getData(), context.getExecution().getExecutionTime()));
    }

    @Bean
    Task<ManualAutomationData> manualAutomationTask(AutomationTaskExecutor executor) {
        return Tasks.oneTime(MANUAL)
                .onFailure(FailureHandler.<ManualAutomationData>maxRetries(3)
                        .withBackoff(Duration.ofMinutes(1), 2).thenRemove())
                .execute((instance, context) -> executor.executeManual(instance.getData()));
    }
}
