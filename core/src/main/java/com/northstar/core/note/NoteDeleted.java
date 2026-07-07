package com.northstar.core.note;

import java.util.UUID;

/**
 * A note was deleted. The search module drops its derived vector rows on this —
 * vector_store has no FK to note, so cleanup is event-driven, not cascade.
 */
public record NoteDeleted(UUID noteId) {
}
