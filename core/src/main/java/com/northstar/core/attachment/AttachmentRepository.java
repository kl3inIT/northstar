package com.northstar.core.attachment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    Optional<Attachment> findBySha256(String sha256);
}
