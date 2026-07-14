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
        VocabEnrichmentArtifactView image,
        String imageAlt,
        VocabEnrichmentArtifactView wordAudio,
        VocabEnrichmentArtifactView exampleAudio,
        String audioTargetId,
        String audioLocale,
        String error) {
}
