package com.northstar.core.note;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link NoteLink}. Package-private: managed only by {@link NoteService}. */
interface NoteLinkRepository extends JpaRepository<NoteLink, UUID> {

    List<NoteLink> findBySourceNoteId(UUID sourceNoteId);

    /** Backlinks: every link pointing at this note. */
    List<NoteLink> findByTargetNoteId(UUID targetNoteId);

    /** Dangling links whose raw title matches — used to resolve them when a note is created. */
    List<NoteLink> findByTargetTitleIgnoreCaseAndTargetNoteIdIsNull(String targetTitle);

    /**
     * Bulk delete so the DELETE executes before the fresh inserts in the same
     * transaction — a derived {@code deleteBy} would let Hibernate order the new
     * INSERTs first and trip the {@code (source_note_id, target_title)} unique key.
     */
    @Modifying
    @Query("DELETE FROM NoteLink l WHERE l.sourceNoteId = :sourceNoteId")
    void deleteBySourceNoteId(@Param("sourceNoteId") UUID sourceNoteId);
}
