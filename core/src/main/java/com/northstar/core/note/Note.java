package com.northstar.core.note;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A note. The Markdown body is the portable content; wiki links inside it are
 * derived into {@code note_link} for backlinks and graph views. Organisation is
 * Obsidian-style: a {@code folderPath} bucket (the tree is derived from paths),
 * {@code tags} that cut across folders, and the link graph. Identity, audit
 * timestamps and optimistic locking come from {@link BaseEntity}.
 */
@Entity
@Table(name = "note")
public class Note extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String slug;

    @Column(name = "folder_path", nullable = false)
    private String folderPath = "";

    @Column(name = "content_markdown", nullable = false, columnDefinition = "text")
    private String contentMarkdown = "";

    @ElementCollection
    @CollectionTable(name = "note_tag", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    /** MFI working state — see {@link NoteStatus}. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoteStatus status = NoteStatus.RESOURCE;

    protected Note() {
        // for JPA
    }

    public Note(UUID id, String title, String slug, String folderPath,
                String contentMarkdown, Set<String> tags, NoteStatus status) {
        super(id);
        this.title = title;
        this.slug = slug;
        this.folderPath = folderPath == null ? "" : folderPath;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
        this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
        this.status = status == null ? NoteStatus.RESOURCE : status;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public Set<String> getTags() {
        return tags;
    }

    public NoteStatus getStatus() {
        return status;
    }

    /** Staging review verdict, or a restore: move the note to another working state. */
    public void moveTo(NoteStatus status) {
        this.status = status;
    }

    public void edit(String title, String folderPath, String contentMarkdown, Set<String> tags) {
        this.title = title;
        this.folderPath = folderPath == null ? "" : folderPath;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }
}
