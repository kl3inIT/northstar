package com.northstar.core.study;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Non-persisting AI preview. {@code metadata} is applied only after user approval. */
public record VocabEnrichmentPreview(
        String example,
        @NotNull List<String> collocations,
        @NotNull List<String> synonyms,
        @NotNull List<String> antonyms,
        String contrast,
        String mnemonic,
        VocabWordFormation wordFormation,
        @NotNull String metadata) {
}
