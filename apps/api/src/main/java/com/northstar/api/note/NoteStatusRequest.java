package com.northstar.api.note;

import com.northstar.core.note.NoteStatus;
import jakarta.validation.constraints.NotNull;

/** Body for PATCH /api/notes/{id}/status — a staging verdict or a restore. */
record NoteStatusRequest(@NotNull NoteStatus status) {
}
