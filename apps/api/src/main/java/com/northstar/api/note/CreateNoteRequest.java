package com.northstar.api.note;

import java.util.List;

/**
 * Body for POST /api/notes. {@code contentMarkdown} may be empty and {@code tags}
 * null; {@code folderPath} '' means the note lives at the root. Title is required.
 */
record CreateNoteRequest(String title, String folderPath, String contentMarkdown, List<String> tags) {
}
