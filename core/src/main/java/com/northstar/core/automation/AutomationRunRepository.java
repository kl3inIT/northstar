package com.northstar.core.automation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {
    Optional<AutomationRun> findByAutomationIdAndScheduledFor(UUID automationId, Instant scheduledFor);
    List<AutomationRun> findByAutomationIdOrderByScheduledForDesc(UUID automationId, Pageable pageable);
    List<AutomationRun> findByStatusOrderByScheduledForAsc(AutomationRunStatus status);
}
