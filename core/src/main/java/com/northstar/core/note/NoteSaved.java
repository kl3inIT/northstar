package com.northstar.core.note;

import java.util.UUID;

/**
 * A note was created or its content/status changed. Carries only the id — the
 * listener reads the current state through {@link NoteService}, so a burst of
 * saves converges on the latest version instead of replaying stale payloads.
 */
public record NoteSaved(UUID noteId) {
}
