package com.northstar.core.note;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The note module's public API. Owns note lifecycle plus the derived wiki-link
 * graph: on every write it re-parses {@code [[links]]} from the Markdown body into
 * {@code note_link} rows, resolving each to an existing note where possible and
 * leaving it dangling otherwise. Creating a note also back-fills any dangling links
 * that were waiting for its title, so backlinks appear as soon as both notes exist.
 *
 * <p>Organisation is Obsidian-style — a {@code folderPath} (the UI derives the tree
 * from distinct paths) plus {@code tags}. Search is keyword-only here (Postgres
 * full-text over title + body); semantic retrieval is a separate, later concern and
 * is not what the in-note search box uses.
 */
@Service
public class NoteService {

    private static final int SNIPPET_LIST = 160;
    private static final int SNIPPET_REF = 120;

    private final NoteRepository notes;
    private final NoteLinkRepository links;
    private final ApplicationEventPublisher events;

    NoteService(NoteRepository notes, NoteLinkRepository links, ApplicationEventPublisher events) {
        this.notes = notes;
        this.links = links;
        this.events = events;
    }

    /** Hand-written note: born a trusted {@link NoteStatus#RESOURCE}. */
    @Transactional
    public NoteDetail create(String title, String folderPath, String markdown, Collection<String> tags) {
        return create(title, folderPath, markdown, tags, NoteStatus.RESOURCE);
    }

    /** Machine-drafted callers (capture, MCP) pass {@link NoteStatus#STAGING} — the review queue. */
    @Transactional
    public NoteDetail create(String title, String folderPath, String markdown, Collection<String> tags,
                             NoteStatus status) {
        Note note = new Note(UUID.randomUUID(), title.strip(), uniqueSlug(title),
                NoteText.normalizeFolderPath(folderPath), markdown, NoteText.normalizeTags(tags), status);
        notes.save(note);
        syncOutgoingLinks(note);
        resolveInboundLinks(note);
        events.publishEvent(new NoteSaved(note.getId()));
        return detail(note);
    }

    /** Staging verdict or restore: move the note to another working state. */
    @Transactional
    public NoteDetail setStatus(UUID id, NoteStatus status) {
        Note note = notes.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
        note.moveTo(status);
        notes.saveAndFlush(note);
        events.publishEvent(new NoteSaved(note.getId()));
        return detail(note);
    }

    /**
     * {@code expectedVersion} (when given) is the version the client loaded; a
     * mismatch means someone else saved in between — fail with a conflict
     * instead of silently overwriting their edit.
     */
    @Transactional
    public NoteDetail update(UUID id, String title, String folderPath, String markdown,
                             Collection<String> tags, Long expectedVersion) {
        Note note = notes.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
        if (expectedVersion != null && note.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Note " + id + " was modified concurrently (expected version " + expectedVersion
                            + ", is " + note.getVersion() + ")");
        }
        note.edit(title.strip(), NoteText.normalizeFolderPath(folderPath), markdown, NoteText.normalizeTags(tags));
        syncOutgoingLinks(note);
        // Flush now so @LastModifiedDate/@Version are current in the response.
        notes.saveAndFlush(note);
        events.publishEvent(new NoteSaved(note.getId()));
        return detail(note);
    }

    /** Deletes the note; inbound wiki-links become dangling again (title kept). */
    @Transactional
    public void delete(UUID id) {
        Note note = notes.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
        links.deleteBySourceNoteId(id);
        List<NoteLink> inbound = links.findByTargetNoteId(id);
        inbound.forEach(link -> link.resolveTo(null));
        links.saveAll(inbound);
        notes.delete(note);
        events.publishEvent(new NoteDeleted(id));
    }

    @Transactional(readOnly = true)
    public Optional<NoteDetail> getBySlug(String slug) {
        return notes.findBySlug(slug).map(this::detail);
    }

    @Transactional(readOnly = true)
    public Optional<NoteDetail> findById(UUID id) {
        return notes.findById(id).map(this::detail);
    }

    /** Exact-title lookup (case-insensitive) — deterministic-title upserts (alignment journal). */
    @Transactional(readOnly = true)
    public Optional<NoteDetail> findByTitle(String title) {
        return notes.findFirstByTitleIgnoreCase(title.strip()).map(this::detail);
    }

    /** Notes newest-first, bounded: callers page instead of pulling the whole table. */
    @Transactional(readOnly = true)
    public Page<NoteSummary> list(Pageable pageable) {
        return notes.findAll(pageable).map(this::summary);
    }

    /** One working-state tab (Staging / Resources / Archive), same paging contract. */
    @Transactional(readOnly = true)
    public Page<NoteSummary> listByStatus(NoteStatus status, Pageable pageable) {
        return notes.findByStatus(status, pageable).map(this::summary);
    }

    /**
     * Notes carrying ANY of the tags, archived excluded — the MFI bridge to the
     * LDP spine: a note "belongs" to a discipline by carrying one of the
     * discipline name's words as a tag (e.g. "English · IELTS" → english, ielts).
     */
    @Transactional(readOnly = true)
    public Page<NoteSummary> listByAnyTag(Collection<String> tags, Pageable pageable) {
        List<String> cleaned = tags.stream()
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isBlank())
                .toList();
        if (cleaned.isEmpty()) {
            return Page.empty(pageable);
        }
        return notes.findByAnyTag(cleaned, NoteStatus.ARCHIVED, pageable).map(this::summary);
    }

    @Transactional(readOnly = true)
    public List<NoteSummary> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<NoteRepository.SearchHit> hits = notes.search(query.strip());
        Map<UUID, Note> byId = notes.findAllById(hits.stream().map(NoteRepository.SearchHit::getId).toList())
                .stream().collect(Collectors.toMap(Note::getId, Function.identity()));
        // Keep the rank order from the hits; snippet is the highlighted matched fragment.
        return hits.stream()
                .map(hit -> summary(byId.get(hit.getId()), hit.getHeadline()))
                .toList();
    }

    // --- internals ---------------------------------------------------------

    /** Replace this note's outgoing links with the ones currently in its body. */
    private void syncOutgoingLinks(Note note) {
        links.deleteBySourceNoteId(note.getId());
        for (String linkedTitle : NoteText.parseLinks(note.getContentMarkdown())) {
            UUID target = notes.findFirstByTitleIgnoreCase(linkedTitle).map(Note::getId).orElse(null);
            links.save(new NoteLink(UUID.randomUUID(), note.getId(), target, linkedTitle));
        }
    }

    /** Point any previously-dangling links that named this note's title at it. */
    private void resolveInboundLinks(Note note) {
        List<NoteLink> dangling = links.findByTargetTitleIgnoreCaseAndTargetNoteIdIsNull(note.getTitle());
        dangling.forEach(link -> link.resolveTo(note.getId()));
        links.saveAll(dangling);
    }

    private String uniqueSlug(String title) {
        String base = NoteText.slugify(title);
        String slug = base;
        int n = 2;
        while (notes.findBySlug(slug).isPresent()) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    private NoteDetail detail(Note note) {
        List<NoteRef> outgoing = links.findBySourceNoteId(note.getId()).stream()
                .map(this::outgoingRef)
                .toList();
        List<NoteRef> backlinks = links.findByTargetNoteId(note.getId()).stream()
                .map(link -> notes.findById(link.getSourceNoteId()).orElse(null))
                .filter(Objects::nonNull)
                .map(src -> new NoteRef(src.getTitle(), src.getSlug(), NoteText.snippet(src.getContentMarkdown(), SNIPPET_REF), true))
                .toList();
        return new NoteDetail(note.getId(), note.getTitle(), note.getSlug(), note.getFolderPath(),
                note.getContentMarkdown(), List.copyOf(note.getTags()), note.getStatus(),
                note.getCreatedAt(), note.getUpdatedAt(), note.getVersion(), outgoing, backlinks);
    }

    private NoteRef outgoingRef(NoteLink link) {
        if (link.getTargetNoteId() != null) {
            return notes.findById(link.getTargetNoteId())
                    .map(t -> new NoteRef(t.getTitle(), t.getSlug(), NoteText.snippet(t.getContentMarkdown(), SNIPPET_REF), true))
                    .orElse(new NoteRef(link.getTargetTitle(), null, "", false));
        }
        return new NoteRef(link.getTargetTitle(), null, "", false);
    }

    private NoteSummary summary(Note note) {
        return summary(note, NoteText.snippet(note.getContentMarkdown(), SNIPPET_LIST));
    }

    private NoteSummary summary(Note note, String snippet) {
        return new NoteSummary(note.getId(), note.getTitle(), note.getSlug(), note.getFolderPath(),
                snippet, List.copyOf(note.getTags()), note.getStatus(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
