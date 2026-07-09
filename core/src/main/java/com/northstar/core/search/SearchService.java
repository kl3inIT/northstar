package com.northstar.core.search;

import com.northstar.core.attachment.AttachmentContent;
import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentView;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.shared.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * The search module's public API: hybrid retrieval over the knowledge base
 * (keyword tsvector + semantic pgvector, fused with Reciprocal Rank Fusion)
 * and the indexing that feeds the semantic half — notes split by markdown
 * header into breadcrumbed sections, uploaded files extracted through Tika,
 * images described by the vision model and embedded as captions.
 *
 * <p>Indexing is idempotent by content hash: every chunk carries a
 * {@code contentHash} of what produced it, and {@link #index} skips the
 * embedding call when the stored hash already matches — a status flip or a
 * no-op save costs one SQL lookup, not an OpenAI round trip. Bumping
 * {@link #INDEX_VERSION} changes every hash, so a chunking-format change
 * re-embeds the corpus on the next backfill with no manual wipe.
 *
 * <p>The vector side is optional by design — the {@link VectorStore} bean only
 * exists in apps that wire an embedding model (api). Everywhere else search
 * degrades to keyword-only and indexing is a no-op the startup backfill later
 * heals: the vector index is derived and disposable, never the source of truth.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** Part of every contentHash — bump when the chunking/caption format changes. */
    private static final int INDEX_VERSION = 3;

    /** Candidate window per retriever before RRF's final cut; mirrors production RRF rank windows. */
    private static final int RANK_WINDOW = 50;
    /** Fetch more chunks than sources because one note/file can own several high-ranking chunks. */
    private static final int SEMANTIC_CHUNK_WINDOW = 150;
    private static final int SNIPPET_CHARS = 200;
    private static final int BACKFILL_PAGE = 100;
    /** Embedding-cost ceiling per uploaded file (~64 × 800 tokens). */
    private static final int MAX_ATTACHMENT_CHUNKS = 64;

    /** An image the note editor embedded: {@code ![alt](/api/files/<uuid>)}. */
    private static final Pattern NOTE_IMAGE =
            Pattern.compile("!\\[.*?]\\(/api/files/([0-9a-fA-F-]{36})\\)");

    private static final String CAPTION_PROMPT = """
            Describe this image for search indexing: the subject, any visible text \
            (transcribe it verbatim), and for diagrams or screenshots the structure \
            and labels. 2-4 sentences. Use the language of the visible text where \
            there is one, otherwise English. Output only the description.""";

    private final NoteService notes;
    private final AttachmentService attachments;
    private final ObjectProvider<VectorStore> vectorStore;
    private final ObjectProvider<ChatModel> chatModel;
    private final JdbcClient jdbc;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    SearchService(NoteService notes, AttachmentService attachments,
            ObjectProvider<VectorStore> vectorStore, ObjectProvider<ChatModel> chatModel,
            JdbcClient jdbc) {
        this.notes = notes;
        this.attachments = attachments;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.jdbc = jdbc;
    }

    /** Hybrid search, best {@code limit} sources; keyword-only where no vector store is wired. */
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Hit> fused = new LinkedHashMap<>();

        List<NoteSummary> keyword = notes.search(query.strip(), RANK_WINDOW);
        for (int rank = 0; rank < keyword.size(); rank++) {
            NoteSummary note = keyword.get(rank);
            Hit hit = new Hit(SearchResult.SOURCE_NOTE, note.title(), note.slug(),
                    "/notes/" + note.slug(), note.snippet(), rank, SearchRanking.rrf(rank));
            fused.merge(hit.url, hit, Hit::plus);
        }

        VectorStore store = vectorStore.getIfAvailable();
        if (store != null) {
            List<Document> chunks = store.similaritySearch(
                    SearchRequest.builder().query(query.strip()).topK(SEMANTIC_CHUNK_WINDOW).build());
            int rank = 0;
            for (Document chunk : chunks) {
                Hit hit = semanticHit(chunk, rank, SearchRanking.rrf(rank));
                Hit existing = fused.get(hit.url);
                if (existing != null && existing.semanticCounted) {
                    continue; // several chunks of one source: only its best rank counts
                }
                hit.semanticCounted = true;
                fused.merge(hit.url, hit, Hit::plus);
                rank++;
                if (rank >= RANK_WINDOW) {
                    break;
                }
            }
        }

        return fused.values().stream()
                .sorted(SearchService::compareHits)
                .limit(limit)
                .map(h -> new SearchResult(h.source, h.title, h.slug, h.url, h.snippet))
                .toList();
    }

    private static Hit semanticHit(Document chunk, int rank, double score) {
        Map<String, Object> metadata = chunk.getMetadata();
        String title = String.valueOf(metadata.get("title"));
        if (metadata.get("noteId") != null) {
            String slug = String.valueOf(metadata.get("slug"));
            return new Hit(SearchResult.SOURCE_NOTE, title, slug, "/notes/" + slug,
                    snippet(chunk.getText()), rank, score);
        }
        return new Hit(SearchResult.SOURCE_FILE, title, null,
                "/api/files/" + metadata.get("attachmentId"), snippet(chunk.getText()), rank, score);
    }

    /**
     * (Re-)embeds one note — header-aware sections plus a caption chunk per
     * embedded image; skipped entirely when the stored content hash already
     * matches. Returns whether anything was embedded.
     */
    public boolean index(UUID noteId) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.debug("No vector store in this app — note {} left for the backfill", noteId);
            return false;
        }
        NoteDetail note = notes.findById(noteId).orElse(null);
        if (note == null) {
            remove(noteId);
            return false;
        }
        if (note.status() == NoteStatus.ARCHIVED) {
            remove(noteId);
            return false;
        }
        String body = note.contentMarkdown() == null || note.contentMarkdown().isBlank()
                ? note.title()
                : note.contentMarkdown();
        String hash = contentHash(note.status() + "\n" + note.title() + "\n" + body);
        if (hash.equals(indexedHash("noteId", noteId))) {
            log.debug("Note {} unchanged (hash match) — embedding skipped", note.slug());
            return false;
        }

        List<Document> docs = new ArrayList<>();
        int chunkIndex = 0;
        for (MarkdownSections.Section section : MarkdownSections.split(note.title(), body)) {
            for (Document piece : splitter.apply(List.of(new Document(section.text())))) {
                docs.add(Document.builder()
                        // Breadcrumb on every chunk: a section embeds with the
                        // headings above it, so queries land on the right one.
                        .text(section.breadcrumb() + "\n\n" + piece.getText())
                        .metadata(noteMetadata(note, hash, chunkIndex++))
                        .build());
            }
        }
        for (UUID imageId : imageIdsIn(body)) {
            String caption = captionFor(imageId);
            if (caption != null) {
                Map<String, Object> metadata = new HashMap<>(noteMetadata(note, hash, chunkIndex++));
                metadata.put("imageAttachmentId", imageId.toString());
                metadata.put("kind", "image");
                docs.add(Document.builder()
                        .text(note.title() + "\n\n" + caption)
                        .metadata(metadata)
                        .build());
            }
        }

        store.delete(filter("noteId", noteId));
        store.add(docs);
        log.debug("Embedded note {} as {} chunk(s)", note.slug(), docs.size());
        return true;
    }

    /**
     * Embeds one uploaded file so search finds it: Tika-extracted text for
     * documents, a vision caption for images. The bytes are immutable, but the
     * content hash folds in {@link #INDEX_VERSION}: an already-indexed file is
     * skipped only while its stored hash still matches, so bumping the version
     * re-embeds every file on the next backfill. Returns whether anything was
     * embedded.
     */
    public boolean indexAttachment(UUID attachmentId) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.debug("No vector store in this app — file {} left for the backfill", attachmentId);
            return false;
        }
        // Metadata-only lookup: compute the hash from sha256 without reading bytea,
        // so an unchanged file costs one SQL round trip, not a Tika/vision run.
        AttachmentView fileMeta = attachments.find(attachmentId).orElse(null);
        if (fileMeta == null) {
            return false;
        }
        String hash = contentHash(fileMeta.sha256());
        if (hash.equals(indexedHash("attachmentId", attachmentId))) {
            log.debug("File {} unchanged (hash match) — embedding skipped", attachmentId);
            return false;
        }
        AttachmentContent content = loadQuietly(attachmentId);
        if (content == null) {
            return false;
        }
        String filename = content.meta().filename();
        String mime = content.meta().mimeType().toLowerCase(Locale.ROOT);

        List<Document> docs;
        if (mime.startsWith("image/")) {
            String caption = caption(content);
            if (caption == null) {
                return false;
            }
            docs = List.of(Document.builder()
                    .text(filename + "\n\n" + caption)
                    .metadata(fileMetadata(attachmentId, filename, hash, 0, "image"))
                    .build());
        } else {
            String text = extractText(content, mime);
            if (text == null || text.isBlank()) {
                log.debug("File {} ({}) has no extractable text — not embedded", filename, mime);
                return false;
            }
            docs = new ArrayList<>();
            int chunkIndex = 0;
            for (MarkdownSections.Section section : MarkdownSections.split(filename, text)) {
                for (Document piece : splitter.apply(List.of(new Document(section.text())))) {
                    docs.add(Document.builder()
                            .text(section.breadcrumb() + "\n\n" + piece.getText())
                            .metadata(fileMetadata(attachmentId, filename, hash, chunkIndex++, "file"))
                            .build());
                }
            }
            if (docs.size() > MAX_ATTACHMENT_CHUNKS) {
                log.warn("File {} produced {} chunks — embedding the first {} only",
                        filename, docs.size(), MAX_ATTACHMENT_CHUNKS);
                docs = docs.subList(0, MAX_ATTACHMENT_CHUNKS);
            }
        }
        // Clear any prior-format vectors before re-adding, so an INDEX_VERSION bump
        // (or a re-caption) heals the file in place instead of duplicating chunks.
        store.delete(filter("attachmentId", attachmentId));
        store.add(docs);
        log.debug("Embedded file {} as {} chunk(s)", filename, docs.size());
        return true;
    }

    /** Drops a deleted note's vectors — vector_store has no FK to note, cleanup is ours. */
    public void remove(UUID noteId) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store != null) {
            store.delete(filter("noteId", noteId));
        }
    }

    /**
     * Heals the derived index: (re-)embeds notes whose content hash changed
     * (saved via an app without a vector store, or after an
     * {@link #INDEX_VERSION} bump), embeds files that never got in, and drops
     * orphaned vectors. Content-hash checks make every pass cheap.
     */
    public void reindexStale() {
        if (vectorStore.getIfAvailable() == null) {
            return;
        }
        int embeddedNotes = 0;
        Set<String> indexedNotes = indexedIds("noteId");
        int pageNumber = 0;
        Page<NoteSummary> page;
        do {
            // Stable sort by id: without an ORDER BY, OFFSET paging can skip a live
            // note between pages, whose id would then be treated as orphaned and its
            // vectors deleted. A fixed id order never shifts existing rows.
            page = notes.list(PageRequest.of(pageNumber++, BACKFILL_PAGE, Sort.by("id")));
            for (NoteSummary note : page) {
                indexedNotes.remove(note.id().toString());
                try {
                    if (index(note.id())) {
                        embeddedNotes++;
                    }
                } catch (Exception e) {
                    log.warn("Backfill failed to embed note {} — will retry next run", note.slug(), e);
                }
            }
        } while (page.hasNext());
        indexedNotes.forEach(orphan -> remove(UUID.fromString(orphan)));

        int embeddedFiles = 0;
        Set<String> indexedFiles = indexedIds("attachmentId");
        for (UUID attachmentId : attachments.listIds()) {
            // Visit EVERY file (not just ones absent from the index): indexAttachment
            // hash-checks internally, so this is cheap for unchanged files and lets
            // an INDEX_VERSION bump re-embed already-indexed ones.
            indexedFiles.remove(attachmentId.toString());
            try {
                if (indexAttachment(attachmentId)) {
                    embeddedFiles++;
                }
            } catch (Exception e) {
                log.warn("Backfill failed to embed file {} — will retry next run", attachmentId, e);
            }
        }
        VectorStore store = vectorStore.getIfAvailable();
        indexedFiles.forEach(orphan -> store.delete(filter("attachmentId", UUID.fromString(orphan))));

        if (embeddedNotes > 0 || embeddedFiles > 0 || !indexedNotes.isEmpty() || !indexedFiles.isEmpty()) {
            log.info("Search backfill: embedded {} note(s) and {} file(s), removed {} orphaned",
                    embeddedNotes, embeddedFiles, indexedNotes.size() + indexedFiles.size());
        }
    }

    // --- extraction & captioning ---------------------------------------------

    /** Text out of an uploaded document; null = a format we do not index. */
    private @Nullable String extractText(AttachmentContent content, String mime) {
        String filename = content.meta().filename().toLowerCase(Locale.ROOT);
        if (mime.startsWith("text/") || filename.endsWith(".md")) {
            return new String(content.data(), StandardCharsets.UTF_8);
        }
        boolean tikaExtractable = mime.equals("application/pdf")
                || mime.equals("application/msword")
                || mime.equals("application/rtf")
                || mime.contains("officedocument");
        if (!tikaExtractable) {
            return null;
        }
        ByteArrayResource resource = new ByteArrayResource(content.data()) {
            @Override
            public String getFilename() {
                return content.meta().filename(); // helps Tika's type detection
            }
        };
        return new TikaDocumentReader(resource).get().stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse(null);
    }

    /**
     * Caption for an image referenced inside a note: reuse the attachment's
     * already-embedded caption (one vision call per image, ever — attachments
     * are sha256-deduplicated), else describe it now.
     */
    private @Nullable String captionFor(UUID attachmentId) {
        String existing = jdbc.sql("""
                        SELECT content FROM vector_store
                        WHERE metadata->>'attachmentId' = :id AND metadata->>'kind' = 'image'
                        LIMIT 1
                        """)
                .param("id", attachmentId.toString())
                .query(String.class)
                .optional()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        AttachmentContent content = loadQuietly(attachmentId);
        if (content == null || !content.meta().mimeType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return null;
        }
        return caption(content);
    }

    /** The vision model's search-oriented description; null when unavailable/failed. */
    private @Nullable String caption(AttachmentContent image) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return null;
        }
        try {
            String caption = ChatClient.create(model).prompt()
                    .user(u -> u.text(CAPTION_PROMPT)
                            .media(Media.builder()
                                    .mimeType(MimeTypeUtils.parseMimeType(image.meta().mimeType()))
                                    .data(image.data())
                                    .build()))
                    .call()
                    .content();
            return caption == null || caption.isBlank() ? null : caption.strip();
        } catch (Exception e) {
            log.warn("Vision caption failed for {} — image not embedded", image.meta().filename(), e);
            return null;
        }
    }

    private @Nullable AttachmentContent loadQuietly(UUID attachmentId) {
        try {
            return attachments.load(attachmentId).orElse(null);
        } catch (IllegalStateException e) {
            log.debug("Attachment {} not readable ({}) — not embedded", attachmentId, e.getMessage());
            return null;
        }
    }

    private static Set<UUID> imageIdsIn(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        Matcher matcher = NOTE_IMAGE.matcher(body);
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
    }

    // --- index bookkeeping ----------------------------------------------------

    private static Map<String, Object> noteMetadata(NoteDetail note, String hash, int chunk) {
        return Map.of(
                "noteId", note.id().toString(),
                "slug", note.slug(),
                "title", note.title(),
                "status", note.status().name(),
                "chunk", chunk,
                "contentHash", hash);
    }

    private static Map<String, Object> fileMetadata(UUID attachmentId, String filename,
            String hash, int chunk, String kind) {
        return Map.of(
                "attachmentId", attachmentId.toString(),
                "title", filename,
                "chunk", chunk,
                "contentHash", hash,
                "kind", kind);
    }

    /** The hash the index currently holds for one source; null = not indexed. */
    private @Nullable String indexedHash(String idKey, UUID id) {
        return jdbc.sql("SELECT metadata->>'contentHash' FROM vector_store "
                        + "WHERE metadata->>'" + idKey + "' = :id LIMIT 1")
                .param("id", id.toString())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private Set<String> indexedIds(String idKey) {
        Set<String> ids = new HashSet<>();
        jdbc.sql("SELECT DISTINCT metadata->>'" + idKey + "' AS id FROM vector_store "
                        + "WHERE metadata->>'" + idKey + "' IS NOT NULL")
                .query((rs, _) -> ids.add(rs.getString("id")))
                .list();
        return ids;
    }

    private static String filter(String idKey, UUID id) {
        return "%s == '%s'".formatted(idKey, id);
    }

    private static String contentHash(String content) {
        // Fold in INDEX_VERSION so a chunking/caption-format change invalidates
        // every stored hash and forces a re-embed on the next backfill.
        return Hashing.sha256Hex(INDEX_VERSION + "\n" + content);
    }

    private static int compareHits(Hit left, Hit right) {
        int score = Double.compare(right.score, left.score);
        if (score != 0) {
            return score;
        }
        int sources = Integer.compare(right.sourceCount, left.sourceCount);
        if (sources != 0) {
            return sources;
        }
        int rank = Integer.compare(left.bestRank, right.bestRank);
        if (rank != 0) {
            return rank;
        }
        return left.url.compareTo(right.url);
    }

    private static String snippet(String chunkText) {
        String flat = chunkText == null ? "" : chunkText.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_CHARS ? flat : flat.substring(0, SNIPPET_CHARS) + "…";
    }

    /** Fusion accumulator: one entry per source, RRF scores added across both rankings. */
    private static final class Hit {

        private final String source;
        private final String title;
        private final @Nullable String slug;
        private final String url;
        private final String snippet;
        private boolean semanticCounted;
        private int sourceCount = 1;
        private int bestRank;
        private double score;

        private Hit(String source, String title, @Nullable String slug, String url,
                String snippet, int rank, double score) {
            this.source = source;
            this.title = title;
            this.slug = slug;
            this.url = url;
            this.snippet = snippet;
            this.bestRank = rank;
            this.score = score;
        }

        /** Keeps the first-seen title/snippet (keyword's highlighted one wins by order). */
        private Hit plus(Hit other) {
            this.score += other.score;
            this.semanticCounted |= other.semanticCounted;
            this.sourceCount++;
            this.bestRank = Math.min(this.bestRank, other.bestRank);
            return this;
        }
    }
}
