package com.northstar.core.automation;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_run")
class AutomationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "automation_id", nullable = false)
    private AutomationDefinition automation;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_kind", nullable = false, length = 16)
    private AutomationRunKind runKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AutomationRunStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "output_type", length = 64)
    private String outputType;

    @Column(name = "output_id")
    private UUID outputId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metrics = Map.of();

    protected AutomationRun() {
        // for JPA
    }

    AutomationRun(UUID id, AutomationDefinition automation, Instant scheduledFor,
            AutomationRunKind runKind, AutomationRunStatus status) {
        super(id);
        this.automation = automation;
        this.scheduledFor = scheduledFor;
        this.runKind = runKind;
        this.status = status;
    }

    void start(Instant now) {
        status = AutomationRunStatus.RUNNING;
        attempt++;
        startedAt = now;
        finishedAt = null;
        errorCode = null;
        errorMessage = null;
    }

    void succeed(Instant now, AutomationHandlerResult result) {
        status = AutomationRunStatus.SUCCEEDED;
        finishedAt = now;
        outputType = result.outputType();
        outputId = result.outputId();
        metrics = Map.copyOf(new LinkedHashMap<>(result.metrics()));
    }

    void fail(Instant now, String code, String message) {
        status = AutomationRunStatus.FAILED;
        finishedAt = now;
        errorCode = clipped(code, 64);
        errorMessage = clipped(message, 2000);
    }

    void skip(Instant now, String code, String message) {
        status = AutomationRunStatus.SKIPPED;
        finishedAt = now;
        errorCode = clipped(code, 64);
        errorMessage = clipped(message, 2000);
    }

    AutomationRunSummary summary() {
        return new AutomationRunSummary(getId(), automation.getId(), scheduledFor, runKind, status,
                attempt, startedAt, finishedAt, errorCode, errorMessage, outputType, outputId,
                metrics, getCreatedAt(), getUpdatedAt());
    }

    AutomationRunStatus status() {
        return status;
    }

    AutomationDefinition automation() {
        return automation;
    }

    Instant scheduledFor() {
        return scheduledFor;
    }

    private static String clipped(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
