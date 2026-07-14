package com.northstar.core.search;

import java.util.List;

/** Bounded, attachment-scoped evidence prepared for one Assistant turn. */
public record AttachmentContext(List<AttachmentExcerpt> excerpts, List<AttachmentSource> sources) {

    public AttachmentContext {
        excerpts = List.copyOf(excerpts);
        sources = List.copyOf(sources);
    }

    public static AttachmentContext empty() {
        return new AttachmentContext(List.of(), List.of());
    }
}
