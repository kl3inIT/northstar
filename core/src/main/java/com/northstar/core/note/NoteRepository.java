package com.northstar.core.note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Note}. Kept package-private-friendly: other modules
 * should go through {@link NoteService} rather than this repository.
 */
public interface NoteRepository extends JpaRepository<Note, UUID> {

    Optional<Note> findBySlug(String slug);

    /** Resolve a wiki-link target title to an existing note (first match wins). */
    Optional<Note> findFirstByTitleIgnoreCase(String title);

    /** One working-state tab (Staging / Resources / Archive). */
    Page<Note> findByStatus(NoteStatus status, Pageable pageable);

    /** A ranked full-text hit: note id plus a {@code <mark>}-highlighted body fragment. */
    interface SearchHit {
        UUID getId();
        String getHeadline();
    }

    /**
     * Keyword full-text search over title (weight A) + body (weight B), ranked.
     * {@code websearch_to_tsquery} accepts plain user input ("cohesion essay",
     * quoted phrases, {@code -exclude}) so the search box needs no query DSL.
     * {@code ts_headline} returns the matched fragment with {@code <mark>} markers
     * for the UI to highlight; the client renders it as text (never innerHTML).
     */
    @Query(value = """
            SELECT n.id AS id,
                   ts_headline('english', n.content_markdown, websearch_to_tsquery('english', :query),
                               'StartSel=<mark>, StopSel=</mark>, MaxWords=25, MinWords=10') AS headline
            FROM note n
            WHERE n.search_tsv @@ websearch_to_tsquery('english', :query)
              AND n.status <> 'ARCHIVED'
            ORDER BY ts_rank(n.search_tsv, websearch_to_tsquery('english', :query)) DESC,
                     n.updated_at DESC
            LIMIT 50
            """, nativeQuery = true)
    List<SearchHit> search(@Param("query") String query);
}
