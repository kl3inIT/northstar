package com.northstar.core.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    Optional<Attachment> findBySha256(String sha256);

    /** Ids only — never drags every bytea into memory (search backfill scan). */
    @Query("SELECT a.id FROM Attachment a")
    List<UUID> findAllIds();
}
