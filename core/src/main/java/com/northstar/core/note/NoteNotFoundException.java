package com.northstar.core.note;

import java.util.UUID;

/** Thrown when a note id/slug does not exist. The api maps this to HTTP 404. */
public class NoteNotFoundException extends RuntimeException {

    public NoteNotFoundException(String message) {
        super(message);
    }

    public NoteNotFoundException(UUID id) {
        super("Note not found: " + id);
    }
}
