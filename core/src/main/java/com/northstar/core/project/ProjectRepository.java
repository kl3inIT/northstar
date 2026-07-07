package com.northstar.core.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Project}. Other modules go through
 * {@link ProjectService}, not this repository.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findByDisciplineIdOrderByCreatedAtDesc(UUID disciplineId);

    long countByDisciplineId(UUID disciplineId);
}
