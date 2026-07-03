package com.northstar.core.note;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A full note for the reading view: body plus its folder, tags, outgoing links and backlinks. */
public record NoteDetail(
        UUID id,
        String title,
        String slug,
        String folderPath,
        String contentMarkdown,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        List<NoteRef> outgoingLinks,
        List<NoteRef> backlinks) {
}
