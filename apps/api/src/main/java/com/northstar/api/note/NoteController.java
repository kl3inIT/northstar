package com.northstar.api.note;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteNotFoundException;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST delivery for the note module (Phase 1). Thin adapter: it only translates
 * HTTP to {@link NoteService} calls — all note logic (wiki-link parse, backlinks,
 * search) lives in {@code :core}. {@code GET /api/notes?q=} runs keyword search;
 * without {@code q} it lists notes newest-first.
 */
@RestController
@RequestMapping("/api/notes")
class NoteController {

    private final NoteService notes;

    NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    List<NoteSummary> list(@RequestParam(name = "q", required = false) String q) {
        return (q == null || q.isBlank()) ? notes.list() : notes.search(q);
    }

    @GetMapping("/{slug}")
    NoteDetail get(@PathVariable String slug) {
        return notes.getBySlug(slug).orElseThrow(() -> new NoteNotFoundException("Note not found: " + slug));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NoteDetail create(@RequestBody CreateNoteRequest request) {
        return notes.create(requireTitle(request.title()), request.folderPath(),
                request.contentMarkdown(), request.tags());
    }

    @PutMapping("/{id}")
    NoteDetail update(@PathVariable UUID id, @RequestBody UpdateNoteRequest request) {
        return notes.update(id, requireTitle(request.title()), request.folderPath(),
                request.contentMarkdown(), request.tags());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        notes.delete(id);
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        return title;
    }
}
