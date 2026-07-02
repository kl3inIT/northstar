package com.northstar.core.note;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Note}. Kept package-private-friendly: other modules
 * should go through the module's public services rather than this repository.
 */
public interface NoteRepository extends JpaRepository<Note, UUID> {

    Optional<Note> findBySlug(String slug);
}
