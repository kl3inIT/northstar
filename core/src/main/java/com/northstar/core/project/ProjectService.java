package com.northstar.core.project;

import com.northstar.core.discipline.DisciplineService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project module's public API. Milestone writes go through the aggregate
 * (the project owns its stages); progress is derived on read, never stored.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final DisciplineService disciplines;

    ProjectService(ProjectRepository projects, DisciplineService disciplines) {
        this.projects = projects;
        this.disciplines = disciplines;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list() {
        return projects.findAllByOrderByCreatedAtDesc().stream().map(ProjectService::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listByDiscipline(UUID disciplineId) {
        return projects.findByDisciplineIdOrderByCreatedAtDesc(disciplineId).stream()
                .map(ProjectService::summary).toList();
    }

    @Transactional(readOnly = true)
    public ProjectSummary find(UUID id) {
        return summary(get(id));
    }

    /** Existence check for modules that FK to a project (task agenda). */
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return projects.existsById(id);
    }

    @Transactional
    public ProjectSummary create(String name, String notes, UUID disciplineId,
            LocalDate startDate, LocalDate targetDate) {
        disciplines.requireExists(disciplineId);
        requireValidSpan(startDate, targetDate);
        Project project = new Project(UUID.randomUUID(), name.strip(), clean(notes),
                disciplineId, startDate, targetDate);
        projects.save(project);
        return summary(project);
    }

    @Transactional
    public ProjectSummary update(UUID id, String name, String notes, UUID disciplineId,
            LocalDate startDate, LocalDate targetDate) {
        disciplines.requireExists(disciplineId);
        requireValidSpan(startDate, targetDate);
        Project project = get(id);
        project.edit(name.strip(), clean(notes), disciplineId, startDate, targetDate);
        return summary(project);
    }

    @Transactional
    public ProjectSummary setDone(UUID id, boolean done) {
        Project project = get(id);
        if (done) {
            project.complete();
        } else {
            project.reopen();
        }
        return summary(project);
    }

    @Transactional
    public void delete(UUID id) {
        projects.delete(get(id));
    }

    @Transactional
    public ProjectSummary addMilestone(UUID projectId, String name, LocalDate dueDate) {
        Project project = get(projectId);
        project.addMilestone(UUID.randomUUID(), name.strip(), dueDate);
        return summary(project);
    }

    @Transactional
    public ProjectSummary editMilestone(UUID projectId, UUID milestoneId, String name, LocalDate dueDate) {
        Project project = get(projectId);
        project.milestone(milestoneId).edit(name.strip(), dueDate);
        return summary(project);
    }

    @Transactional
    public ProjectSummary toggleMilestone(UUID projectId, UUID milestoneId) {
        Project project = get(projectId);
        project.milestone(milestoneId).toggleDone();
        return summary(project);
    }

    @Transactional
    public ProjectSummary removeMilestone(UUID projectId, UUID milestoneId) {
        Project project = get(projectId);
        project.removeMilestone(milestoneId);
        return summary(project);
    }

    private Project get(UUID id) {
        return projects.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }


    private static void requireValidSpan(LocalDate startDate, LocalDate targetDate) {
        if (startDate != null && targetDate != null && targetDate.isBefore(startDate)) {
            throw new IllegalArgumentException("targetDate must not be before startDate");
        }
    }

    private static String clean(String notes) {
        return notes == null || notes.isBlank() ? null : notes.strip();
    }

    private static ProjectSummary summary(Project project) {
        return new ProjectSummary(project.getId(), project.getName(), project.getNotes(),
                project.getStatus(), project.getDisciplineId(), project.getStartDate(),
                project.getTargetDate(),
                project.getMilestones().stream()
                        .map(m -> new MilestoneSummary(m.getId(), m.getName(), m.getDueDate(),
                                m.getDoneAt(), m.getSortOrder()))
                        .toList(),
                project.progressPercent(), project.getCreatedAt());
    }
}
