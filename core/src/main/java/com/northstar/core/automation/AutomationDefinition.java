package com.northstar.core.automation;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_definition")
class AutomationDefinition extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_kind", nullable = false, length = 16)
    private AutomationTriggerKind triggerKind;

    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    @Column(name = "days_of_week", nullable = false, length = 96)
    private String daysOfWeek;

    @Column(name = "timezone_id", nullable = false, length = 64)
    private String timezoneId;

    @Column(name = "catch_up_window_minutes", nullable = false)
    private int catchUpWindowMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> workflowConfig = Map.of();

    @Column(name = "config_version", nullable = false)
    private int configVersion;

    @Column(name = "schedule_version", nullable = false)
    private long scheduleVersion;

    @Column(name = "synced_schedule_version", nullable = false)
    private long syncedScheduleVersion;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AutomationDefinition() {
        // for JPA
    }

    AutomationDefinition(UUID id, String type, String name, boolean enabled,
            AutomationTrigger trigger, Map<String, Object> workflowConfig, int configVersion) {
        super(id);
        this.type = type;
        this.scheduleVersion = 1;
        apply(name, enabled, trigger, workflowConfig, configVersion);
    }

    void apply(String name, boolean enabled, AutomationTrigger trigger,
            Map<String, Object> workflowConfig, int configVersion) {
        this.name = name;
        this.enabled = enabled;
        this.triggerKind = trigger.kind();
        this.localTime = trigger.localTime();
        this.daysOfWeek = encodeDays(trigger.daysOfWeek());
        this.timezoneId = trigger.timezone();
        this.catchUpWindowMinutes = trigger.catchUpWindowMinutes();
        this.workflowConfig = Map.copyOf(new LinkedHashMap<>(workflowConfig));
        this.configVersion = configVersion;
    }

    void edit(String name, boolean enabled, AutomationTrigger trigger,
            Map<String, Object> workflowConfig, int configVersion) {
        apply(name, enabled, trigger, workflowConfig, configVersion);
        scheduleVersion++;
    }

    void delete(Instant now) {
        enabled = false;
        deletedAt = now;
        scheduleVersion++;
    }

    void markSynced(long projectedVersion) {
        if (projectedVersion == scheduleVersion) syncedScheduleVersion = projectedVersion;
    }

    AutomationDefinitionSummary summary() {
        return new AutomationDefinitionSummary(getId(), type, name, enabled, trigger(),
                workflowConfig, configVersion, scheduleVersion, syncedScheduleVersion,
                scheduleVersion == syncedScheduleVersion,
                deletedAt, getCreatedAt(), getUpdatedAt(), getVersion());
    }

    AutomationTrigger trigger() {
        return new AutomationTrigger(triggerKind, localTime, decodeDays(daysOfWeek),
                timezoneId, catchUpWindowMinutes);
    }

    String type() {
        return type;
    }

    boolean enabled() {
        return enabled;
    }

    long scheduleVersion() {
        return scheduleVersion;
    }

    long syncedScheduleVersion() {
        return syncedScheduleVersion;
    }

    boolean deleted() {
        return deletedAt != null;
    }

    Map<String, Object> workflowConfig() {
        return workflowConfig;
    }

    private static String encodeDays(Set<DayOfWeek> days) {
        return days.stream().sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<DayOfWeek> decodeDays(String value) {
        return Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet());
    }
}
