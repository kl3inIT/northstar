package com.northstar.core.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    Optional<Attachment> findBySha256(String sha256);

    /** Metadata only — a constructor projection that never selects the bytea column. */
    @Query("SELECT new com.northstar.core.attachment.AttachmentView("
            + "a.id, a.filename, a.mimeType, a.sizeBytes, a.sha256, a.createdAt) "
            + "FROM Attachment a WHERE a.id = :id")
    Optional<AttachmentView> findMetaById(UUID id);

    /** Ids only — never drags every bytea into memory (search backfill scan). */
    @Query("SELECT a.id FROM Attachment a")
    List<UUID> findAllIds();
}
