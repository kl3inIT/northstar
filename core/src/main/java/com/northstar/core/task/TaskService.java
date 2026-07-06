package com.northstar.core.task;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.project.ProjectService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The task module's public API. "Today" is date-based in the caller's zone: it
 * bundles overdue + due-today open tasks plus anything completed today (so a
 * ticked box stays visible until tomorrow). One store — list, board and calendar
 * views all read from here.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final DisciplineService disciplines;
    private final ProjectService projects;

    TaskService(TaskRepository tasks, DisciplineService disciplines, ProjectService projects) {
        this.tasks = tasks;
        this.disciplines = disciplines;
        this.projects = projects;
    }

    @Transactional
    public TaskSummary create(String title, String notes, LocalDate dueDate, LocalTime dueTime,
            UUID disciplineId) {
        return create(title, notes, dueDate, dueTime, null, disciplineId);
    }

    @Transactional
    public TaskSummary create(String title, String notes, LocalDate dueDate, LocalTime dueTime,
            LocalDate plannedDate, UUID disciplineId) {
        requireDiscipline(disciplineId);
        Task task = new Task(UUID.randomUUID(), title.strip(),
                notes == null || notes.isBlank() ? null : notes.strip(),
                dueDate, dueTime, disciplineId);
        task.planFor(plannedDate);
        tasks.save(task);
        return summary(task);
    }

    @Transactional
    public TaskSummary update(UUID id, String title, String notes, LocalDate dueDate, LocalTime dueTime,
            LocalDate plannedDate, UUID disciplineId) {
        requireDiscipline(disciplineId);
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.edit(title.strip(), notes == null || notes.isBlank() ? null : notes.strip(),
                dueDate, dueTime, plannedDate, disciplineId);
        return summary(task);
    }

    /** Star/unstar the "do" date without touching the deadline; null clears the plan. */
    @Transactional
    public TaskSummary setPlanned(UUID id, LocalDate plannedDate) {
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.planFor(plannedDate);
        return summary(task);
    }

    /** Attach to / detach from a project without touching anything else; null detaches. */
    @Transactional
    public TaskSummary setProject(UUID id, UUID projectId) {
        if (projectId != null && !projects.exists(projectId)) {
            throw new IllegalArgumentException("No project with id " + projectId);
        }
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.assignToProject(projectId);
        return summary(task);
    }

    @Transactional
    public TaskSummary setDone(UUID id, boolean done) {
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (done) {
            task.complete(Instant.now());
        } else {
            task.reopen();
        }
        return summary(task);
    }

    @Transactional
    public void delete(UUID id) {
        if (!tasks.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        tasks.deleteById(id);
    }

    /**
     * Overdue + due-today open tasks, tasks planned for today (or earlier — plans
     * roll forward), plus tasks completed today (zone-local).
     */
    @Transactional(readOnly = true)
    public List<TaskSummary> today(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        Map<UUID, Task> open = new LinkedHashMap<>();
        tasks.findByStatusAndDueDateLessThanEqualOrderByDueDateAscDueTimeAscCreatedAtAsc(TaskStatus.OPEN, today)
                .forEach(task -> open.put(task.getId(), task));
        tasks.findByStatusAndPlannedDateLessThanEqualOrderByPlannedDateAscCreatedAtAsc(TaskStatus.OPEN, today)
                .forEach(task -> open.putIfAbsent(task.getId(), task));
        List<TaskSummary> doneToday = tasks
                .findByStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(TaskStatus.DONE, startOfDay)
                .stream().map(this::summary).toList();
        return concat(open.values().stream().map(this::summary).toList(), doneToday);
    }

    /** Open tasks due within the next {@code days} days after today. */
    @Transactional(readOnly = true)
    public List<TaskSummary> upcoming(ZoneId zone, int days) {
        LocalDate today = LocalDate.now(zone);
        return tasks
                .findByStatusAndDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAsc(
                        TaskStatus.OPEN, today.plusDays(1), today.plusDays(days))
                .stream().map(this::summary).toList();
    }

    /** Dated tasks (any status) with due date inside [from, to] — calendar/board. */
    @Transactional(readOnly = true)
    public List<TaskSummary> range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        return tasks.findByDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAsc(from, to)
                .stream().map(this::summary).toList();
    }

    /** Undated open tasks — "someday". */
    @Transactional(readOnly = true)
    public List<TaskSummary> someday() {
        return tasks.findByStatusAndDueDateIsNullOrderByCreatedAtAsc(TaskStatus.OPEN)
                .stream().map(this::summary).toList();
    }

    /** Open tasks of one discipline — the agenda inside a study block's details. */
    @Transactional(readOnly = true)
    public List<TaskSummary> openByDiscipline(UUID disciplineId) {
        return tasks.findByStatusAndDisciplineIdOrderByDueDateAscDueTimeAscCreatedAtAsc(
                TaskStatus.OPEN, disciplineId)
                .stream().map(this::summary).toList();
    }

    /** Every task of one project (open first is the caller's concern) — the project's agenda. */
    @Transactional(readOnly = true)
    public List<TaskSummary> byProject(UUID projectId) {
        return tasks.findByProjectIdOrderByDueDateAscDueTimeAscCreatedAtAsc(projectId)
                .stream().map(this::summary).toList();
    }

    private void requireDiscipline(UUID disciplineId) {
        if (disciplineId != null && !disciplines.exists(disciplineId)) {
            throw new IllegalArgumentException("No discipline with id " + disciplineId);
        }
    }

    private static List<TaskSummary> concat(List<TaskSummary> a, List<TaskSummary> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private TaskSummary summary(Task task) {
        return new TaskSummary(task.getId(), task.getTitle(), task.getNotes(), task.getStatus(),
                task.getDueDate(), task.getDueTime(), task.getPlannedDate(), task.getCompletedAt(),
                task.getCreatedAt(), task.getDisciplineId(), task.getProjectId());
    }
}
