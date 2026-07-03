package com.northstar.core.note;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A note as shown in a list or search result: no body, just a snippet + its folder and tags. */
public record NoteSummary(
        UUID id,
        String title,
        String slug,
        String folderPath,
        String snippet,
        List<String> tags,
        Instant updatedAt) {
}
