package com.northstar.core.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AutomationDefinitionRepository extends JpaRepository<AutomationDefinition, UUID> {
    List<AutomationDefinition> findByDeletedAtIsNullOrderByCreatedAtAsc();
    List<AutomationDefinition> findAllByOrderByCreatedAtAsc();
}
