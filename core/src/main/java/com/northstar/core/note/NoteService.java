package com.northstar.core.note;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
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

    NoteService(NoteRepository notes, NoteLinkRepository links) {
        this.notes = notes;
        this.links = links;
    }

    @Transactional
    public NoteDetail create(String title, String folderPath, String markdown, Collection<String> tags) {
        Instant now = Instant.now();
        Note note = new Note(UUID.randomUUID(), title.strip(), uniqueSlug(title),
                NoteText.normalizeFolderPath(folderPath), markdown, NoteText.normalizeTags(tags), now);
        notes.save(note);
        syncOutgoingLinks(note);
        resolveInboundLinks(note);
        return detail(note);
    }

    @Transactional
    public NoteDetail update(UUID id, String title, String folderPath, String markdown, Collection<String> tags) {
        Note note = notes.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
        note.edit(title.strip(), NoteText.normalizeFolderPath(folderPath), markdown, NoteText.normalizeTags(tags), Instant.now());
        syncOutgoingLinks(note);
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
    }

    @Transactional(readOnly = true)
    public Optional<NoteDetail> getBySlug(String slug) {
        return notes.findBySlug(slug).map(this::detail);
    }

    @Transactional(readOnly = true)
    public List<NoteSummary> list() {
        return notes.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream().map(this::summary).toList();
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
                note.getContentMarkdown(), List.copyOf(note.getTags()), note.getCreatedAt(), note.getUpdatedAt(),
                outgoing, backlinks);
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
                snippet, List.copyOf(note.getTags()), note.getUpdatedAt());
    }
}
