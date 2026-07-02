package com.northstar.core.note;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/**
 * A note. The Markdown body is the portable content; wiki links inside it are
 * derived into {@code note_links} for backlinks and graph views.
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

    @Column(name = "content_markdown", nullable = false, columnDefinition = "text")
    private String contentMarkdown = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Note() {
        // for JPA
    }

    public Note(UUID id, String title, String slug, String contentMarkdown, Instant now) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
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

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void edit(String title, String contentMarkdown, Instant now) {
        this.title = title;
        this.contentMarkdown = contentMarkdown == null ? "" : contentMarkdown;
        this.updatedAt = now;
    }
}
