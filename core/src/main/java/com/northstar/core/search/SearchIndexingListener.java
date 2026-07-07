package com.northstar.core.search;

import com.northstar.core.attachment.AttachmentStored;
import com.northstar.core.note.NoteDeleted;
import com.northstar.core.note.NoteSaved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the vector index in step with the note and attachment modules, off the
 * request thread: {@code @ApplicationModuleListener} = after-commit + async +
 * its own transaction, durable through the Event Publication Registry (V15) —
 * an embedding call that fails or a crash mid-flight leaves the publication
 * incomplete for redelivery instead of silently losing the source.
 */
@Component
class SearchIndexingListener {

    private final SearchService search;

    SearchIndexingListener(SearchService search) {
        this.search = search;
    }

    @ApplicationModuleListener
    void on(NoteSaved event) {
        search.index(event.noteId());
    }

    @ApplicationModuleListener
    void on(NoteDeleted event) {
        search.remove(event.noteId());
    }

    @ApplicationModuleListener
    void on(AttachmentStored event) {
        search.indexAttachment(event.id());
    }
}
