package com.northstar.core.automation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record AutomationExecutionContext(
        UUID automationId,
        String automationName,
        UUID runId,
        Instant scheduledFor,
        ZoneId zone,
        AutomationRunKind runKind,
        int attempt) {
}
