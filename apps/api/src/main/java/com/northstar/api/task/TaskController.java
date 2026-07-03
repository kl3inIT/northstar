package com.northstar.api.task;

import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
    List<TaskSummary> today(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        return tasks.today(zone(tz));
    }

    @GetMapping("/upcoming")
    List<TaskSummary> upcoming(
            @RequestHeader(name = "X-Timezone", required = false) String tz,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return tasks.upcoming(zone(tz), Math.clamp(days, 1, 60));
    }

    @GetMapping("/someday")
    List<TaskSummary> someday() {
        return tasks.someday();
    }

    /** Open tasks of one discipline — the agenda inside a study block's details. */
    @GetMapping("/open")
    List<TaskSummary> openByDiscipline(@RequestParam("disciplineId") UUID disciplineId) {
        return tasks.openByDiscipline(disciplineId);
    }

    @GetMapping("/range")
    List<TaskSummary> range(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid range");
        }
        return tasks.range(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskSummary create(@Valid @RequestBody TaskRequest request) {
        return tasks.create(request.title(), request.notes(), request.dueDate(), request.dueTime(),
                request.disciplineId());
    }

    @PutMapping("/{id}")
    TaskSummary update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        return tasks.update(id, request.title(), request.notes(), request.dueDate(), request.dueTime(),
                request.disciplineId());
    }

    @PatchMapping("/{id}/status")
    TaskSummary setStatus(@PathVariable UUID id, @RequestBody TaskStatusRequest request) {
        return tasks.setDone(id, request.done());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
