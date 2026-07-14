package com.northstar.core.search;

import java.util.List;

/** Bounded, attachment-scoped evidence prepared for one Assistant turn. */
public record AttachmentContext(String promptSection, List<AttachmentSource> sources) {

    public AttachmentContext {
        sources = List.copyOf(sources);
    }

    public static AttachmentContext empty() {
        return new AttachmentContext("", List.of());
    }
}
