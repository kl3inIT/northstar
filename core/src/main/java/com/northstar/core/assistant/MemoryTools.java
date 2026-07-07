package com.northstar.core.assistant;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The assistant's writable long-term memory, following the Claude memory-tool
 * contract as implemented by spring-ai-agent-utils' AutoMemoryTools (Apache-2.0,
 * Christian Tzolov) — same six operations, same two-step save protocol, same
 * line-number semantics — with the file system swapped for the note module: each
 * "memory file" is a note in the dedicated {@value #MEMORY_FOLDER} folder, so
 * memories are visible in the UI, embedded into the vector store like any other
 * note (search_knowledge recalls them by meaning), and back up with the database.
 *
 * <p>The store is flat: a path like {@code feedback_testing.md} maps to the note
 * titled {@code feedback_testing} in the Memory folder — folder scoping replaces
 * the library's path-traversal guard. {@code MEMORY.md} maps to the index note,
 * whose body the api injects into every turn via {@link #promptSection()}.
 *
 * <p>Deliberately {@code @Tool}-only (not {@code @McpTool}): the contract depends
 * on the memory system prompt the in-app assistant injects; MCP clients never see
 * that prompt, so publishing the tools there would invite misuse of the index.
 */
@Component
public class MemoryTools implements NorthstarTool {

    public static final String MEMORY_FOLDER = "Memory";
    static final String INDEX_NAME = "MEMORY";

    private static final List<String> MEMORY_TAGS = List.of("memory");
    private static final String SYSTEM_PROMPT = loadPrompt();

    private static final String VIEW = """
            View a memory file or list the persistent memory store.

            Usage:
            - An empty path or "/" lists every memory file with its size.
            - A file path (e.g. 'feedback_testing.md') returns that file's contents with line numbers.
            - The store is flat: file names only, no subdirectories.
            - 'MEMORY.md' is the always-loaded index of all memories — its current content is already
              in your context, so read it here only when you suspect the in-context copy is stale.
            - Optionally supply a line range 'start,end' to page through large files.

            Memory file structure: each memory file uses YAML frontmatter:
              ---
              name: <short name>
              description: <one-line description used to judge relevance in future conversations>
              type: <user | feedback | project | reference>
              ---
              <memory content>""";

    private static final String CREATE = """
            Create a new file in the persistent memory store.

            Usage:
            - The file must NOT already exist; use MemoryStrReplace to update existing files.
            - The store is flat: use a descriptive file name (e.g. 'feedback_reviews.md'), no subdirectories.
            - Saving a memory is a TWO-STEP process:
                Step 1 — call MemoryCreate to write the memory file with YAML frontmatter
                         (name, description, type: user | feedback | project | reference).
                Step 2 — call MemoryInsert (or MemoryStrReplace) to add a pointer line to MEMORY.md.
                        MEMORY.md entry format: "- [Title](filename.md) — one-line hook (<=150 chars)"
            - Check the memory index first to avoid duplicate memories; update an existing file instead.
            - For feedback/project types, structure the body as the fact, then a **Why:** line and a
              **How to apply:** line.""";

    private static final String STR_REPLACE = """
            Replace an exact string in an existing memory file.

            Usage:
            - old_str must match exactly (including whitespace and newlines) and appear exactly once;
              if it appears more than once, include more surrounding context to disambiguate.
            - new_str can be empty to delete the matched text.
            - Returns a snippet of the file around the edited location with line numbers.

            Common uses: updating stale memory content, keeping frontmatter name/description in sync
            with the body, and editing MEMORY.md index lines after a rename, description change or
            delete.""";

    private static final String INSERT = """
            Insert text at a specific line number in an existing memory file.

            Usage:
            - insert_line is the line number AFTER which the new text is inserted (0 inserts at the
              beginning; the total line count appends to the end). Lines are 1-indexed.
            - Main use: appending the pointer line to MEMORY.md after MemoryCreate (Step 2 of the
              two-step save). Read MEMORY.md via MemoryView first to get the current line count.""";

    private static final String DELETE = """
            Delete a file from the persistent memory store.

            Usage:
            - Irreversible; use when a memory is confirmed stale, wrong, or superseded — do not
              leave outdated entries.
            - The MEMORY.md index itself cannot be deleted.
            - After deleting a memory file, always remove its line from MEMORY.md using
              MemoryStrReplace to keep the index accurate.""";

    private static final String RENAME = """
            Rename a file within the persistent memory store.

            Usage:
            - The source file must exist; the destination name must NOT already exist.
            - The store is flat: both arguments are file names, no subdirectories.
            - After renaming, update the file's pointer in MEMORY.md using MemoryStrReplace so the
              index link stays correct.""";

    private final NoteService notes;

    MemoryTools(NoteService notes) {
        this.notes = notes;
    }

    /**
     * The api's per-turn system-prompt contribution: the memory rules plus the
     * LIVE index body, so the model starts every conversation already knowing
     * what it remembers without spending a tool call.
     */
    public String promptSection() {
        String index = byName(INDEX_NAME).map(NoteDetail::contentMarkdown)
                .filter(body -> !body.isBlank())
                .orElse("(no memories saved yet)");
        return SYSTEM_PROMPT + "\n\n<memory-index>\n" + index + "\n</memory-index>";
    }

    @Tool(name = "MemoryView", description = VIEW)
    String memoryView(
            @ToolParam(description = "File name to view (e.g. 'feedback_testing.md'), or empty string / '/' to list the whole store.")
            String path,
            @ToolParam(description = "Optional line range as 'start,end' (e.g. '1,50') when viewing a file. Ignored for the listing.",
                    required = false) String viewRange) {
        if (isRoot(path)) {
            return listStore();
        }
        String name;
        try {
            name = nameOf(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        return byName(name)
                .map(note -> readFile(name, note.contentMarkdown(), viewRange))
                .orElse("Error: Path does not exist: " + path);
    }

    @Tool(name = "MemoryCreate", description = CREATE)
    String memoryCreate(
            @ToolParam(description = "File name for the new memory (e.g. 'feedback_testing.md'). Use descriptive names that reflect the topic.")
            String path,
            @ToolParam(description = "Full file content: the YAML frontmatter block (name, description, type) followed by the memory body.")
            String fileText) {
        String name;
        try {
            name = nameOf(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        if (byName(name).isPresent()) {
            return "Error: File already exists: " + path
                    + ". Use MemoryStrReplace to modify existing files.";
        }
        String body = fileText == null ? "" : fileText;
        // Born RESOURCE, not STAGING: memory must be live immediately, it is the
        // model's own state, not a machine draft awaiting the user's review.
        notes.create(name, MEMORY_FOLDER, body, MEMORY_TAGS, NoteStatus.RESOURCE);
        return "Successfully created file: " + path + " (" + body.length() + " bytes)";
    }

    @Tool(name = "MemoryStrReplace", description = STR_REPLACE)
    String memoryStrReplace(
            @ToolParam(description = "File name to edit. Use 'MEMORY.md' to update the index.")
            String path,
            @ToolParam(description = "The exact text to find and replace. Must appear exactly once in the file.")
            String oldStr,
            @ToolParam(description = "The replacement text. Use empty string to delete the matched text.")
            String newStr) {
        String name;
        try {
            name = nameOf(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        Optional<NoteDetail> note = byNameForEdit(name);
        if (note.isEmpty()) {
            return "Error: File does not exist: " + path;
        }
        String content = note.get().contentMarkdown();
        String target = oldStr == null ? "" : oldStr;
        int occurrences = countOccurrences(content, target);
        if (occurrences == 0) {
            return "Error: old_str not found in file: " + path;
        }
        if (occurrences > 1) {
            return "Error: old_str appears %d times in the file. Provide more surrounding context to make it unique."
                    .formatted(occurrences);
        }
        String replacement = newStr == null ? "" : newStr;
        String updated = replaceFirst(content, target, replacement);
        save(note.get(), updated);
        if (replacement.isBlank()) {
            return "Successfully deleted matched text from %s.".formatted(path);
        }
        return "Successfully edited %s. Here's a snippet of the result:\n%s"
                .formatted(path, editSnippet(updated, replacement));
    }

    @Tool(name = "MemoryInsert", description = INSERT)
    String memoryInsert(
            @ToolParam(description = "File name to modify. Use 'MEMORY.md' to append an index entry.")
            String path,
            @ToolParam(description = "The line number after which to insert the text. Use 0 to insert before the first line; pass the total line count to append at the end.")
            Integer insertLine,
            @ToolParam(description = "The text to insert. For MEMORY.md entries use: '- [Title](filename.md) — one-line hook'")
            String insertText) {
        String name;
        try {
            name = nameOf(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        Optional<NoteDetail> note = byNameForEdit(name);
        if (note.isEmpty()) {
            return "Error: File does not exist: " + path;
        }
        if (insertLine == null || insertLine < 0) {
            return "Error: insert_line must be a non-negative integer";
        }
        String content = note.get().contentMarkdown();
        List<String> lines = new ArrayList<>(content.lines().toList());
        if (insertLine > lines.size()) {
            return "Error: insert_line %d exceeds file length of %d lines".formatted(insertLine, lines.size());
        }
        lines.add(insertLine, insertText == null ? "" : insertText);
        save(note.get(), String.join("\n", lines) + (content.endsWith("\n") ? "\n" : ""));
        return "Successfully inserted text at line " + insertLine + " in: " + path;
    }

    @Tool(name = "MemoryDelete", description = DELETE)
    String memoryDelete(
            @ToolParam(description = "File name to delete. Remember to also remove its MEMORY.md entry afterwards.")
            String path) {
        String name;
        try {
            name = nameOf(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        if (name.equalsIgnoreCase(INDEX_NAME)) {
            return "Error: Cannot delete the MEMORY.md index.";
        }
        Optional<NoteDetail> note = byName(name);
        if (note.isEmpty()) {
            return "Error: Path does not exist: " + path;
        }
        notes.delete(note.get().id());
        return "Successfully deleted file: " + path;
    }

    @Tool(name = "MemoryRename", description = RENAME)
    String memoryRename(
            @ToolParam(description = "Current file name.") String oldPath,
            @ToolParam(description = "New file name. Remember to update the MEMORY.md link afterwards.")
            String newPath) {
        String oldName;
        String newName;
        try {
            oldName = nameOf(oldPath);
            newName = nameOf(newPath);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        if (oldName.equalsIgnoreCase(INDEX_NAME)) {
            return "Error: Cannot rename the MEMORY.md index.";
        }
        Optional<NoteDetail> source = byName(oldName);
        if (source.isEmpty()) {
            return "Error: Source path does not exist: " + oldPath;
        }
        if (byName(newName).isPresent()) {
            return "Error: Destination path already exists: " + newPath;
        }
        NoteDetail note = source.get();
        notes.update(note.id(), newName, note.folderPath(), note.contentMarkdown(), note.tags(), null);
        return "Successfully renamed '%s' to '%s'".formatted(oldPath, newPath);
    }

    // --- notes-backed "file system" -----------------------------------------

    /**
     * Tool-facing path → note title: '.md' stripped, flat only. The folder scope
     * ({@link #MEMORY_FOLDER}) is what keeps the model out of the user's notes —
     * the equivalent of the library's path-traversal guard.
     */
    private static String nameOf(String path) {
        String name = path == null ? "" : path.strip();
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("A file name is required (e.g. 'feedback_testing.md').");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException(
                    "The memory store is flat — use a plain descriptive file name, no subdirectories.");
        }
        return name;
    }

    private static boolean isRoot(String path) {
        String p = path == null ? "" : path.strip();
        return p.isEmpty() || p.equals("/") || p.equals(".");
    }

    private Optional<NoteDetail> byName(String name) {
        return notes.listByFolder(MEMORY_FOLDER).stream()
                .filter(summary -> summary.title().equalsIgnoreCase(name))
                .findFirst()
                .flatMap(summary -> notes.findById(summary.id()));
    }

    /**
     * Like {@link #byName} but bootstraps the index on first use: the very
     * first two-step save targets MEMORY.md before anything created it.
     */
    private Optional<NoteDetail> byNameForEdit(String name) {
        Optional<NoteDetail> existing = byName(name);
        if (existing.isEmpty() && name.equalsIgnoreCase(INDEX_NAME)) {
            return Optional.of(notes.create(INDEX_NAME, MEMORY_FOLDER,
                    "# Memory index\n", MEMORY_TAGS, NoteStatus.RESOURCE));
        }
        return existing;
    }

    private void save(NoteDetail note, String body) {
        notes.update(note.id(), note.title(), note.folderPath(), body, note.tags(), null);
    }

    private String listStore() {
        StringBuilder sb = new StringBuilder("Contents of /:\n\n");
        List<NoteSummary> entries = notes.listByFolder(MEMORY_FOLDER);
        if (entries.isEmpty()) {
            return sb.append("  (empty — no memories saved yet)\n").toString();
        }
        for (var entry : entries) {
            int size = notes.findById(entry.id())
                    .map(d -> d.contentMarkdown().getBytes(StandardCharsets.UTF_8).length)
                    .orElse(0);
            sb.append("  ").append(entry.title()).append(".md (").append(size).append(" bytes)\n");
        }
        return sb.toString();
    }

    // --- verbatim AutoMemoryTools presentation semantics ---------------------

    private static String readFile(String name, String content, String viewRange) {
        List<String> allLines = content.lines().toList();
        int totalLines = allLines.size();
        int startLine = 1;
        int endLine = totalLines;
        if (viewRange != null && !viewRange.isBlank()) {
            String[] parts = viewRange.split(",");
            if (parts.length != 2) {
                return "Error: view_range must be 'start,end' (e.g. '1,50')";
            }
            try {
                startLine = Math.max(1, Integer.parseInt(parts[0].strip()));
                endLine = Math.min(totalLines, Integer.parseInt(parts[1].strip()));
            } catch (NumberFormatException e) {
                return "Error: view_range must be 'start,end' integers (e.g. '1,50')";
            }
        }
        StringBuilder sb = new StringBuilder(
                "File: %s.md\nLines %d-%d of %d\n\n".formatted(name, startLine, endLine, totalLines));
        for (int i = startLine - 1; i < endLine; i++) {
            sb.append("%6d\t%s\n".formatted(i + 1, allLines.get(i)));
        }
        return sb.toString();
    }

    private static int countOccurrences(String text, String substring) {
        if (substring.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    private static String replaceFirst(String text, String oldStr, String newStr) {
        int index = text.indexOf(oldStr);
        return index == -1 ? text
                : text.substring(0, index) + newStr + text.substring(index + oldStr.length());
    }

    /** The edited region with five lines of context either side, line-numbered. */
    private static String editSnippet(String fileContent, String newStr) {
        String[] lines = fileContent.split("\n", -1);
        String[] newLines = newStr.split("\n", -1);
        int editStart = -1;
        int editEnd = -1;
        for (int i = 0; i < lines.length; i++) {
            if (newLines.length > 0 && lines[i].contains(newLines[0])) {
                boolean matches = true;
                for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
                    if (!lines[i + j].contains(newLines[j])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    editStart = i;
                    editEnd = i + newLines.length - 1;
                    break;
                }
            }
        }
        if (editStart == -1) {
            editStart = 0;
            editEnd = Math.min(10, lines.length - 1);
        }
        int from = Math.max(0, editStart - 5);
        int to = Math.min(lines.length - 1, editEnd + 5);
        StringBuilder snippet = new StringBuilder();
        for (int i = from; i <= to; i++) {
            snippet.append("%6d→%s".formatted(i + 1, lines[i]));
            if (i < to) {
                snippet.append("\n");
            }
        }
        return snippet.toString();
    }

    private static String loadPrompt() {
        try {
            return new DefaultResourceLoader()
                    .getResource("classpath:/prompts/memory-tools.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing memory system prompt resource", e);
        }
    }
}
