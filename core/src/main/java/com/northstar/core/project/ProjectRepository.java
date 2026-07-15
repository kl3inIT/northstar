package com.northstar.core.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Project}. Other modules go through
 * {@link ProjectService}, not this repository.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // Fetch milestones with the projects: summary() reads getMilestones() per row,
    // which would otherwise fire one SELECT per project (N+1).
    @EntityGraph(attributePaths = "milestones")
    List<Project> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "milestones")
    List<Project> findByDisciplineIdOrderByCreatedAtDesc(UUID disciplineId);

    long countByDisciplineId(UUID disciplineId);
}
