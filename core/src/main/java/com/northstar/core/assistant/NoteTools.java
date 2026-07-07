package com.northstar.core.assistant;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.search.SearchResult;
import com.northstar.core.search.SearchService;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Knowledge-base tools — thin adapters over the note module's public API. */
@Component
class NoteTools implements NorthstarTool {

    private static final String SEARCH_KNOWLEDGE = """
            Search the user's personal knowledge base AND uploaded files (study notes \
            for IELTS/HSK, scholarship research, project notes, journal, plus PDFs, \
            documents and images they saved). Hybrid retrieval: exact keywords AND \
            meaning both match, so a paraphrased question ("cách viết mở bài IELTS") \
            finds notes that never contain those words. Each hit has source ('note' or \
            'file'), title, url, snippet, and for notes a slug. Use this BEFORE \
            answering questions about the user's studies, plans or previously saved \
            knowledge. Read a promising note hit in full with get_note (its slug); \
            file hits cannot be opened with get_note — use their snippet. When your \
            answer draws on a hit, cite it inline as a markdown link: [title](url). \
            If results look off, retry once with different phrasing before concluding \
            the source does not exist.""";

    private static final String GET_NOTE = """
            Read one note in full (Markdown body, tags, outgoing links and backlinks) \
            by its slug — slugs come from search_knowledge results.""";

    private static final String CREATE_NOTE = """
            Save new knowledge into the user's knowledge base as a Markdown note. Use when \
            the user learns something worth keeping or asks to note something down. Keep a \
            short capture short; reference related existing notes inline as [[Exact Title]] \
            only when clearly related.""";

    private static final String UPDATE_NOTE = """
            Edit an existing note: retitle, move to another folder, retag, REPLACE the \
            whole Markdown body, or change its working state (status RESOURCE = approved \
            knowledge, STAGING = back to review, ARCHIVED = soft-delete, restorable — when \
            the user asks to delete a note, archive it, never lose the text). Use for \
            corrections and rewrites; to add to the end without retyping the body, use \
            append_to_note. Only pass the fields to change.""";

    private static final String APPEND_TO_NOTE = """
            Add Markdown to the END of an existing note, keeping everything already there \
            ('thêm vào note X ...'). Returns the updated note.""";

    private static final int SEARCH_LIMIT = 8;

    private final NoteService notes;
    private final SearchService search;

    NoteTools(NoteService notes, SearchService search) {
        this.notes = notes;
        this.search = search;
    }

    @Tool(name = "search_knowledge", description = SEARCH_KNOWLEDGE)
    @McpTool(name = "search_knowledge", description = SEARCH_KNOWLEDGE,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<SearchResult> searchKnowledge(
            @ToolParam(description = "What to look for — plain keywords or a natural-language question, in the note's language where known")
            @McpToolParam(description = "What to look for — plain keywords or a natural-language question, in the note's language where known",
                    required = true) String query) {
        return search.search(query, SEARCH_LIMIT);
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
                        "No note with slug '" + slug + "' — find slugs via search_knowledge."));
    }

    @Tool(name = "update_note", description = UPDATE_NOTE)
    @McpTool(name = "update_note", description = UPDATE_NOTE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    NoteDetail updateNote(
            @ToolParam(description = "The note's slug, from search_knowledge/get_note")
            @McpToolParam(description = "The note's slug, from search_knowledge/get_note",
                    required = true) String slug,
            @ToolParam(description = "New title; pass '' or omit to keep", required = false)
            @McpToolParam(description = "New title; pass '' or omit to keep", required = false) String title,
            @ToolParam(description = "New folder path like 'English/IELTS'; pass '' or omit to keep, 'none' for root", required = false)
            @McpToolParam(description = "New folder path like 'English/IELTS'; pass '' or omit to keep, 'none' for root",
                    required = false) String folderPath,
            @ToolParam(description = "REPLACEMENT Markdown body (full text, not a diff); pass '' or omit to keep — to add to the end use append_to_note", required = false)
            @McpToolParam(description = "REPLACEMENT Markdown body (full text, not a diff); pass '' or omit to keep — to add to the end use append_to_note",
                    required = false) String contentMarkdown,
            @ToolParam(description = "Replacement tag list (1-4 lowercase tags); pass [] or omit to keep", required = false)
            @McpToolParam(description = "Replacement tag list (1-4 lowercase tags); pass [] or omit to keep",
                    required = false) List<String> tags,
            @ToolParam(description = "New working state (RESOURCE = approved, STAGING = back to review, ARCHIVED = soft-delete); omit to keep", required = false)
            @McpToolParam(description = "New working state (RESOURCE = approved, STAGING = back to review, ARCHIVED = soft-delete); omit to keep",
                    required = false) NoteStatus status) {
        NoteDetail current = bySlug(slug);
        NoteDetail updated = notes.update(current.id(),
                title == null || title.isBlank() ? current.title() : title,
                ToolSupport.resolve(folderPath, current.folderPath(), String::strip),
                contentMarkdown == null || contentMarkdown.isBlank()
                        ? current.contentMarkdown() : contentMarkdown,
                tags == null || tags.isEmpty() ? current.tags() : tags,
                null);
        if (status != null) {
            updated = notes.setStatus(current.id(), status);
        }
        return updated;
    }

    @Tool(name = "append_to_note", description = APPEND_TO_NOTE)
    @McpTool(name = "append_to_note", description = APPEND_TO_NOTE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    NoteDetail appendToNote(
            @ToolParam(description = "The note's slug, from search_knowledge/get_note")
            @McpToolParam(description = "The note's slug, from search_knowledge/get_note",
                    required = true) String slug,
            @ToolParam(description = "Markdown to add at the end of the note body")
            @McpToolParam(description = "Markdown to add at the end of the note body",
                    required = true) String markdown) {
        NoteDetail current = bySlug(slug);
        String body = current.contentMarkdown() == null || current.contentMarkdown().isBlank()
                ? markdown.strip()
                : current.contentMarkdown().stripTrailing() + "\n\n" + markdown.strip();
        return notes.update(current.id(), current.title(), current.folderPath(), body,
                current.tags(), null);
    }

    private NoteDetail bySlug(String slug) {
        return notes.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No note with slug '" + slug + "' — find slugs via search_knowledge."));
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
