package com.northstar.core.note;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A wiki link derived from a source note's Markdown body. {@code targetNoteId} is
 * null while the linked title does not resolve to an existing note yet (a dangling
 * link that keeps its raw {@code targetTitle}); it is filled in when a note with
 * that title is later created. Package-private: only {@link NoteService} manages
 * these — other modules and apps go through the service.
 */
@Entity
@Table(name = "note_link")
class NoteLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_note_id", nullable = false)
    private UUID sourceNoteId;

    @Column(name = "target_note_id")
    private UUID targetNoteId;

    @Column(name = "target_title", nullable = false)
    private String targetTitle;

    protected NoteLink() {
        // for JPA
    }

    NoteLink(UUID id, UUID sourceNoteId, UUID targetNoteId, String targetTitle) {
        this.id = id;
        this.sourceNoteId = sourceNoteId;
        this.targetNoteId = targetNoteId;
        this.targetTitle = targetTitle;
    }

    UUID getSourceNoteId() {
        return sourceNoteId;
    }

    UUID getTargetNoteId() {
        return targetNoteId;
    }

    String getTargetTitle() {
        return targetTitle;
    }

    void resolveTo(UUID noteId) {
        this.targetNoteId = noteId;
    }
}
