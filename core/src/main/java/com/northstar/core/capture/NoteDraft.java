package com.northstar.core.capture;

import java.util.List;

/**
 * LLM-produced suggestion for a note — mirrors what the note editor needs. Never
 * persisted directly: the user reviews/edits it, then the note module creates the
 * real note from the confirmed values.
 */
public record NoteDraft(
        String title,
        String folderPath,
        List<String> tags,
        String contentMarkdown) {
}
