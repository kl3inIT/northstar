package com.northstar.worker.automation;

import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import java.util.UUID;

final class AutomationScheduleData implements ScheduleAndData {

    private final Schedule schedule;
    private final UUID automationId;
    private final long scheduleVersion;

    private AutomationScheduleData() {
        this(null, null, 0);
    }

    AutomationScheduleData(Schedule schedule, UUID automationId, long scheduleVersion) {
        this.schedule = schedule;
        this.automationId = automationId;
        this.scheduleVersion = scheduleVersion;
    }

    @Override
    public Schedule getSchedule() {
        return schedule;
    }

    @Override
    public Object getData() {
        return automationId;
    }

    UUID automationId() {
        return automationId;
    }

    long scheduleVersion() {
        return scheduleVersion;
    }
}
