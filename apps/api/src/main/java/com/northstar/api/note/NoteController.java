package com.northstar.api.note;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteNotFoundException;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
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
 * REST delivery for the note module. Thin adapter: it only translates HTTP to
 * {@link NoteService} calls — all note logic (wiki-link parse, backlinks,
 * search) lives in {@code :core}. Listing is paged (newest-first); keyword
 * search is its own endpoint so both have a precise OpenAPI schema. Input
 * validation is Bean Validation on the request records; violations become 400
 * ProblemDetail via the global advice.
 */
@RestController
@RequestMapping("/api/notes")
class NoteController {

    private static final int MAX_PAGE_SIZE = 5_000;

    private final NoteService notes;

    NoteController(NoteService notes) {
        this.notes = notes;
    }

    /** Without {@code status}: every note (legacy view). With it: one working-state tab. */
    @GetMapping
    @Operation(operationId = "listNotes")
    PagedModel<NoteSummary> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size,
            @RequestParam(name = "status", required = false) NoteStatus status) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return new PagedModel<>(status == null ? notes.list(pageable) : notes.listByStatus(status, pageable));
    }

    @GetMapping("/search")
    @Operation(operationId = "searchNotes")
    List<NoteSummary> search(@RequestParam("q") String q) {
        return notes.search(q);
    }

    @GetMapping("/by-project")
    @Operation(operationId = "listNotesByProject")
    List<NoteSummary> byProject(@RequestParam("projectId") UUID projectId) {
        return notes.listByProject(projectId);
    }

    @GetMapping("/{slug}")
    @Operation(operationId = "getNote")
    NoteDetail get(@PathVariable String slug) {
        return notes.getBySlug(slug).orElseThrow(() -> new NoteNotFoundException("Note not found: " + slug));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createNote")
    NoteDetail create(@Valid @RequestBody CreateNoteRequest request) {
        return notes.create(request.title(), request.folderPath(), request.contentMarkdown(), request.tags(),
                request.status() == null ? NoteStatus.RESOURCE : request.status(), request.projectId());
    }

    /** Staging verdict ("→ Resources" / "Archive") or a restore. */
    @PatchMapping("/{id}/status")
    @Operation(operationId = "setNoteStatus")
    NoteDetail setStatus(@PathVariable UUID id, @Valid @RequestBody NoteStatusRequest request) {
        return notes.setStatus(id, request.status());
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateNote")
    NoteDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateNoteRequest request) {
        return notes.update(id, request.title(), request.folderPath(),
                request.contentMarkdown(), request.tags(), request.version(), request.projectId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteNote")
    void delete(@PathVariable UUID id) {
        notes.delete(id);
    }
}
