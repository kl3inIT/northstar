package com.northstar.core.note;

/**
 * A reference to another note from a link or backlink. When {@code resolved} is
 * false the target note does not exist yet (a dangling wiki link) and {@code slug}
 * is null — the UI can render it as a "create this note" affordance.
 */
public record NoteRef(String title, String slug, String snippet, boolean resolved) {
}
