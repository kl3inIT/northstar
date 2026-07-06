package com.northstar.core.assistant;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Knowledge-base tools — thin adapters over the note module's public API. */
@Component
class NoteTools implements NorthstarTool {

    private static final String SEARCH_NOTES = """
            Full-text search over the user's personal knowledge base (study notes for \
            IELTS/HSK, scholarship research, project notes, journal). Returns title, slug, \
            folder, tags and a highlighted snippet per hit. Use this BEFORE answering \
            questions about the user's studies, plans or previously saved knowledge.""";

    private static final String GET_NOTE = """
            Read one note in full (Markdown body, tags, outgoing links and backlinks) \
            by its slug — slugs come from search_notes results.""";

    private static final String CREATE_NOTE = """
            Save new knowledge into the user's knowledge base as a Markdown note. Use when \
            the user learns something worth keeping or asks to note something down. Keep a \
            short capture short; reference related existing notes inline as [[Exact Title]] \
            only when clearly related.""";

    private final NoteService notes;

    NoteTools(NoteService notes) {
        this.notes = notes;
    }

    @Tool(name = "search_notes", description = SEARCH_NOTES)
    @McpTool(name = "search_notes", description = SEARCH_NOTES,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<NoteSummary> searchNotes(
            @ToolParam(description = "Plain keyword query; quoted \"phrases\" and -exclusions are supported")
            @McpToolParam(description = "Plain keyword query; quoted \"phrases\" and -exclusions are supported",
                    required = true) String query) {
        return notes.search(query);
    }

    @Tool(name = "get_note", description = GET_NOTE)
    @McpTool(name = "get_note", description = GET_NOTE,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    NoteDetail getNote(
            @ToolParam(description = "The note's slug, e.g. 'kinh-nghiem-apply-hoc-bong'")
            @McpToolParam(description = "The note's slug, e.g. 'kinh-nghiem-apply-hoc-bong'",
                    required = true) String slug) {
        return notes.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No note with slug '" + slug + "' — find slugs via search_notes."));
    }

    @Tool(name = "create_note", description = CREATE_NOTE)
    @McpTool(name = "create_note", description = CREATE_NOTE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    NoteDetail createNote(
            @ToolParam(description = "Short, specific title")
            @McpToolParam(description = "Short, specific title", required = true) String title,
            @ToolParam(description = "Folder path like 'English/IELTS'; empty or omitted = root", required = false)
            @McpToolParam(description = "Folder path like 'English/IELTS'; empty or omitted = root",
                    required = false) String folderPath,
            @ToolParam(description = "Note body in Markdown")
            @McpToolParam(description = "Note body in Markdown", required = true) String contentMarkdown,
            @ToolParam(description = "1-4 lowercase tags, reusing the user's existing tags where possible", required = false)
            @McpToolParam(description = "1-4 lowercase tags, reusing the user's existing tags where possible",
                    required = false) List<String> tags) {
        // Machine-drafted → STAGING: the user reviews it in the Notes staging tab.
        return notes.create(title, folderPath, contentMarkdown, tags, NoteStatus.STAGING);
    }
}
