package com.northstar.api.study;

import com.northstar.core.study.VocabEnrichmentPreview;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record VocabEnrichmentJobView(
        @NotNull UUID id,
        @NotNull UUID cardId,
        @NotNull String cardFront,
        @NotNull VocabEnrichmentJobStatus status,
        VocabEnrichmentPreview preview,
        String imageBase64,
        String imageMediaType,
        String imageAlt,
        String error) {
}

