package com.northstar.core.note;

/**
 * A note's title and full Markdown body, without the wiki-link graph — for cheap
 * folder listings (e.g. the assistant's memory store) that need the whole body
 * but none of the backlink/outgoing-link resolution {@link NoteDetail} carries.
 */
public record NoteBody(String title, String markdown) {
}
