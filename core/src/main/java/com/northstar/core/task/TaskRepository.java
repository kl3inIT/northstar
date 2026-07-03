package com.northstar.core.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Task}. Other modules go through {@link TaskService}. */
interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Open tasks due on or before {@code date} — Today + Overdue in one query. */
    List<Task> findByStatusAndDueDateLessThanEqualOrderByDueDateAscDueTimeAscCreatedAtAsc(
            TaskStatus status, LocalDate date);

    /** Open tasks in a date window — the Upcoming list. */
    List<Task> findByStatusAndDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAsc(
            TaskStatus status, LocalDate from, LocalDate to);

    /** Undated open tasks — "someday". */
    List<Task> findByStatusAndDueDateIsNullOrderByCreatedAtAsc(TaskStatus status);

    /** Tasks completed on/after {@code since} — Today keeps just-finished items visible. */
    List<Task> findByStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
            TaskStatus status, java.time.Instant since);

    /** Dated tasks in a window, any status — calendar cells and board columns. */
    List<Task> findByDueDateBetweenOrderByDueDateAscDueTimeAscCreatedAtAsc(LocalDate from, LocalDate to);

    /** Open tasks of one discipline — the agenda inside a study block's details. */
    List<Task> findByStatusAndDisciplineIdOrderByDueDateAscDueTimeAscCreatedAtAsc(
            TaskStatus status, UUID disciplineId);
}
