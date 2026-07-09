package com.northstar.api.project;

import com.northstar.core.project.ProjectService;
import com.northstar.core.project.ProjectSummary;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for the project module. Milestone writes address the parent
 * project (the aggregate owns its stages) and every write returns the full
 * refreshed project, so clients never stitch state together.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    @Operation(operationId = "listProjects")
    List<ProjectSummary> list(@RequestParam(name = "disciplineId", required = false) UUID disciplineId) {
        return disciplineId == null ? projects.list() : projects.listByDiscipline(disciplineId);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProject")
    ProjectSummary find(@PathVariable UUID id) {
        return projects.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createProject")
    ProjectSummary create(@Valid @RequestBody ProjectRequest request) {
        return projects.create(request.name(), request.notes(), request.disciplineId(),
                request.startDate(), request.targetDate());
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProject")
    ProjectSummary update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return projects.update(id, request.name(), request.notes(), request.disciplineId(),
                request.startDate(), request.targetDate());
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "setProjectStatus")
    ProjectSummary setStatus(@PathVariable UUID id, @RequestBody ProjectStatusRequest request) {
        return projects.setDone(id, request.done());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteProject")
    void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    @PostMapping("/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addProjectMilestone")
    ProjectSummary addMilestone(@PathVariable UUID id, @Valid @RequestBody MilestoneRequest request) {
        return projects.addMilestone(id, request.name(), request.dueDate());
    }

    @PutMapping("/{id}/milestones/{milestoneId}")
    @Operation(operationId = "updateProjectMilestone")
    ProjectSummary editMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId,
            @Valid @RequestBody MilestoneRequest request) {
        return projects.editMilestone(id, milestoneId, request.name(), request.dueDate());
    }

    /** Tick/untick one stage — progress recomputes from the milestones. */
    @PatchMapping("/{id}/milestones/{milestoneId}/toggle")
    @Operation(operationId = "toggleProjectMilestone")
    ProjectSummary toggleMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId) {
        return projects.toggleMilestone(id, milestoneId);
    }

    @DeleteMapping("/{id}/milestones/{milestoneId}")
    @Operation(operationId = "removeProjectMilestone")
    ProjectSummary removeMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId) {
        return projects.removeMilestone(id, milestoneId);
    }
}
