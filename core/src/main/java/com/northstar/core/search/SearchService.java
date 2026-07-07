package com.northstar.core.search;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The search module's public API: hybrid retrieval over the knowledge base
 * (keyword tsvector + semantic pgvector, fused with Reciprocal Rank Fusion)
 * and the note→vector indexing that feeds the semantic half.
 *
 * <p>The vector side is optional by design — the {@link VectorStore} bean only
 * exists in apps that wire an embedding model (api). Everywhere else search
 * degrades to keyword-only and indexing is a no-op the startup backfill later
 * heals: the vector index is derived and disposable, never the source of truth.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final int SEMANTIC_TOP_K = 10;
    /** Standard RRF dampening constant (Cormack et al.): score = Σ 1/(K + rank). */
    private static final int RRF_K = 60;
    private static final int SNIPPET_CHARS = 200;
    private static final int BACKFILL_PAGE = 100;

    private final NoteService notes;
    private final ObjectProvider<VectorStore> vectorStore;
    private final JdbcClient jdbc;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    SearchService(NoteService notes, ObjectProvider<VectorStore> vectorStore, JdbcClient jdbc) {
        this.notes = notes;
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    /** Hybrid search, best {@code limit} notes; keyword-only where no vector store is wired. */
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Hit> fused = new LinkedHashMap<>();

        List<NoteSummary> keyword = notes.search(query.strip());
        for (int rank = 0; rank < keyword.size(); rank++) {
            NoteSummary note = keyword.get(rank);
            fused.merge(note.slug(), new Hit(note.title(), note.slug(), note.snippet(), rrf(rank)), Hit::plus);
        }

        VectorStore store = vectorStore.getIfAvailable();
        if (store != null) {
            List<Document> chunks = store.similaritySearch(
                    SearchRequest.builder().query(query.strip()).topK(SEMANTIC_TOP_K).build());
            int rank = 0;
            for (Document chunk : chunks) {
                String slug = String.valueOf(chunk.getMetadata().get("slug"));
                Hit existing = fused.get(slug);
                if (existing != null && existing.semanticCounted) {
                    continue; // several chunks of one note: only its best rank counts
                }
                Hit hit = new Hit(String.valueOf(chunk.getMetadata().get("title")), slug,
                        snippet(chunk.getText()), rrf(rank));
                hit.semanticCounted = true;
                fused.merge(slug, hit, Hit::plus);
                rank++;
            }
        }

        return fused.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .map(h -> new SearchResult(h.title, h.slug, h.snippet))
                .toList();
    }

    /** (Re-)embeds one note's chunks; called after every save, and by the backfill. */
    public void index(UUID noteId) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.debug("No vector store in this app — note {} left for the backfill", noteId);
            return;
        }
        NoteDetail note = notes.findById(noteId).orElse(null);
        if (note == null) {
            remove(noteId);
            return;
        }
        store.delete(byNoteId(noteId));
        String body = note.contentMarkdown() == null || note.contentMarkdown().isBlank()
                ? note.title()
                : note.contentMarkdown();
        List<Document> chunks = splitter.apply(List.of(new Document(body)));
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            docs.add(Document.builder()
                    // Title on every chunk: later chunks embed without their heading context otherwise.
                    .text(note.title() + "\n\n" + chunks.get(i).getText())
                    .metadata(Map.of(
                            "noteId", noteId.toString(),
                            "slug", note.slug(),
                            "title", note.title(),
                            "chunk", i,
                            "noteUpdatedAt", note.updatedAt().toString()))
                    .build());
        }
        store.add(docs);
        log.debug("Embedded note {} as {} chunk(s)", note.slug(), docs.size());
    }

    /** Drops a deleted note's vectors — vector_store has no FK to note, cleanup is ours. */
    public void remove(UUID noteId) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store != null) {
            store.delete(byNoteId(noteId));
        }
    }

    /**
     * Heals the derived index: embeds notes that are missing or stale (saved via
     * an app without a vector store, or before V14) and drops orphaned vectors.
     */
    public void reindexStale() {
        if (vectorStore.getIfAvailable() == null) {
            return;
        }
        Map<String, String> indexedAt = new HashMap<>();
        jdbc.sql("SELECT metadata->>'noteId' AS note_id, MAX(metadata->>'noteUpdatedAt') AS updated_at "
                        + "FROM vector_store GROUP BY 1")
                .query((rs, _) -> indexedAt.put(rs.getString("note_id"), rs.getString("updated_at")))
                .list();

        int reindexed = 0;
        int pageNumber = 0;
        Page<NoteSummary> page;
        do {
            page = notes.list(PageRequest.of(pageNumber++, BACKFILL_PAGE));
            for (NoteSummary note : page) {
                String key = note.id().toString();
                boolean stale = !note.updatedAt().toString().equals(indexedAt.remove(key));
                if (stale) {
                    try {
                        index(note.id());
                        reindexed++;
                    } catch (Exception e) {
                        log.warn("Backfill failed to embed note {} — will retry next run", note.slug(), e);
                    }
                }
            }
        } while (page.hasNext());

        // Whatever is left in the map has no matching note anymore.
        indexedAt.keySet().forEach(orphan -> remove(UUID.fromString(orphan)));
        if (reindexed > 0 || !indexedAt.isEmpty()) {
            log.info("Search backfill: embedded {} note(s), removed {} orphaned", reindexed, indexedAt.size());
        }
    }

    private static String byNoteId(UUID noteId) {
        return "noteId == '%s'".formatted(noteId);
    }

    private static double rrf(int rank) {
        return 1.0 / (RRF_K + rank + 1);
    }

    private static String snippet(String chunkText) {
        String flat = chunkText == null ? "" : chunkText.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_CHARS ? flat : flat.substring(0, SNIPPET_CHARS) + "…";
    }

    /** Fusion accumulator: one entry per note, RRF scores added across both rankings. */
    private static final class Hit {

        private final String title;
        private final String slug;
        private final String snippet;
        private boolean semanticCounted;
        private double score;

        private Hit(String title, String slug, String snippet, double score) {
            this.title = title;
            this.slug = slug;
            this.snippet = snippet;
            this.score = score;
        }

        /** Keeps the first-seen title/snippet (keyword's highlighted one wins by order). */
        private Hit plus(Hit other) {
            this.score += other.score;
            this.semanticCounted |= other.semanticCounted;
            return this;
        }
    }
}
