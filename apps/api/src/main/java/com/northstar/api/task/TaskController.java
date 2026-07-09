package com.northstar.api.task;

import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST delivery for the task module. "Today" is timezone-sensitive, so list
 * endpoints take the browser zone via the {@code X-Timezone} header (IANA id),
 * defaulting to the server zone. Body validation is Bean Validation on
 * {@link TaskRequest}; violations become 400 ProblemDetail via the global advice.
 */
@RestController
@RequestMapping("/api/tasks")
class TaskController {

    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/today")
    @Operation(operationId = "listTodayTasks")
    List<TaskSummary> today(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return tasks.today(zone(tz));
    }

    @GetMapping("/upcoming")
    @Operation(operationId = "listUpcomingTasks")
    List<TaskSummary> upcoming(
            @RequestHeader(name = "X-Timezone", required = false) String tz,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return tasks.upcoming(zone(tz), Math.clamp(days, 1, 60));
    }

    @GetMapping("/someday")
    @Operation(operationId = "listSomedayTasks")
    List<TaskSummary> someday() {
        return tasks.someday();
    }

    /** Open tasks of one discipline — the agenda inside a study block's details. */
    @GetMapping("/open")
    @Operation(operationId = "listOpenTasksByDiscipline")
    List<TaskSummary> openByDiscipline(@RequestParam("disciplineId") UUID disciplineId) {
        return tasks.openByDiscipline(disciplineId);
    }

    // A year view plus margin. The calendar page fetches tasks over the visible
    // window widened 90 days backwards (overdue open tasks roll onto today), so
    // year view asks for ~365+90 days — the cap must clear that or the whole
    // calendar 400s. Events aren't back-widened, hence their tighter 400-day cap.
    private static final long MAX_RANGE_DAYS = 500;

    @GetMapping("/range")
    @Operation(operationId = "listTasksByDateRange")
    List<TaskSummary> range(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid range");
        }
        return tasks.range(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTask")
    TaskSummary create(@Valid @RequestBody TaskRequest request) {
        return tasks.create(request.title(), request.notes(), request.dueDate(), request.dueTime(),
                request.plannedDate(), request.disciplineId(), request.projectId());
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTask")
    TaskSummary update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        return tasks.update(id, request.title(), request.notes(), request.dueDate(), request.dueTime(),
                request.plannedDate(), request.disciplineId());
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "setTaskStatus")
    TaskSummary setStatus(@PathVariable UUID id, @RequestBody TaskStatusRequest request) {
        return tasks.setDone(id, request.done());
    }

    /** Star/unstar the "do" day (null clears); never moves the deadline. */
    @PatchMapping("/{id}/planned")
    @Operation(operationId = "setTaskPlannedDate")
    TaskSummary setPlanned(@PathVariable UUID id, @RequestBody TaskPlannedRequest request) {
        return tasks.setPlanned(id, request.plannedDate());
    }

    /** Every task of one project — the project's agenda. */
    @GetMapping("/by-project")
    @Operation(operationId = "listTasksByProject")
    List<TaskSummary> byProject(@RequestParam("projectId") UUID projectId) {
        return tasks.byProject(projectId);
    }

    /** Attach to / detach from a project (null detaches); touches nothing else. */
    @PatchMapping("/{id}/project")
    @Operation(operationId = "setTaskProject")
    TaskSummary setProject(@PathVariable UUID id, @RequestBody TaskProjectRequest request) {
        return tasks.setProject(id, request.projectId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTask")
    void delete(@PathVariable UUID id) {
        tasks.delete(id);
    }

    private static ZoneId zone(String tz) {
        try {
            return tz == null ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
