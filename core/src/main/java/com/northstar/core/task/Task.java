package com.northstar.core.task;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A task. {@code dueDate} is optional (null = someday); {@code dueTime} refines a
 * dated task onto a calendar slot later. {@code plannedDate} is the Things-style
 * "do" date — the day the user intends to work on it — independent of the
 * deadline: starring a task for today must never move its due date. Status is
 * deliberately binary — the methodology's friction rule wants "done?" to be a
 * yes/no question. {@code completedAt} is domain data (drives the Today view),
 * unlike the audit timestamps inherited from {@link BaseEntity}.
 */
@Entity
@Table(name = "task")
public class Task extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** LDP spine: which discipline this action trains. Plain UUID — no JPA relation across modules. */
    @Column(name = "discipline_id")
    private UUID disciplineId;

    /** Optional project this task is part of (the project's agenda). Plain UUID across modules. */
    @Column(name = "project_id")
    private UUID projectId;

    protected Task() {
        // for JPA
    }

    public Task(UUID id, String title, String notes, LocalDate dueDate, LocalTime dueTime, UUID disciplineId) {
        super(id);
        this.title = title;
        this.notes = notes;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.disciplineId = disciplineId;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getDisciplineId() {
        return disciplineId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    /** Attach to / detach from a project without touching anything else; null detaches. */
    public void assignToProject(UUID projectId) {
        this.projectId = projectId;
    }

    public void edit(String title, String notes, LocalDate dueDate, LocalTime dueTime,
            LocalDate plannedDate, UUID disciplineId) {
        this.title = title;
        this.notes = notes;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.plannedDate = plannedDate;
        this.disciplineId = disciplineId;
    }

    /** The "do" side of do-vs-due: (re)schedule my work day; null = unplanned. */
    public void planFor(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public void complete(Instant now) {
        // Idempotent: re-completing an already-done task must keep the original
        // completedAt, otherwise a repeated set_task_done(true) resurfaces it in Today.
        if (this.status == TaskStatus.DONE) return;
        this.status = TaskStatus.DONE;
        this.completedAt = now;
    }

    public void reopen() {
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
    }
}
