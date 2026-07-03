package com.northstar.core.note;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A note. The Markdown body is the portable content; wiki links inside it are
 * derived into {@code note_link} for backlinks and graph views. Organisation is
 * Obsidian-style: a {@code folderPath} bucket (the tree is derived from paths),
 * {@code tags} that cut across folders, and the link graph.
 */
@Entity
@Table(name = "note")
public class Note {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Note() {
        // for JPA
    }

    public Note(UUID id, String title, String slug, String folderPath,
                String contentMarkdown, Set<String> tags, Instant now) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.folderPath = folderPath == null ? "" : folderPath;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
        this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void edit(String title, String folderPath, String contentMarkdown, Set<String> tags, Instant now) {
        this.title = title;
        this.folderPath = folderPath == null ? "" : folderPath;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        this.updatedAt = now;
    }
}
