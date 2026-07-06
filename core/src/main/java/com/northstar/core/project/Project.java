package com.northstar.core.project;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A staged piece of work under a discipline. Unlike a task (one action,
 * OPEN/DONE), a project spans weeks and moves through {@link Milestone}s —
 * the aggregate owns them (cascade + orphan removal); they have no meaning
 * outside their project.
 */
@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String notes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    /** LDP spine: which discipline this project serves. Plain UUID — no JPA relation across modules. */
    @Column(name = "discipline_id")
    private UUID disciplineId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Milestone> milestones = new ArrayList<>();

    protected Project() {
        // for JPA
    }

    public Project(UUID id, String name, String notes, UUID disciplineId,
            LocalDate startDate, LocalDate targetDate) {
        super(id);
        this.name = name;
        this.notes = notes;
        this.disciplineId = disciplineId;
        this.startDate = startDate;
        this.targetDate = targetDate;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public UUID getDisciplineId() {
        return disciplineId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public List<Milestone> getMilestones() {
        return List.copyOf(milestones);
    }

    public void edit(String name, String notes, UUID disciplineId,
            LocalDate startDate, LocalDate targetDate) {
        this.name = name;
        this.notes = notes;
        this.disciplineId = disciplineId;
        this.startDate = startDate;
        this.targetDate = targetDate;
    }

    public void complete() {
        this.status = ProjectStatus.DONE;
    }

    public void reopen() {
        this.status = ProjectStatus.ACTIVE;
    }

    public Milestone addMilestone(UUID id, String name, LocalDate dueDate) {
        int nextOrder = milestones.stream().mapToInt(Milestone::getSortOrder).max().orElse(-1) + 1;
        Milestone milestone = new Milestone(id, this, name, dueDate, nextOrder);
        milestones.add(milestone);
        return milestone;
    }

    public Milestone milestone(UUID milestoneId) {
        return milestones.stream()
                .filter(m -> m.getId().equals(milestoneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No milestone with id " + milestoneId));
    }

    public void removeMilestone(UUID milestoneId) {
        milestones.remove(milestone(milestoneId));
    }

    /** Done milestones out of total; 0 while the project has no milestones yet. */
    public int progressPercent() {
        if (milestones.isEmpty()) {
            return status == ProjectStatus.DONE ? 100 : 0;
        }
        long done = milestones.stream().filter(m -> m.getDoneAt() != null).count();
        return (int) Math.round(done * 100.0 / milestones.size());
    }

    public enum ProjectStatus { ACTIVE, DONE }

    /** One stage of the project; owned by the aggregate, ordered by {@code sortOrder}. */
    @Entity
    @Table(name = "project_milestone")
    public static class Milestone {

        @Id
        @Column(nullable = false, updatable = false)
        private UUID id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "project_id", nullable = false)
        private Project project;

        @NotBlank
        @Column(nullable = false)
        private String name;

        @Column(name = "due_date")
        private LocalDate dueDate;

        @Column(name = "done_at")
        private Instant doneAt;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        protected Milestone() {
            // for JPA
        }

        Milestone(UUID id, Project project, String name, LocalDate dueDate, int sortOrder) {
            this.id = id;
            this.project = project;
            this.name = name;
            this.dueDate = dueDate;
            this.sortOrder = sortOrder;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public Instant getDoneAt() {
            return doneAt;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void edit(String name, LocalDate dueDate) {
            this.name = name;
            this.dueDate = dueDate;
        }

        public void toggleDone() {
            this.doneAt = doneAt == null ? Instant.now() : null;
        }
    }
}
