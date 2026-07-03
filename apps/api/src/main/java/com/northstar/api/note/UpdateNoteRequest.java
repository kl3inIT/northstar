package com.northstar.api.note;

import java.util.List;

/** Body for PUT /api/notes/{id}. */
record UpdateNoteRequest(String title, String folderPath, String contentMarkdown, List<String> tags) {
}
