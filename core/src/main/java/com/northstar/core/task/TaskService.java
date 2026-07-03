package com.northstar.core.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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

    TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public TaskSummary create(String title, String notes, LocalDate dueDate, LocalTime dueTime) {
        Task task = new Task(UUID.randomUUID(), title.strip(),
                notes == null || notes.isBlank() ? null : notes.strip(),
                dueDate, dueTime, Instant.now());
        tasks.save(task);
        return summary(task);
    }

    @Transactional
    public TaskSummary update(UUID id, String title, String notes, LocalDate dueDate, LocalTime dueTime) {
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        task.edit(title.strip(), notes == null || notes.isBlank() ? null : notes.strip(),
                dueDate, dueTime, Instant.now());
        return summary(task);
    }

    @Transactional
    public TaskSummary setDone(UUID id, boolean done) {
        Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (done) {
            task.complete(Instant.now());
        } else {
            task.reopen(Instant.now());
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

    /** Overdue + due-today open tasks, plus tasks completed today (zone-local). */
    @Transactional(readOnly = true)
    public List<TaskSummary> today(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        List<TaskSummary> open = tasks
                .findByStatusAndDueDateLessThanEqualOrderByDueDateAscDueTimeAscCreatedAtAsc(TaskStatus.OPEN, today)
                .stream().map(this::summary).toList();
        List<TaskSummary> doneToday = tasks
                .findByStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(TaskStatus.DONE, startOfDay)
                .stream().map(this::summary).toList();
        return concat(open, doneToday);
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

    private static List<TaskSummary> concat(List<TaskSummary> a, List<TaskSummary> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private TaskSummary summary(Task task) {
        return new TaskSummary(task.getId(), task.getTitle(), task.getNotes(), task.getStatus(),
                task.getDueDate(), task.getDueTime(), task.getCompletedAt(), task.getCreatedAt());
    }
}
